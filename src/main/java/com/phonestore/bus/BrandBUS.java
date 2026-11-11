package com.phonestore.bus;

import com.phonestore.dao.BrandDAO;
import com.phonestore.dao.ProductDAO;
import com.phonestore.dto.BrandDTO;
import com.phonestore.exception.BusinessException;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class BrandBUS {

    // Chưa có quyền riêng cho thương hiệu, tạm dùng quyền sản phẩm
    private static final String PERM_ADD = "add_product";
    private static final String PERM_EDIT = "edit_product";
    private static final String PERM_DELETE = "delete_product";

    private final BrandDAO brandDAO;
    private final ProductDAO productDAO;
    private final AuthBUS authBUS;

    public BrandBUS() {
        this(new BrandDAO(), new ProductDAO(), new AuthBUS());
    }

    public BrandBUS(BrandDAO brandDAO, ProductDAO productDAO, AuthBUS authBUS) {
        this.brandDAO = brandDAO;
        this.productDAO = productDAO;
        this.authBUS = authBUS;
    }

    public List<BrandDTO> getAllBrands() {
        return brandDAO.findAll();
    }

    public void addBrand(BrandDTO brand) {
        authBUS.requirePermission(PERM_ADD);
        validate(brand);
        checkDuplicateName(brand);
        if (!brandDAO.insert(brand)) {
            throw new BusinessException("Không thể thêm thương hiệu. Vui lòng thử lại.");
        }
    }

    public void updateBrand(BrandDTO brand) {
        authBUS.requirePermission(PERM_EDIT);
        if (brand.getBrandId() <= 0 || brandDAO.findById(brand.getBrandId()) == null) {
            throw new BusinessException("Không tìm thấy thương hiệu cần sửa");
        }
        validate(brand);
        checkDuplicateName(brand);
        if (!brandDAO.update(brand)) {
            throw new BusinessException("Không thể cập nhật thương hiệu. Vui lòng thử lại.");
        }
    }

    public void deleteBrand(int brandId) {
        authBUS.requirePermission(PERM_DELETE);
        if (brandId <= 0) {
            throw new BusinessException("Mã thương hiệu không hợp lệ");
        }
        long count = countProducts(brandId);
        if (count > 0) {
            throw new BusinessException("Không thể xóa: còn " + count
                    + " sản phẩm thuộc thương hiệu này. Hãy xóa hoặc chuyển các sản phẩm đó trước.");
        }
        if (!brandDAO.delete(brandId)) {
            throw new BusinessException("Không thể xóa thương hiệu. Vui lòng thử lại.");
        }
    }

    // ---------- hàm nội bộ ----------

    private long countProducts(int brandId) {
        // Tạm lọc từ danh sách sản phẩm. Sau này nên nhờ Phước thêm ProductDAO.countByBrandId
        try {
            return productDAO.findAll().stream()
                    .filter(p -> Objects.equals(p.getBrandId(), brandId))
                    .count();
        } catch (SQLException e) {
            throw new BusinessException("Không thể kiểm tra sản phẩm thuộc thương hiệu", e);
        }
    }

    private void validate(BrandDTO brand) {
        brand.setBrandName(clean(brand.getBrandName()));
        brand.setCountry(clean(brand.getCountry()));
        brand.setLogoUrl(clean(brand.getLogoUrl()));
        brand.setDescription(clean(brand.getDescription()));

        if (brand.getBrandName() == null) {
            throw new BusinessException("Vui lòng nhập tên thương hiệu");
        }
        if (brand.getBrandName().length() > 100) {
            throw new BusinessException("Tên thương hiệu tối đa 100 ký tự");
        }
        if (brand.getCountry() == null) {
            throw new BusinessException("Vui lòng nhập quốc gia");
        }
        if (brand.getCountry().length() > 100) {
            throw new BusinessException("Tên quốc gia tối đa 100 ký tự");
        }
        if (brand.getLogoUrl() != null && brand.getLogoUrl().length() > 255) {
            throw new BusinessException("Đường dẫn logo tối đa 255 ký tự");
        }
    }

    // Database không có ràng buộc UNIQUE cho tên, nên BUS phải tự kiểm tra
    private void checkDuplicateName(BrandDTO brand) {
        for (BrandDTO b : brandDAO.searchByName(brand.getBrandName())) {
            boolean sameName = b.getBrandName().equalsIgnoreCase(brand.getBrandName());
            boolean otherRecord = b.getBrandId() != brand.getBrandId();
            if (sameName && otherRecord) {
                throw new BusinessException("Thương hiệu \"" + brand.getBrandName() + "\" đã tồn tại");
            }
        }
    }

    // Cắt khoảng trắng, chuỗi rỗng thì thành null
    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
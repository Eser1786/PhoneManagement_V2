package com.phonestore.bus;

import com.phonestore.dao.LookupDAO;
import com.phonestore.dao.ProductDAO;
import com.phonestore.dao.ProductSpecDAO;
import com.phonestore.dto.LookupItem;
import com.phonestore.dto.ProductDTO;
import com.phonestore.dto.ProductFilter;
import com.phonestore.dto.ProductListItemDTO;
import com.phonestore.dto.ProductSpecDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.PermissionNames;
import com.phonestore.util.ProductStatuses;
import com.phonestore.util.SqlErrorMapper;
import com.phonestore.util.Validator;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Nghiệp vụ sản phẩm: xem / tìm kiếm / thêm / sửa / xóa sản phẩm và các phiên bản (ProductSpecs).
 *
 * - Quyền: view_products, add_product, edit_product, delete_product.
 * - Giá luôn là số dương; giá vốn không âm. Tồn kho KHÔNG sửa qua updateSpec (chỉ đổi khi bán / nhập kho);
 *   tồn kho ban đầu khi thêm phiên bản được ghi vào Inventory.
 * - Xóa: chưa từng bán / nhập xuất thì xóa hẳn, đã có lịch sử thì chuyển "Ngừng kinh doanh" (xóa mềm).
 */
public class ProductBUS {

    private final ProductDAO productDAO;
    private final ProductSpecDAO specDAO;
    private final LookupDAO lookupDAO;
    private final AuthBUS authBUS;

    public ProductBUS() {
        this(new ProductDAO(), new ProductSpecDAO(), new LookupDAO(), new AuthBUS());
    }

    /** Dùng cho test: truyền DAO giả. */
    public ProductBUS(ProductDAO productDAO, ProductSpecDAO specDAO, LookupDAO lookupDAO, AuthBUS authBUS) {
        this.productDAO = productDAO;
        this.specDAO = specDAO;
        this.lookupDAO = lookupDAO;
        this.authBUS = authBUS;
    }

    // ================================================================ đọc

    /** Danh sách (mỗi dòng một phiên bản) theo bộ lọc; filter null = không lọc. */
    public List<ProductListItemDTO> searchProducts(ProductFilter filter) {
        authBUS.requirePermission(PermissionNames.VIEW_PRODUCTS);
        if (filter != null) {
            if (filter.getMinPrice() != null && filter.getMinPrice().signum() < 0) {
                throw new BusinessException("Giá tối thiểu không được âm");
            }
            if (filter.getMinPrice() != null && filter.getMaxPrice() != null
                    && filter.getMinPrice().compareTo(filter.getMaxPrice()) > 0) {
                throw new BusinessException("Giá tối thiểu không được lớn hơn giá tối đa");
            }
            if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
                Validator.requireOneOf(filter.getStatus(), "Trạng thái", ProductStatuses.ALL);
            }
        }
        try {
            return productDAO.search(filter);
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể tải danh sách sản phẩm do lỗi cơ sở dữ liệu");
        }
    }

    public ProductDTO getProduct(int productId) {
        authBUS.requirePermission(PermissionNames.VIEW_PRODUCTS);
        try {
            ProductDTO p = productDAO.findById(productId);
            if (p == null) {
                throw new BusinessException("Không tìm thấy sản phẩm");
            }
            return p;
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể tải sản phẩm do lỗi cơ sở dữ liệu");
        }
    }

    public List<ProductSpecDTO> getSpecs(int productId) {
        authBUS.requirePermission(PermissionNames.VIEW_PRODUCTS);
        try {
            return specDAO.findByProductId(productId);
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể tải cấu hình sản phẩm do lỗi cơ sở dữ liệu");
        }
    }

    public ProductSpecDTO getSpec(int specId) {
        authBUS.requirePermission(PermissionNames.VIEW_PRODUCTS);
        try {
            ProductSpecDTO s = specDAO.findById(specId);
            if (s == null) {
                throw new BusinessException("Không tìm thấy phiên bản sản phẩm");
            }
            return s;
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể tải phiên bản sản phẩm do lỗi cơ sở dữ liệu");
        }
    }

    public List<LookupItem> getBrandOptions() {
        authBUS.requirePermission(PermissionNames.VIEW_PRODUCTS);
        try {
            return lookupDAO.findBrands();
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể tải danh sách hãng do lỗi cơ sở dữ liệu");
        }
    }

    public List<LookupItem> getCategoryOptions() {
        authBUS.requirePermission(PermissionNames.VIEW_PRODUCTS);
        try {
            return lookupDAO.findCategories();
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể tải danh sách loại do lỗi cơ sở dữ liệu");
        }
    }

    // ================================================================ thêm

    /** Thêm sản phẩm cùng ít nhất một phiên bản (một transaction). Trả về product_id mới. */
    public int createProduct(ProductDTO product, List<ProductSpecDTO> specs) {
        authBUS.requirePermission(PermissionNames.ADD_PRODUCT);
        validateProduct(product);
        if (specs == null || specs.isEmpty()) {
            throw new BusinessException("Sản phẩm cần có ít nhất một phiên bản (cấu hình, giá)");
        }
        List<ProductSpecDTO> copy = new ArrayList<>();
        for (ProductSpecDTO s : specs) {
            validateSpec(s, true);
            copy.add(s);
        }
        try {
            return productDAO.insertWithSpecs(product, copy, specDAO);
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể thêm sản phẩm do lỗi cơ sở dữ liệu");
        }
    }

    /** Thêm một phiên bản cho sản phẩm có sẵn. Trả về spec_id mới. */
    public int addSpec(ProductSpecDTO spec) {
        authBUS.requirePermission(PermissionNames.ADD_PRODUCT);
        validateSpec(spec, true);
        try {
            if (productDAO.findById(spec.getProductId()) == null) {
                throw new BusinessException("Không tìm thấy sản phẩm");
            }
            return specDAO.insertWithInitialStock(spec);
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể thêm phiên bản do lỗi cơ sở dữ liệu");
        }
    }

    // ================================================================ sửa

    public void updateProduct(ProductDTO product) {
        authBUS.requirePermission(PermissionNames.EDIT_PRODUCT);
        Validator.requireSelected(product.getProductId(), "sản phẩm");
        validateProduct(product);
        try {
            if (!productDAO.update(product)) {
                throw new BusinessException("Không tìm thấy sản phẩm");
            }
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể cập nhật sản phẩm do lỗi cơ sở dữ liệu");
        }
    }

    /** Sửa thông tin / giá / trạng thái của phiên bản. Tồn kho giữ nguyên. */
    public void updateSpec(ProductSpecDTO spec) {
        authBUS.requirePermission(PermissionNames.EDIT_PRODUCT);
        Validator.requireSelected(spec.getSpecId(), "phiên bản");
        validateSpec(spec, false);
        try {
            if (!specDAO.updateKeepStock(spec)) {
                throw new BusinessException("Không tìm thấy phiên bản sản phẩm");
            }
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể cập nhật phiên bản do lỗi cơ sở dữ liệu");
        }
    }

    public void changeSpecStatus(int specId, String status) {
        authBUS.requirePermission(PermissionNames.EDIT_PRODUCT);
        Validator.requireOneOf(status, "Trạng thái", ProductStatuses.ALL);
        try {
            if (!specDAO.updateStatus(specId, status)) {
                throw new BusinessException("Không tìm thấy phiên bản sản phẩm");
            }
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể đổi trạng thái do lỗi cơ sở dữ liệu");
        }
    }

    // ================================================================ xóa

    /**
     * Xóa sản phẩm. Trả về true nếu xóa hẳn, false nếu chỉ chuyển "Ngừng kinh doanh" vì đã có lịch sử bán / kho.
     */
    public boolean deleteProduct(int productId) {
        authBUS.requirePermission(PermissionNames.DELETE_PRODUCT);
        try {
            if (productDAO.findById(productId) == null) {
                throw new BusinessException("Không tìm thấy sản phẩm");
            }
            if (productDAO.hasHistory(productId)) {
                productDAO.discontinue(productId);
                return false;
            }
            productDAO.deleteWithInitialInventory(productId);
            return true;
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể xóa sản phẩm do lỗi cơ sở dữ liệu");
        }
    }

    /** Xóa phiên bản; cùng quy tắc xóa hẳn / xóa mềm như deleteProduct. */
    public boolean deleteSpec(int specId) {
        authBUS.requirePermission(PermissionNames.DELETE_PRODUCT);
        try {
            if (specDAO.findById(specId) == null) {
                throw new BusinessException("Không tìm thấy phiên bản sản phẩm");
            }
            if (specDAO.hasHistory(specId)) {
                specDAO.updateStatus(specId, ProductStatuses.DISCONTINUED);
                return false;
            }
            specDAO.deleteWithInitialInventory(specId);
            return true;
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể xóa phiên bản do lỗi cơ sở dữ liệu");
        }
    }

    // ================================================================ kiểm tra dữ liệu

    private void validateProduct(ProductDTO p) {
        if (p == null) {
            throw new BusinessException("Thiếu thông tin sản phẩm");
        }
        p.setName(Validator.requireText(p.getName(), "Tên sản phẩm", 100));
        p.setDescription(Validator.optionalText(p.getDescription(), "Mô tả", 5000));
        Validator.requireSelected(p.getBrandId(), "hãng");
        Validator.requireSelected(p.getCategoryId(), "loại sản phẩm");
    }

    /** newSpec = true: kiểm tra cả tồn kho ban đầu và product_id. */
    private void validateSpec(ProductSpecDTO s, boolean newSpec) {
        if (s == null) {
            throw new BusinessException("Thiếu thông tin phiên bản");
        }
        Validator.requireMoney(s.getPrice(), "Giá bán", false);
        Validator.requireMoney(s.getCostPrice(), "Giá vốn", true);
        if (newSpec) {
            Validator.requireNonNegative(s.getStockQuantity(), "Tồn kho ban đầu");
        }
        s.setScreenSize(Validator.requireText(s.getScreenSize(), "Màn hình", 50));
        s.setProcessor(Validator.requireText(s.getProcessor(), "Chip", 50));
        s.setResolution(Validator.requireText(s.getResolution(), "Độ phân giải", 50));
        s.setRam(Validator.requireText(s.getRam(), "RAM", 50));
        s.setStorage(Validator.requireText(s.getStorage(), "Bộ nhớ", 50));
        s.setBattery(Validator.requireText(s.getBattery(), "Pin", 50));
        s.setCameraMain(Validator.requireText(s.getCameraMain(), "Camera sau", 50));
        s.setCameraFront(Validator.requireText(s.getCameraFront(), "Camera trước", 50));
        s.setWeight(Validator.requireText(s.getWeight(), "Trọng lượng", 50));
        s.setOs(Validator.requireText(s.getOs(), "Hệ điều hành", 50));
        s.setColor(Validator.requireText(s.getColor(), "Màu sắc", 50));
        s.setWarrantyPeriod(Validator.requireText(s.getWarrantyPeriod(), "Bảo hành", 50));
        if (s.getStatus() == null || s.getStatus().isBlank()) {
            s.setStatus(ProductStatuses.ON_SALE);
        }
        Validator.requireOneOf(s.getStatus(), "Trạng thái", ProductStatuses.ALL);
        if (!newSpec) {
            Validator.requireSelected(s.getSpecId(), "phiên bản");
        }
    }
}

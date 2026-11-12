package com.phonestore.bus;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import com.phonestore.dao.ProductDAO;
import com.phonestore.dao.ProductSpecDAO;
import com.phonestore.dto.ProductDTO;
import com.phonestore.dto.ProductFilter;
import com.phonestore.dto.ProductListItemDTO;
import com.phonestore.dto.ProductSpecDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.PermissionNames;
import com.phonestore.util.SessionManager;

public class ProductBUS {

    private final ProductDAO productDAO;
    private final ProductSpecDAO productSpecDAO;

    public ProductBUS() {
        this.productDAO = new ProductDAO();
        this.productSpecDAO = new ProductSpecDAO();
    }

    public ProductBUS(ProductDAO productDAO, ProductSpecDAO productSpecDAO) {
        this.productDAO = productDAO;
        this.productSpecDAO = productSpecDAO;
    }

    public List<ProductListItemDTO> searchProducts(ProductFilter filter) {
        try {
            return productDAO.search(filter);
        } catch (SQLException e) {
            throw new BusinessException("Lỗi khi tìm kiếm sản phẩm: " + e.getMessage(), e);
        }
    }

    public List<ProductDTO> getAllProducts() {
        try {
            return productDAO.findAll();
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }

    public ProductDTO getProductById(int productId) {
        try {
            return productDAO.findById(productId);
        } catch (SQLException e) {
            throw new BusinessException("Không tìm thấy sản phẩm có mã: " + productId, e);
        }
    }

    public ProductSpecDTO getSpecById(int specId) {
        try {
            return productSpecDAO.findById(specId);
        } catch (SQLException e) {
            throw new BusinessException("Không tìm thấy phiên bản cấu hình có mã: " + specId, e);
        }
    }

    public List<ProductSpecDTO> getSpecsByProductId(int productId) {
        try {
            return productSpecDAO.findByProductId(productId);
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }

    public int addProductWithSpec(ProductDTO product, ProductSpecDTO spec) {
        checkPermission(PermissionNames.ADD_PRODUCT);
        validateProduct(product);
        validateSpec(spec);

        try {
            int newProductId = productDAO.insert(product);
            if (newProductId <= 0) {
                throw new BusinessException("Thêm sản phẩm thất bại.");
            }
            spec.setProductId(newProductId);
            int newSpecId = productSpecDAO.insert(spec);
            if (newSpecId <= 0) {
                throw new BusinessException("Thêm cấu hình sản phẩm thất bại.");
            }
            return newProductId;
        } catch (SQLException e) {
            throw new BusinessException("Lỗi lưu cơ sở dữ liệu: " + e.getMessage(), e);
        }
    }

    public boolean updateProductAndSpec(ProductDTO product, ProductSpecDTO spec) {
        checkPermission(PermissionNames.EDIT_PRODUCT);
        if (product.getProductId() <= 0 || spec.getSpecId() <= 0) {
            throw new BusinessException("Mã sản phẩm hoặc mã cấu hình không hợp lệ.");
        }
        validateProduct(product);
        validateSpec(spec);

        try {
            boolean pUpdated = productDAO.update(product);
            boolean sUpdated = productSpecDAO.update(spec);
            return pUpdated || sUpdated;
        } catch (SQLException e) {
            throw new BusinessException("Lỗi cập nhật sản phẩm: " + e.getMessage(), e);
        }
    }

    public boolean deleteProduct(int productId) {
        checkPermission(PermissionNames.DELETE_PRODUCT);
        if (productId <= 0) {
            throw new BusinessException("Mã sản phẩm không hợp lệ.");
        }
        try {
            return productDAO.delete(productId);
        } catch (SQLException e) {
            throw new BusinessException("Không thể xóa sản phẩm do đã có đơn hàng hoặc dữ liệu liên quan.", e);
        }
    }

    private void validateProduct(ProductDTO product) {
        if (product == null) {
            throw new BusinessException("Thông tin sản phẩm không được rỗng.");
        }
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            throw new BusinessException("Tên sản phẩm không được để trống.");
        }
        if (product.getBrandId() == null || product.getBrandId() <= 0) {
            throw new BusinessException("Vui lòng chọn hãng sản xuất.");
        }
        if (product.getCategoryId() == null || product.getCategoryId() <= 0) {
            throw new BusinessException("Vui lòng chọn danh mục.");
        }
    }

    private void validateSpec(ProductSpecDTO spec) {
        if (spec == null) {
            throw new BusinessException("Thông tin cấu hình không được rỗng.");
        }
        if (spec.getPrice() == null || spec.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Giá bán phải lớn hơn hoặc bằng 0.");
        }
        if (spec.getCostPrice() == null || spec.getCostPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Giá vốn phải lớn hơn hoặc bằng 0.");
        }
        if (spec.getStockQuantity() < 0) {
            throw new BusinessException("Số lượng tồn kho không được âm.");
        }
        if (spec.getStatus() == null || spec.getStatus().isBlank()) {
            spec.setStatus("Đang bán");
        }
    }

    private void checkPermission(String permission) {
        if (SessionManager.isLoggedIn() && !SessionManager.hasPermission(permission)) {
            throw new BusinessException("Bạn không có quyền thực hiện thao tác này: " + permission);
        }
    }
}
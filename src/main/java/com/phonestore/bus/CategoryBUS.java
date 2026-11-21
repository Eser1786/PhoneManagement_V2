package com.phonestore.bus;

import com.phonestore.dao.CategoryDAO;
import com.phonestore.dao.ProductDAO;
import com.phonestore.dto.CategoryDTO;
import com.phonestore.exception.BusinessException;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class CategoryBUS {

    private static final String PERM_ADD = "add_product";
    private static final String PERM_EDIT = "edit_product";
    private static final String PERM_DELETE = "delete_product";

    private final CategoryDAO categoryDAO;
    private final ProductDAO productDAO;
    private final AuthBUS authBUS;

    public CategoryBUS() {
        this(new CategoryDAO(), new ProductDAO(), new AuthBUS());
    }

    public CategoryBUS(CategoryDAO categoryDAO, ProductDAO productDAO, AuthBUS authBUS) {
        this.categoryDAO = categoryDAO;
        this.productDAO = productDAO;
        this.authBUS = authBUS;
    }

    public List<CategoryDTO> getAllCategories() {
        return categoryDAO.findAll();
    }

    public void addCategory(CategoryDTO category) {
        authBUS.requirePermission(PERM_ADD);
        validate(category);
        checkDuplicateName(category);
        if (!categoryDAO.insert(category)) {
            throw new BusinessException("Không thể thêm danh mục. Vui lòng thử lại.");
        }
    }

    public void updateCategory(CategoryDTO category) {
        authBUS.requirePermission(PERM_EDIT);
        if (category.getCategoryId() <= 0 || categoryDAO.findById(category.getCategoryId()) == null) {
            throw new BusinessException("Không tìm thấy danh mục cần sửa");
        }
        validate(category);
        checkDuplicateName(category);
        if (!categoryDAO.update(category)) {
            throw new BusinessException("Không thể cập nhật danh mục. Vui lòng thử lại.");
        }
    }

    public void deleteCategory(int categoryId) {
        authBUS.requirePermission(PERM_DELETE);
        if (categoryId <= 0) {
            throw new BusinessException("Mã danh mục không hợp lệ");
        }
        long count;
        try {
            count = productDAO.findAll().stream()
                    .filter(p -> Objects.equals(p.getCategoryId(), categoryId))
                    .count();
        } catch (SQLException e) {
            throw new BusinessException("Không thể kiểm tra sản phẩm thuộc danh mục", e);
        }
        if (count > 0) {
            throw new BusinessException("Không thể xóa: còn " + count
                    + " sản phẩm thuộc danh mục này. Hãy xóa hoặc chuyển các sản phẩm đó trước.");
        }
        if (!categoryDAO.delete(categoryId)) {
            throw new BusinessException("Không thể xóa danh mục. Vui lòng thử lại.");
        }
    }

    private void validate(CategoryDTO category) {
        category.setCategoryName(clean(category.getCategoryName()));
        category.setDescription(clean(category.getDescription()));

        if (category.getCategoryName() == null) {
            throw new BusinessException("Vui lòng nhập tên danh mục");
        }
        if (category.getCategoryName().length() > 100) {
            throw new BusinessException("Tên danh mục tối đa 100 ký tự");
        }
    }

    private void checkDuplicateName(CategoryDTO category) {
        for (CategoryDTO c : categoryDAO.searchByName(category.getCategoryName())) {
            boolean sameName = c.getCategoryName().equalsIgnoreCase(category.getCategoryName());
            boolean otherRecord = c.getCategoryId() != category.getCategoryId();
            if (sameName && otherRecord) {
                throw new BusinessException("Danh mục \"" + category.getCategoryName() + "\" đã tồn tại");
            }
        }
    }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
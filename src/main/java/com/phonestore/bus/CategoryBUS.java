package com.phonestore.bus;

import java.util.Collections;
import java.util.List;

import com.phonestore.dao.CategoryDAO;
import com.phonestore.dto.CategoryDTO;
import com.phonestore.exception.BusinessException;

public class CategoryBUS {

    private final CategoryDAO categoryDAO;

    public CategoryBUS() {
        this.categoryDAO = new CategoryDAO();
    }

    public CategoryBUS(CategoryDAO categoryDAO) {
        this.categoryDAO = categoryDAO;
    }

    public List<CategoryDTO> getAllCategories() {
        try {
            return categoryDAO.findAll();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public CategoryDTO getCategoryById(int categoryId) {
        return categoryDAO.findById(categoryId);
    }

    public List<CategoryDTO> searchCategories(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getAllCategories();
        }
        return categoryDAO.searchByName(keyword.trim());
    }

    public boolean addCategory(CategoryDTO category) {
        validateCategory(category);
        return categoryDAO.insert(category);
    }

    public boolean updateCategory(CategoryDTO category) {
        if (category.getCategoryId() <= 0) {
            throw new BusinessException("Mã danh mục không hợp lệ.");
        }
        validateCategory(category);
        return categoryDAO.update(category);
    }

    public boolean deleteCategory(int categoryId) {
        if (categoryId <= 0) {
            throw new BusinessException("Mã danh mục không hợp lệ.");
        }
        return categoryDAO.delete(categoryId);
    }

    private void validateCategory(CategoryDTO category) {
        if (category == null) {
            throw new BusinessException("Dữ liệu danh mục không được để trống.");
        }
        if (category.getCategoryName() == null || category.getCategoryName().trim().isEmpty()) {
            throw new BusinessException("Tên danh mục không được để trống.");
        }
    }
}
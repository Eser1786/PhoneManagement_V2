package com.phonestore.bus;

import java.util.Collections;
import java.util.List;

import com.phonestore.dao.BrandDAO;
import com.phonestore.dto.BrandDTO;
import com.phonestore.exception.BusinessException;

public class BrandBUS {

    private final BrandDAO brandDAO;

    public BrandBUS() {
        this.brandDAO = new BrandDAO();
    }

    public BrandBUS(BrandDAO brandDAO) {
        this.brandDAO = brandDAO;
    }

    public List<BrandDTO> getAllBrands() {
        try {
            return brandDAO.findAll();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public BrandDTO getBrandById(int brandId) {
        return brandDAO.findById(brandId);
    }

    public List<BrandDTO> searchBrands(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getAllBrands();
        }
        return brandDAO.searchByName(keyword.trim());
    }

    public boolean addBrand(BrandDTO brand) {
        validateBrand(brand);
        return brandDAO.insert(brand);
    }

    public boolean updateBrand(BrandDTO brand) {
        if (brand.getBrandId() <= 0) {
            throw new BusinessException("Mã thương hiệu không hợp lệ.");
        }
        validateBrand(brand);
        return brandDAO.update(brand);
    }

    public boolean deleteBrand(int brandId) {
        if (brandId <= 0) {
            throw new BusinessException("Mã thương hiệu không hợp lệ.");
        }
        return brandDAO.delete(brandId);
    }

    private void validateBrand(BrandDTO brand) {
        if (brand == null) {
            throw new BusinessException("Dữ liệu thương hiệu không được để trống.");
        }
        if (brand.getBrandName() == null || brand.getBrandName().trim().isEmpty()) {
            throw new BusinessException("Tên thương hiệu không được để trống.");
        }
    }
}
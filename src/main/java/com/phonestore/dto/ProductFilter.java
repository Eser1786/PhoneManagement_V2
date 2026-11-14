package com.phonestore.dto;

import java.math.BigDecimal;

/**
 * Điều kiện lọc khi tìm kiếm sản phẩm.
 * Field nào để null thì không lọc theo điều kiện đó.
 *
 * Ví dụ: lọc Apple, giá từ 10 đến 30 triệu, đang bán:
 *   ProductFilter f = new ProductFilter();
 *   f.setBrandId(1);
 *   f.setMinPrice(new BigDecimal("10000000"));
 *   f.setMaxPrice(new BigDecimal("30000000"));
 *   f.setStatus("Đang bán");
 *
 * Không lọc gì cả: new ProductFilter() (tất cả field đều null).
 */
public class ProductFilter {

    private Integer brandId;
    private Integer categoryId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String status;
    private String keyword;   // tìm theo tên sản phẩm
    private Integer maxStock; // chỉ lấy phiên bản còn tồn <= giá trị này (báo cáo sắp hết hàng)

    public ProductFilter() {
    }

    public Integer getBrandId() {
        return brandId;
    }

    public void setBrandId(Integer brandId) {
        this.brandId = brandId;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getMaxStock() {
        return maxStock;
    }

    public void setMaxStock(Integer maxStock) {
        this.maxStock = maxStock;
    }
}

package com.phonestore.dto;

import java.math.BigDecimal;

public class ProductFilter {
    
    private Integer brandId;
    private Integer categoryId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String status;
    private String keyword;

    public ProductFilter(){
    }

    public Integer getBrandId(){
        return brandId;
    }

    public void setBrandId(Integer brandId){
        this.brandId = brandId;
    }

    public Integer getCategoryId(){
        return categoryId;
    }

    public void setCategoryId(Integer categoryId){
        this.categoryId = categoryId;
    }

    public BigDecimal getMinPrice(){
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice){
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice(){
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice){
        this.maxPrice = maxPrice;
    }

    public String getStatus(){
        return status;
    }

    public void setStatus(String status){
        this.status = status;
    }

    public String getKeyword(){
        return keyword;
    }

    public void setKeyword(String keyword){
        this.keyword = keyword;
    }
}

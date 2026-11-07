package com.phonestore.dto;

import java.math.BigDecimal;

public class ProductListItemDTO {
 
    private int specId;
    private int productId;
    private String productName;
    private String brandName;
    private String categoryName;
    private BigDecimal price;
    private int stockQuantity;
    private String ram;
    private String storage;
    private String color;
    private String status;
    
    public ProductListItemDTO(){
    }

    public int getSpecId(){
        return specId;
    }

    public void setSpecId(int specId){
        this.specId = specId;
    }
    
    public int getProductId(){
        return productId;
    }

    public void setProductId(int productId){
        this.productId = productId;
    }

    public String getProductName(){
        return productName;
    }

    public void setProductName(String productName){
        this.productName = productName;
    }

    public String getBrandName(){
        return brandName;
    }   

    public void setBrandName(String brandName){
        this.brandName = brandName;
    }

    public String getCategoryName(){
        return categoryName;
    }

    public void setCategoryName(String categoryName){
        this.categoryName = categoryName;
    }

    public BigDecimal getPrice(){
        return price;
    }

    public void setPrice(BigDecimal price){
        this.price = price;
    }

    public int getStockQuantity(){
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity){
        this.stockQuantity = stockQuantity;
    }

    public String getRam(){
        return ram;
    }

    public void setRam(String ram){
        this.ram = ram;
    }

    public String getStorage(){
        return storage;
    }

    public void setStorage(String storage){
        this.storage = storage;
    }

    public String getColor(){
        return color;
    }

    public void setColor(String color){
        this.color = color;
    }

    public String getStatus(){
        return status;
    }

    public void setStatus(String status){
        this.status = status;
    }

    @Override 
    public String toString() {
        return "ProductListItemDTO{" +
                "specId=" + specId +
                ", productName='" + productName + '\'' +
                ", brandName='" + brandName + '\'' +
                ", categoryName='" + categoryName + '\'' +
                ", price=" + price +
                ", stockQuantity=" + stockQuantity +
                ", ram='" + ram + '\'' +
                ", storage='" + storage + '\'' +
                ", color='" + color + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
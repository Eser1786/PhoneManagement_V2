package com.phonestore.dto;

public class ProductDTO {
    private int productId;
    private String name;
    private Integer brandId;
    private Integer categoryId;
    private String description;

    public ProductDTO() {}

    public ProductDTO(int productId, String name, Integer brandId, Integer categoryId, String description) {
        this.productId = productId;
        this.name = name;
        this.brandId = brandId;
        this.categoryId = categoryId;
        this.description = description;
    }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getBrandId() { return brandId; }
    public void setBrandId(Integer brandId) { this.brandId = brandId; }
    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
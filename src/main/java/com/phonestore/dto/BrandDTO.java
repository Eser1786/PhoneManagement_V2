package com.phonestore.dto;

public class BrandDTO {
    private int brandId;
    private String brandName;
    private String country;
    private String logoUrl;
    private String description;

    public BrandDTO() {}

    public BrandDTO(int brandId, String brandName, String country, String logoUrl, String description) {
        this.brandId = brandId;
        this.brandName = brandName;
        this.country = country;
        this.logoUrl = logoUrl;
        this.description = description;
    }

    public int getBrandId() { return brandId; }
    public void setBrandId(int brandId) { this.brandId = brandId; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
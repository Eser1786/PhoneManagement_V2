package com.phonestore.dto;

import java.math.BigDecimal;

public class ProductSpecDTO {
    private int specId;
    private Integer productId;
    private BigDecimal price;
    private BigDecimal costPrice;
    private int stockQuantity;
    private String screenSize;
    private String processor;
    private String resolution;
    private String ram;
    private String storage;
    private String battery;
    private String cameraMain;
    private String cameraFront;
    private String weight;
    private String os;
    private String color;
    private String warrantyPeriod;
    private String status;

    public ProductSpecDTO() {}

    public ProductSpecDTO(int specId, Integer productId, BigDecimal price, BigDecimal costPrice, int stockQuantity,
                          String screenSize, String processor, String resolution, String ram, String storage,
                          String battery, String cameraMain, String cameraFront, String weight, String os,
                          String color, String warrantyPeriod, String status) {
        this.specId = specId;
        this.productId = productId;
        this.price = price;
        this.costPrice = costPrice;
        this.stockQuantity = stockQuantity;
        this.screenSize = screenSize;
        this.processor = processor;
        this.resolution = resolution;
        this.ram = ram;
        this.storage = storage;
        this.battery = battery;
        this.cameraMain = cameraMain;
        this.cameraFront = cameraFront;
        this.weight = weight;
        this.os = os;
        this.color = color;
        this.warrantyPeriod = warrantyPeriod;
        this.status = status;
    }

    public int getSpecId() { return specId; }
    public void setSpecId(int specId) { this.specId = specId; }
    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }
    public String getScreenSize() { return screenSize; }
    public void setScreenSize(String screenSize) { this.screenSize = screenSize; }
    public String getProcessor() { return processor; }
    public void setProcessor(String processor) { this.processor = processor; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public String getRam() { return ram; }
    public void setRam(String ram) { this.ram = ram; }
    public String getStorage() { return storage; }
    public void setStorage(String storage) { this.storage = storage; }
    public String getBattery() { return battery; }
    public void setBattery(String battery) { this.battery = battery; }
    public String getCameraMain() { return cameraMain; }
    public void setCameraMain(String cameraMain) { this.cameraMain = cameraMain; }
    public String getCameraFront() { return cameraFront; }
    public void setCameraFront(String cameraFront) { this.cameraFront = cameraFront; }
    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }
    public String getOs() { return os; }
    public void setOs(String os) { this.os = os; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getWarrantyPeriod() { return warrantyPeriod; }
    public void setWarrantyPeriod(String warrantyPeriod) { this.warrantyPeriod = warrantyPeriod; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
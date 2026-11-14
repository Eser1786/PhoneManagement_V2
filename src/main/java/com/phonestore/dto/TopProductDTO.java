package com.phonestore.dto;

import java.math.BigDecimal;

/** Một dòng của bảng "sản phẩm bán chạy": mỗi dòng là một phiên bản (dung lượng, màu). */
public class TopProductDTO {

    private int specId;
    private String productName;
    private String storage;
    private String color;
    private int quantitySold;
    private BigDecimal revenue;

    public TopProductDTO() {
    }

    /** Tên đầy đủ để hiển thị, ví dụ "iPhone 15 Pro 128GB Titan tự nhiên". */
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder(productName == null ? "" : productName);
        if (storage != null && !storage.isBlank()) {
            sb.append(' ').append(storage);
        }
        if (color != null && !color.isBlank()) {
            sb.append(' ').append(color);
        }
        return sb.toString();
    }

    public int getSpecId() { return specId; }
    public void setSpecId(int specId) { this.specId = specId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getStorage() { return storage; }
    public void setStorage(String storage) { this.storage = storage; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public int getQuantitySold() { return quantitySold; }
    public void setQuantitySold(int quantitySold) { this.quantitySold = quantitySold; }

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
}

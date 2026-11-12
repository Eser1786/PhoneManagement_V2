package com.phonestore.dto;

import java.math.BigDecimal;

/** Một dòng hàng của đơn, đã kèm tên sản phẩm, dung lượng, màu để hiển thị và in hóa đơn. */
public class OrderDetailItemDTO {

    private int orderDetailId;
    private int specId;
    private String productName;
    private String storage;
    private String color;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountPercent;
    private BigDecimal subtotal;          // thành tiền sau chiết khấu

    public OrderDetailItemDTO() {
    }

    /** Tiền hàng của dòng trước chiết khấu = đơn giá x số lượng. */
    public BigDecimal getGrossAmount() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
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

    public int getOrderDetailId() { return orderDetailId; }
    public void setOrderDetailId(int orderDetailId) { this.orderDetailId = orderDetailId; }

    public int getSpecId() { return specId; }
    public void setSpecId(int specId) { this.specId = specId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getStorage() { return storage; }
    public void setStorage(String storage) { this.storage = storage; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
}

package com.phonestore.dto;

import java.math.BigDecimal;

public class OrderDetailDTO {
    private int orderDetailId;
    private Integer orderId;
    private Integer specId;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountPercent;
    private BigDecimal subtotal;

    public OrderDetailDTO() {}

    public OrderDetailDTO(int orderDetailId, Integer orderId, Integer specId, int quantity,
                          BigDecimal unitPrice, BigDecimal discountPercent, BigDecimal subtotal) {
        this.orderDetailId = orderDetailId;
        this.orderId = orderId;
        this.specId = specId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.discountPercent = discountPercent;
        this.subtotal = subtotal;
    }

    public int getOrderDetailId() { return orderDetailId; }
    public void setOrderDetailId(int orderDetailId) { this.orderDetailId = orderDetailId; }
    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }
    public Integer getSpecId() { return specId; }
    public void setSpecId(Integer specId) { this.specId = specId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
}
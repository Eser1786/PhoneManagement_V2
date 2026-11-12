package com.phonestore.dto;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Một dòng trong danh sách đơn hàng, đã kèm tên khách hàng và tên nhân viên (join với Users)
 * để GUI hiển thị luôn mà không phải tra thêm.
 */
public class OrderListItemDTO {

    private int orderId;
    private Timestamp orderDate;
    private Integer customerId;       // null = khách vãng lai
    private String customerName;      // null nếu khách vãng lai
    private String customerPhone;
    private Integer staffId;
    private String staffName;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private String paymentStatus;
    private String orderStatus;
    private Timestamp deliveryDate;   // chỉ có khi đơn đã hoàn thành
    private String notes;

    public OrderListItemDTO() {
    }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public Timestamp getOrderDate() { return orderDate; }
    public void setOrderDate(Timestamp orderDate) { this.orderDate = orderDate; }

    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public Integer getStaffId() { return staffId; }
    public void setStaffId(Integer staffId) { this.staffId = staffId; }

    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }

    public Timestamp getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(Timestamp deliveryDate) { this.deliveryDate = deliveryDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}

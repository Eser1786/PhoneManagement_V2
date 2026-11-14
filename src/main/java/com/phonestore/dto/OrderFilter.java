package com.phonestore.dto;

import java.time.LocalDate;

/**
 * Điều kiện lọc danh sách đơn hàng. Field nào để null thì không lọc theo điều kiện đó.
 * new OrderFilter() = lấy tất cả đơn.
 *
 *   OrderFilter f = new OrderFilter();
 *   f.setOrderStatus(OrderConstants.ORDER_STATUS_PENDING);
 *   f.setFromDate(LocalDate.of(2026, 11, 1));     // tính cả ngày đầu
 *   f.setToDate(LocalDate.of(2026, 11, 30));      // tính cả ngày cuối
 *   f.setKeyword("An");   // số mã đơn hoặc một phần tên / số điện thoại khách hàng
 */
public class OrderFilter {

    private Integer customerId;
    private String orderStatus;
    private String paymentStatus;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String keyword;

    public OrderFilter() {
    }

    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }

    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }

    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}

package com.phonestore.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Toàn bộ dữ liệu để in hóa đơn của một đơn hàng: thông tin đơn, địa chỉ khách, các dòng hàng
 * và ba số tiền đã tính sẵn. Màn hình in chỉ cần hiển thị, không phải tự tính lại.
 *
 *   tổng tiền hàng (grossTotal) - tổng giảm giá (discountTotal) = tổng thanh toán (totalAmount)
 */
public class InvoiceDTO {

    private OrderListItemDTO order;
    private String customerAddress;               // null nếu khách vãng lai
    private List<OrderDetailItemDTO> items;
    private BigDecimal grossTotal;
    private BigDecimal discountTotal;
    private BigDecimal totalAmount;

    public InvoiceDTO() {
    }

    public OrderListItemDTO getOrder() { return order; }
    public void setOrder(OrderListItemDTO order) { this.order = order; }

    public String getCustomerAddress() { return customerAddress; }
    public void setCustomerAddress(String customerAddress) { this.customerAddress = customerAddress; }

    public List<OrderDetailItemDTO> getItems() { return items; }
    public void setItems(List<OrderDetailItemDTO> items) { this.items = items; }

    public BigDecimal getGrossTotal() { return grossTotal; }
    public void setGrossTotal(BigDecimal grossTotal) { this.grossTotal = grossTotal; }

    public BigDecimal getDiscountTotal() { return discountTotal; }
    public void setDiscountTotal(BigDecimal discountTotal) { this.discountTotal = discountTotal; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
}

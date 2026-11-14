package com.phonestore.util;

import com.phonestore.dto.InvoiceDTO;
import com.phonestore.dto.OrderDetailItemDTO;
import com.phonestore.dto.OrderListItemDTO;

import java.math.BigDecimal;

/**
 * Dựng hóa đơn dạng chữ (rộng 48 cột, dùng font monospace) từ InvoiceDTO, để in hoặc hiện
 * trong một TextArea:
 *
 *   InvoiceDTO invoice = orderBUS.getInvoice(orderId);
 *   String text = InvoiceFormatter.toText(invoice);
 *
 * Muốn bố cục đẹp hơn, GUI có thể tự dựng giao diện từ các field của InvoiceDTO.
 */
public final class InvoiceFormatter {

    public static final String STORE_NAME = "CỬA HÀNG ĐIỆN THOẠI";
    private static final int WIDTH = 48;
    private static final String LINE = "-".repeat(WIDTH);

    private InvoiceFormatter() {
    }

    public static String toText(InvoiceDTO invoice) {
        OrderListItemDTO order = invoice.getOrder();
        StringBuilder sb = new StringBuilder();

        sb.append(center(STORE_NAME)).append('\n');
        sb.append(center("HÓA ĐƠN BÁN HÀNG")).append('\n');
        sb.append(LINE).append('\n');
        sb.append(twoColumns("Số: #" + order.getOrderId(), "Ngày: " + FormatUtil.dateTime(order.getOrderDate()))).append('\n');

        String customer = order.getCustomerName() == null ? "Khách vãng lai" : order.getCustomerName();
        if (order.getCustomerPhone() != null && !order.getCustomerPhone().isBlank()) {
            customer += " (" + order.getCustomerPhone() + ")";
        }
        sb.append("Khách hàng: ").append(customer).append('\n');
        if (invoice.getCustomerAddress() != null && !invoice.getCustomerAddress().isBlank()) {
            sb.append("Địa chỉ: ").append(invoice.getCustomerAddress()).append('\n');
        }
        if (order.getStaffName() != null) {
            sb.append("Nhân viên: ").append(order.getStaffName()).append('\n');
        }
        sb.append(LINE).append('\n');

        int index = 1;
        for (OrderDetailItemDTO item : invoice.getItems()) {
            sb.append(index++).append(". ").append(item.getDisplayName()).append('\n');
            String left = "   " + item.getQuantity() + " x " + FormatUtil.formatCurrency(item.getUnitPrice());
            if (item.getDiscountPercent() != null && item.getDiscountPercent().signum() > 0) {
                left += "  (-" + item.getDiscountPercent().stripTrailingZeros().toPlainString() + "%)";
            }
            sb.append(twoColumns(left, FormatUtil.formatCurrency(item.getSubtotal()))).append('\n');
        }
        sb.append(LINE).append('\n');

        sb.append(twoColumns("Tiền hàng:", FormatUtil.formatCurrency(invoice.getGrossTotal()))).append('\n');
        if (invoice.getDiscountTotal().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(twoColumns("Giảm giá:", "-" + FormatUtil.formatCurrency(invoice.getDiscountTotal()))).append('\n');
        }
        sb.append(twoColumns("TỔNG CỘNG:", FormatUtil.formatCurrency(invoice.getTotalAmount()))).append('\n');
        sb.append(LINE).append('\n');
        sb.append("Thanh toán: ").append(order.getPaymentMethod())
                .append(" (").append(order.getPaymentStatus()).append(")\n");
        sb.append("Trạng thái đơn: ").append(order.getOrderStatus()).append('\n');
        if (order.getNotes() != null && !order.getNotes().isBlank()) {
            sb.append("Ghi chú: ").append(order.getNotes()).append('\n');
        }
        sb.append(LINE).append('\n');
        sb.append(center("Cảm ơn quý khách!")).append('\n');
        return sb.toString();
    }

    /** Chữ bên trái và chữ bên phải trên cùng một dòng rộng WIDTH; thiếu chỗ thì cách nhau một dấu cách. */
    private static String twoColumns(String left, String right) {
        int gap = WIDTH - left.length() - right.length();
        return left + " ".repeat(Math.max(1, gap)) + right;
    }

    private static String center(String text) {
        int pad = Math.max(0, (WIDTH - text.length()) / 2);
        return " ".repeat(pad) + text;
    }
}

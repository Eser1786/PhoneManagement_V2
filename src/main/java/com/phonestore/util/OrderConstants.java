package com.phonestore.util;

import java.util.List;
import java.util.Map;

/**
 * Các giá trị cố định của đơn hàng, dùng chung cho DAO, BUS và GUI (ví dụ đổ vào ComboBox).
 * Giá trị chuỗi chính là nội dung lưu trong database.
 */
public final class OrderConstants {

    // Phương thức thanh toán
    public static final String PAYMENT_CASH = "Tiền mặt";
    public static final String PAYMENT_TRANSFER = "Chuyển khoản";
    public static final String PAYMENT_CARD = "Thẻ";
    public static final List<String> PAYMENT_METHODS = List.of(PAYMENT_CASH, PAYMENT_TRANSFER, PAYMENT_CARD);

    // Trạng thái thanh toán
    public static final String PAYMENT_STATUS_UNPAID = "Chưa thanh toán";
    public static final String PAYMENT_STATUS_PAID = "Đã thanh toán";
    public static final String PAYMENT_STATUS_REFUNDED = "Đã hoàn tiền";
    public static final List<String> PAYMENT_STATUSES =
            List.of(PAYMENT_STATUS_UNPAID, PAYMENT_STATUS_PAID, PAYMENT_STATUS_REFUNDED);

    // Trạng thái đơn hàng
    public static final String ORDER_STATUS_PENDING = "Chờ xử lý";
    public static final String ORDER_STATUS_SHIPPING = "Đang giao";
    public static final String ORDER_STATUS_COMPLETED = "Hoàn thành";
    public static final String ORDER_STATUS_CANCELLED = "Đã hủy";
    public static final List<String> ORDER_STATUSES =
            List.of(ORDER_STATUS_PENDING, ORDER_STATUS_SHIPPING, ORDER_STATUS_COMPLETED, ORDER_STATUS_CANCELLED);

    /**
     * Các trạng thái đơn được phép chuyển tới từ một trạng thái. Hoàn thành và Đã hủy là trạng thái cuối.
     * Chờ xử lý có thể đi thẳng tới Hoàn thành (bán tại quầy, giao ngay).
     */
    public static final Map<String, List<String>> NEXT_ORDER_STATUSES = Map.of(
            ORDER_STATUS_PENDING, List.of(ORDER_STATUS_SHIPPING, ORDER_STATUS_COMPLETED, ORDER_STATUS_CANCELLED),
            ORDER_STATUS_SHIPPING, List.of(ORDER_STATUS_COMPLETED, ORDER_STATUS_CANCELLED),
            ORDER_STATUS_COMPLETED, List.of(),
            ORDER_STATUS_CANCELLED, List.of());

    /** Giá trị Inventory.transaction_type. */
    public static final String INVENTORY_OUT = "Xuất kho";
    public static final String INVENTORY_IN = "Nhập kho";

    private OrderConstants() {
    }
}

package com.phonestore.dao;

import com.phonestore.config.DBConnection;
import com.phonestore.dto.OrderDTO;
import com.phonestore.dto.OrderDetailDTO;
import com.phonestore.dto.OrderDetailItemDTO;
import com.phonestore.dto.OrderFilter;
import com.phonestore.dto.OrderListItemDTO;
import com.phonestore.exception.OutOfStockException;
import com.phonestore.util.OrderConstants;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Truy cập bảng Orders và OrderDetails.
 *
 * Các hàm ghi nhiều bảng (createOrder, cancelOrder) chạy trong MỘT transaction, thành công cả hoặc
 * không có gì. DAO không tính tiền hay kiểm tra nghiệp vụ: việc đó của OrderBUS.
 */
public class OrderDAO {

    /** Giữ lại tên cũ cho mã đã dùng nó; nên dùng OrderConstants.INVENTORY_OUT. */
    public static final String TRANSACTION_TYPE_OUT = OrderConstants.INVENTORY_OUT;

    /** SELECT và JOIN dùng chung cho danh sách và chi tiết một đơn (đã kèm tên khách và tên nhân viên). */
    private static final String LIST_SELECT =
            "SELECT o.order_id, o.order_date, o.customer_id, cu.full_name AS customer_name, "
                    + "cu.phone AS customer_phone, o.staff_id, su.full_name AS staff_name, o.total_amount, "
                    + "o.payment_method, o.payment_status, o.order_status, o.delivery_date, o.notes "
                    + "FROM Orders o "
                    + "LEFT JOIN Customers c ON c.customer_id = o.customer_id "
                    + "LEFT JOIN Users cu ON cu.user_id = c.user_id "
                    + "LEFT JOIN Staff st ON st.staff_id = o.staff_id "
                    + "LEFT JOIN Users su ON su.user_id = st.user_id ";

    // ================================================================ tạo đơn

    /**
     * Lưu đơn hàng trong một transaction: thêm Orders, thêm OrderDetails, trừ stock_quantity (chỉ trừ nếu
     * còn đủ hàng), ghi Inventory "Xuất kho", cộng Customers.total_spent. Lỗi ở bước nào thì rollback hết.
     * Trả về order_id mới.
     *
     * @throws OutOfStockException nếu có phiên bản không còn đủ hàng khi trừ kho
     * @throws SQLException        lỗi SQL (kể cả khách hàng / nhân viên không tồn tại: vi phạm khóa ngoại)
     */
    public int createOrder(OrderDTO order, List<OrderDetailDTO> details)
            throws SQLException, OutOfStockException {

        // Luôn xử lý theo thứ tự spec_id tăng dần để hai đơn cùng lúc không khóa chéo nhau (deadlock)
        List<OrderDetailDTO> sorted = new ArrayList<>(details);
        sorted.sort(Comparator.comparingInt(OrderDetailDTO::getSpecId));

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int orderId = insertOrder(conn, order);

                for (OrderDetailDTO d : sorted) {
                    lockSpecRowForUpdate(conn, d.getSpecId());
                    insertDetail(conn, orderId, d);
                    decreaseStock(conn, d.getSpecId(), d.getQuantity());
                    insertInventory(conn, d.getSpecId(), 0, d.getQuantity(), OrderConstants.INVENTORY_OUT,
                            "Bán hàng - đơn #" + orderId);
                }

                if (order.getCustomerId() != null) {
                    changeTotalSpent(conn, order.getCustomerId(), order.getTotalAmount());
                }

                conn.commit();
                return orderId;
            } catch (SQLException | OutOfStockException | RuntimeException e) {
                rollbackQuietly(conn, e);
                throw e;
            } finally {
                conn.setAutoCommit(true);   // trả kết nối về pool ở trạng thái mặc định
            }
        }
    }

    // ================================================================ đọc đơn

    /** Danh sách đơn theo bộ lọc, mới nhất trước. Filter null = tất cả. */
    public List<OrderListItemDTO> search(OrderFilter f) throws SQLException {
        StringBuilder sql = new StringBuilder(LIST_SELECT).append("WHERE 1 = 1");
        List<Object> params = new ArrayList<>();

        if (f != null) {
            if (f.getCustomerId() != null) {
                sql.append(" AND o.customer_id = ?");
                params.add(f.getCustomerId());
            }
            if (f.getOrderStatus() != null && !f.getOrderStatus().isBlank()) {
                sql.append(" AND o.order_status = ?");
                params.add(f.getOrderStatus());
            }
            if (f.getPaymentStatus() != null && !f.getPaymentStatus().isBlank()) {
                sql.append(" AND o.payment_status = ?");
                params.add(f.getPaymentStatus());
            }
            if (f.getFromDate() != null) {
                sql.append(" AND o.order_date >= ?");
                params.add(Timestamp.valueOf(f.getFromDate().atStartOfDay()));
            }
            if (f.getToDate() != null) {
                sql.append(" AND o.order_date < ?");   // hết ngày toDate = đầu ngày kế tiếp
                params.add(Timestamp.valueOf(f.getToDate().plusDays(1).atStartOfDay()));
            }
            if (f.getKeyword() != null && !f.getKeyword().isBlank()) {
                String keyword = f.getKeyword().trim();
                String like = "%" + escapeLike(keyword) + "%";
                if (keyword.matches("\\d{1,9}")) {
                    sql.append(" AND (o.order_id = ? OR cu.phone LIKE ? OR cu.full_name LIKE ?)");
                    params.add(Integer.parseInt(keyword));
                } else {
                    sql.append(" AND (cu.phone LIKE ? OR cu.full_name LIKE ?)");
                }
                params.add(like);
                params.add(like);
            }
        }
        sql.append(" ORDER BY o.order_date DESC, o.order_id DESC");

        List<OrderListItemDTO> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapListItem(rs));
                }
            }
        }
        return result;
    }

    /** Một đơn theo mã. Trả về null nếu không có. */
    public OrderListItemDTO findListItemById(int orderId) throws SQLException {
        String sql = LIST_SELECT + "WHERE o.order_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapListItem(rs) : null;
            }
        }
    }

    /** Các dòng hàng của một đơn, kèm tên sản phẩm. */
    public List<OrderDetailItemDTO> findItems(int orderId) throws SQLException {
        String sql = "SELECT od.order_detail_id, od.spec_id, p.name AS product_name, s.storage, s.color, "
                + "od.quantity, od.unit_price, od.discount_percent, od.subtotal "
                + "FROM OrderDetails od "
                + "JOIN ProductSpecs s ON s.spec_id = od.spec_id "
                + "JOIN Products p ON p.product_id = s.product_id "
                + "WHERE od.order_id = ? ORDER BY od.order_detail_id";
        List<OrderDetailItemDTO> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderDetailItemDTO item = new OrderDetailItemDTO();
                    item.setOrderDetailId(rs.getInt("order_detail_id"));
                    item.setSpecId(rs.getInt("spec_id"));
                    item.setProductName(rs.getString("product_name"));
                    item.setStorage(rs.getString("storage"));
                    item.setColor(rs.getString("color"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setUnitPrice(rs.getBigDecimal("unit_price"));
                    item.setDiscountPercent(rs.getBigDecimal("discount_percent"));
                    item.setSubtotal(rs.getBigDecimal("subtotal"));
                    result.add(item);
                }
            }
        }
        return result;
    }

    /** Địa chỉ khách hàng dạng "địa chỉ, thành phố" (dùng cho hóa đơn). Null nếu không có khách. */
    public String findCustomerAddress(int customerId) throws SQLException {
        String sql = "SELECT address, city FROM Customers WHERE customer_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("address") + ", " + rs.getString("city") : null;
            }
        }
    }

    // ================================================================ cập nhật trạng thái

    /**
     * Đổi trạng thái đơn từ expectedCurrent sang newStatus. Chỉ đổi nếu đơn VẪN đang ở expectedCurrent,
     * nên hai người cùng thao tác thì chỉ một người thành công. Sang "Hoàn thành" thì ghi ngày giao.
     * Trả về false nếu không đổi được (đơn không tồn tại hoặc trạng thái đã khác).
     */
    public boolean updateOrderStatus(int orderId, String expectedCurrent, String newStatus) throws SQLException {
        String sql = "UPDATE Orders SET order_status = ?, "
                + "delivery_date = CASE WHEN ? = ? THEN NOW() ELSE delivery_date END "
                + "WHERE order_id = ? AND order_status = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setString(2, newStatus);
            ps.setString(3, OrderConstants.ORDER_STATUS_COMPLETED);
            ps.setInt(4, orderId);
            ps.setString(5, expectedCurrent);
            return ps.executeUpdate() > 0;
        }
    }

    /** Đổi trạng thái thanh toán, cũng chỉ khi trạng thái hiện tại đúng như expectedCurrent. */
    public boolean updatePaymentStatus(int orderId, String expectedCurrent, String newStatus) throws SQLException {
        String sql = "UPDATE Orders SET payment_status = ? WHERE order_id = ? AND payment_status = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setInt(2, orderId);
            ps.setString(3, expectedCurrent);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Hủy đơn trong một transaction: đổi trạng thái thành "Đã hủy" (đơn đã thanh toán thì thành
     * "Đã hoàn tiền"), hoàn lại tồn kho, ghi Inventory "Nhập kho", trừ lại Customers.total_spent.
     * Chỉ hủy được đơn đang "Chờ xử lý" hoặc "Đang giao".
     * Trả về false (không thay đổi gì) nếu đơn không tồn tại hoặc không còn hủy được.
     */
    public boolean cancelOrder(int orderId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer customerId;
                BigDecimal total;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT customer_id, total_amount, order_status FROM Orders WHERE order_id = ? FOR UPDATE")) {
                    ps.setInt(1, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        String status = rs.getString("order_status");
                        if (!OrderConstants.ORDER_STATUS_PENDING.equals(status)
                                && !OrderConstants.ORDER_STATUS_SHIPPING.equals(status)) {
                            conn.rollback();
                            return false;
                        }
                        int cid = rs.getInt("customer_id");
                        customerId = rs.wasNull() ? null : cid;
                        total = rs.getBigDecimal("total_amount");
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE Orders SET order_status = ?, "
                                + "payment_status = CASE WHEN payment_status = ? THEN ? ELSE payment_status END "
                                + "WHERE order_id = ?")) {
                    ps.setString(1, OrderConstants.ORDER_STATUS_CANCELLED);
                    ps.setString(2, OrderConstants.PAYMENT_STATUS_PAID);
                    ps.setString(3, OrderConstants.PAYMENT_STATUS_REFUNDED);
                    ps.setInt(4, orderId);
                    ps.executeUpdate();
                }

                // Hoàn kho theo thứ tự spec_id tăng dần (cùng thứ tự khóa với createOrder)
                List<int[]> lines = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT spec_id, quantity FROM OrderDetails WHERE order_id = ? ORDER BY spec_id")) {
                    ps.setInt(1, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            lines.add(new int[]{rs.getInt("spec_id"), rs.getInt("quantity")});
                        }
                    }
                }
                for (int[] line : lines) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE ProductSpecs SET stock_quantity = stock_quantity + ? WHERE spec_id = ?")) {
                        ps.setInt(1, line[1]);
                        ps.setInt(2, line[0]);
                        ps.executeUpdate();
                    }
                    insertInventory(conn, line[0], line[1], 0, OrderConstants.INVENTORY_IN, "Hủy đơn #" + orderId);
                }

                if (customerId != null) {
                    changeTotalSpent(conn, customerId, total.negate());
                }

                conn.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(conn, e);
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // ---------------------------------------------------------------- các bước trong transaction

    private int insertOrder(Connection conn, OrderDTO o) throws SQLException {
        // delivery_date và order_date để MySQL tự điền (mặc định)
        String sql = "INSERT INTO Orders(customer_id, staff_id, total_amount, payment_method, "
                + "payment_status, order_status, notes) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setNullableInt(ps, 1, o.getCustomerId());
            setNullableInt(ps, 2, o.getStaffId());
            ps.setBigDecimal(3, o.getTotalAmount());
            ps.setString(4, o.getPaymentMethod());
            ps.setString(5, o.getPaymentStatus());
            ps.setString(6, o.getOrderStatus());
            ps.setString(7, o.getNotes());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Không lấy được order_id vừa tạo");
                }
                return keys.getInt(1);
            }
        }
    }

    private void insertDetail(Connection conn, int orderId, OrderDetailDTO d) throws SQLException {
        String sql = "INSERT INTO OrderDetails(order_id, spec_id, quantity, unit_price, discount_percent, subtotal) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, d.getSpecId());
            ps.setInt(3, d.getQuantity());
            ps.setBigDecimal(4, d.getUnitPrice());
            ps.setBigDecimal(5, d.getDiscountPercent() == null ? BigDecimal.ZERO : d.getDiscountPercent());
            ps.setBigDecimal(6, d.getSubtotal());
            ps.executeUpdate();
        }
    }

    /**
     * Trừ kho bằng một câu UPDATE có điều kiện "còn đủ hàng". Nếu hai người mua món cuối cùng
     * cùng lúc thì chỉ một người trừ được, người kia nhận 0 dòng được cập nhật => hết hàng.
     */
    private void decreaseStock(Connection conn, int specId, int quantity)
            throws SQLException, OutOfStockException {
        String sql = "UPDATE ProductSpecs SET stock_quantity = stock_quantity - ? "
                + "WHERE spec_id = ? AND stock_quantity >= ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setInt(2, specId);
            ps.setInt(3, quantity);
            if (ps.executeUpdate() == 0) {
                throw new OutOfStockException(specId);
            }
        }
    }

    /**
     * Khóa dòng ProductSpecs tương ứng để tránh deadlock khi nhiều transaction cùng insert OrderDetails
     * (FK check giữ shared lock) rồi đồng thời nâng cấp sang update stock.
     */
    private void lockSpecRowForUpdate(Connection conn, int specId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT spec_id FROM ProductSpecs WHERE spec_id = ? FOR UPDATE")) {
            ps.setInt(1, specId);
            try (ResultSet rs = ps.executeQuery()) {
                // Nếu không có dòng thì để insertDetail ném lỗi FK như hành vi hiện tại.
                if (rs.next()) {
                    // no-op
                }
            }
        }
    }

    private void insertInventory(Connection conn, int specId, int quantityIn, int quantityOut,
                                 String type, String notes) throws SQLException {
        String sql = "INSERT INTO Inventory(spec_id, quantity_in, quantity_out, transaction_type, notes) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, specId);
            ps.setInt(2, quantityIn);
            ps.setInt(3, quantityOut);
            ps.setString(4, type);
            ps.setString(5, notes);
            ps.executeUpdate();
        }
    }

    /** Cộng (amount dương) hoặc trừ (amount âm) tổng chi tiêu của khách; không để xuống dưới 0. */
    private void changeTotalSpent(Connection conn, int customerId, BigDecimal amount) throws SQLException {
        String sql = "UPDATE Customers SET total_spent = GREATEST(total_spent + ?, 0) WHERE customer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, amount);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- helpers

    private OrderListItemDTO mapListItem(ResultSet rs) throws SQLException {
        OrderListItemDTO o = new OrderListItemDTO();
        o.setOrderId(rs.getInt("order_id"));
        o.setOrderDate(rs.getTimestamp("order_date"));
        int customerId = rs.getInt("customer_id");
        o.setCustomerId(rs.wasNull() ? null : customerId);
        o.setCustomerName(rs.getString("customer_name"));
        o.setCustomerPhone(rs.getString("customer_phone"));
        int staffId = rs.getInt("staff_id");
        o.setStaffId(rs.wasNull() ? null : staffId);
        o.setStaffName(rs.getString("staff_name"));
        o.setTotalAmount(rs.getBigDecimal("total_amount"));
        o.setPaymentMethod(rs.getString("payment_method"));
        o.setPaymentStatus(rs.getString("payment_status"));
        o.setOrderStatus(rs.getString("order_status"));
        o.setDeliveryDate(rs.getTimestamp("delivery_date"));
        o.setNotes(rs.getString("notes"));
        return o;
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    /** Thoát các ký tự đặc biệt của LIKE để từ khóa chứa % hoặc _ vẫn tìm đúng. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Rollback; nếu rollback cũng lỗi thì gắn vào lỗi gốc, không làm mất lỗi gốc. */
    private static void rollbackQuietly(Connection conn, Exception original) {
        try {
            conn.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }
}

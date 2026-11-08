package com.phonestore.dao;

import com.phonestore.config.DBConnection;
import com.phonestore.dto.OrderDTO;
import com.phonestore.dto.OrderDetailDTO;
import com.phonestore.exception.OutOfStockException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class OrderDAO {

    public static final String TRANSACTION_TYPE_OUT = "Xuất kho";

    public int createOrder(OrderDTO order, List<OrderDetailDTO> details)
            throws SQLException, OutOfStockException {

        List<OrderDetailDTO> sorted = new ArrayList<>(details);
        sorted.sort(Comparator.comparingInt(OrderDetailDTO::getSpecId));

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int orderId = insertOrder(conn, order);

                for (OrderDetailDTO d : sorted) {
                    insertDetail(conn, orderId, d);
                    decreaseStock(conn, d.getSpecId(), d.getQuantity());
                    insertInventoryOut(conn, d.getSpecId(), d.getQuantity(), orderId);
                }

                if (order.getCustomerId() != null) {
                    addTotalSpent(conn, order.getCustomerId(), order.getTotalAmount());
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


    private int insertOrder(Connection conn, OrderDTO o) throws SQLException {
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

    private void insertInventoryOut(Connection conn, int specId, int quantity, int orderId) throws SQLException {
        String sql = "INSERT INTO Inventory(spec_id, quantity_in, quantity_out, transaction_type, notes) "
                + "VALUES (?, 0, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, specId);
            ps.setInt(2, quantity);
            ps.setString(3, TRANSACTION_TYPE_OUT);
            ps.setString(4, "Bán hàng - đơn #" + orderId);
            ps.executeUpdate();
        }
    }

    private void addTotalSpent(Connection conn, int customerId, BigDecimal amount) throws SQLException {
        String sql = "UPDATE Customers SET total_spent = total_spent + ? WHERE customer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, amount);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }


    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void rollbackQuietly(Connection conn, Exception original) {
        try {
            conn.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }
}
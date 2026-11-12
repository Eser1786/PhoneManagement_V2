package com.phonestore.dao;

import com.phonestore.config.DBConnection;
import com.phonestore.dto.DashboardSummaryDTO;
import com.phonestore.dto.RevenuePointDTO;
import com.phonestore.dto.TopProductDTO;
import com.phonestore.util.OrderConstants;
import com.phonestore.util.ProductStatuses;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Các truy vấn thống kê (chỉ đọc). Mọi con số doanh thu đều bỏ qua đơn "Đã hủy".
 * Giá vốn = số lượng x ProductSpecs.cost_price HIỆN TẠI (OrderDetails không lưu giá vốn lúc bán),
 * nên nếu sau này sửa giá vốn thì lợi nhuận của các đơn cũ cũng thay đổi theo.
 *
 * Kết quả revenueByDay / revenueByMonth chỉ chứa các kỳ CÓ đơn; ReportBUS điền 0 cho kỳ còn lại.
 */
public class ReportDAO {

    private static final String REVENUE_FROM_WHERE =
            "FROM Orders o "
                    + "JOIN OrderDetails od ON od.order_id = o.order_id "
                    + "JOIN ProductSpecs s ON s.spec_id = od.spec_id "
                    + "WHERE o.order_status <> ? AND o.order_date >= ? AND o.order_date < ? ";

    /** Doanh thu, giá vốn theo từng ngày trong khoảng [from, to] (tính cả hai đầu). Period dạng yyyy-MM-dd. */
    public List<RevenuePointDTO> revenueByDay(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT DATE(o.order_date) AS period, COUNT(DISTINCT o.order_id) AS order_count, "
                + "SUM(od.subtotal) AS revenue, SUM(od.quantity * s.cost_price) AS cost "
                + REVENUE_FROM_WHERE + "GROUP BY DATE(o.order_date) ORDER BY period";
        return queryRevenue(sql, start(from), start(to.plusDays(1)));
    }

    /** Doanh thu, giá vốn theo từng tháng của một năm. Period dạng yyyy-MM. */
    public List<RevenuePointDTO> revenueByMonth(int year) throws SQLException {
        String sql = "SELECT DATE_FORMAT(o.order_date, '%Y-%m') AS period, COUNT(DISTINCT o.order_id) AS order_count, "
                + "SUM(od.subtotal) AS revenue, SUM(od.quantity * s.cost_price) AS cost "
                + REVENUE_FROM_WHERE + "GROUP BY DATE_FORMAT(o.order_date, '%Y-%m') ORDER BY period";
        return queryRevenue(sql, start(LocalDate.of(year, 1, 1)), start(LocalDate.of(year + 1, 1, 1)));
    }

    /** Sản phẩm bán chạy nhất trong khoảng ngày, xếp theo số lượng bán (bằng nhau thì theo doanh thu). */
    public List<TopProductDTO> topProducts(LocalDate from, LocalDate to, int limit) throws SQLException {
        String sql = "SELECT s.spec_id, p.name AS product_name, s.storage, s.color, "
                + "SUM(od.quantity) AS quantity_sold, SUM(od.subtotal) AS revenue "
                + "FROM Orders o "
                + "JOIN OrderDetails od ON od.order_id = o.order_id "
                + "JOIN ProductSpecs s ON s.spec_id = od.spec_id "
                + "JOIN Products p ON p.product_id = s.product_id "
                + "WHERE o.order_status <> ? AND o.order_date >= ? AND o.order_date < ? "
                + "GROUP BY s.spec_id, p.name, s.storage, s.color "
                + "ORDER BY quantity_sold DESC, revenue DESC, s.spec_id LIMIT ?";
        List<TopProductDTO> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, OrderConstants.ORDER_STATUS_CANCELLED);
            ps.setTimestamp(2, start(from));
            ps.setTimestamp(3, start(to.plusDays(1)));
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TopProductDTO t = new TopProductDTO();
                    t.setSpecId(rs.getInt("spec_id"));
                    t.setProductName(rs.getString("product_name"));
                    t.setStorage(rs.getString("storage"));
                    t.setColor(rs.getString("color"));
                    t.setQuantitySold(rs.getInt("quantity_sold"));
                    t.setRevenue(rs.getBigDecimal("revenue"));
                    result.add(t);
                }
            }
        }
        return result;
    }

    /** Các con số cho Dashboard tại ngày "today". */
    public DashboardSummaryDTO dashboard(LocalDate today, int lowStockThreshold) throws SQLException {
        DashboardSummaryDTO d = new DashboardSummaryDTO();
        try (Connection conn = DBConnection.getConnection()) {
            String revenueSql = "SELECT COUNT(*), COALESCE(SUM(total_amount), 0) FROM Orders "
                    + "WHERE order_status <> ? AND order_date >= ? AND order_date < ?";

            try (PreparedStatement ps = conn.prepareStatement(revenueSql)) {
                bindRange(ps, start(today), start(today.plusDays(1)));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    d.setOrdersToday(rs.getInt(1));
                    d.setRevenueToday(rs.getBigDecimal(2));
                }
            }
            LocalDate firstOfMonth = today.withDayOfMonth(1);
            try (PreparedStatement ps = conn.prepareStatement(revenueSql)) {
                bindRange(ps, start(firstOfMonth), start(firstOfMonth.plusMonths(1)));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    d.setRevenueThisMonth(rs.getBigDecimal(2));
                }
            }

            d.setPendingOrders(count(conn, "SELECT COUNT(*) FROM Orders WHERE order_status = ?",
                    OrderConstants.ORDER_STATUS_PENDING));
            d.setTotalCustomers(count(conn, "SELECT COUNT(*) FROM Customers"));
            d.setProductsOnSale(count(conn, "SELECT COUNT(*) FROM ProductSpecs WHERE status = ?",
                    ProductStatuses.ON_SALE));
            d.setLowStockCount(count(conn,
                    "SELECT COUNT(*) FROM ProductSpecs WHERE stock_quantity <= ? AND status <> ?",
                    lowStockThreshold, ProductStatuses.DISCONTINUED));
        }
        return d;
    }

    // ---------------------------------------------------------------- helpers

    private List<RevenuePointDTO> queryRevenue(String sql, Timestamp from, Timestamp toExclusive) throws SQLException {
        List<RevenuePointDTO> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindRange(ps, from, toExclusive);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal revenue = rs.getBigDecimal("revenue");
                    BigDecimal cost = rs.getBigDecimal("cost");
                    result.add(new RevenuePointDTO(rs.getString("period"), rs.getInt("order_count"),
                            revenue == null ? BigDecimal.ZERO : revenue, cost == null ? BigDecimal.ZERO : cost));
                }
            }
        }
        return result;
    }

    private static void bindRange(PreparedStatement ps, Timestamp from, Timestamp toExclusive) throws SQLException {
        ps.setString(1, OrderConstants.ORDER_STATUS_CANCELLED);
        ps.setTimestamp(2, from);
        ps.setTimestamp(3, toExclusive);
    }

    private static int count(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static Timestamp start(LocalDate date) {
        return Timestamp.valueOf(date.atStartOfDay());
    }
}

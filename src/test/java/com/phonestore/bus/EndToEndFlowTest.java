package com.phonestore.bus;

import com.phonestore.config.DBConnection;
import com.phonestore.dao.InventoryDAO;
import com.phonestore.dao.LookupDAO;
import com.phonestore.dao.OrderDAO;
import com.phonestore.dao.ProductDAO;
import com.phonestore.dao.ProductSpecDAO;
import com.phonestore.dao.ReportDAO;
import com.phonestore.dto.DashboardSummaryDTO;
import com.phonestore.dto.OrderDTO;
import com.phonestore.dto.OrderDetailDTO;
import com.phonestore.dto.ProductFilter;
import com.phonestore.dto.ProductSpecDTO;
import com.phonestore.dto.RevenuePointDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Kiểm thử End-to-End (Task STT 33):
 * Đăng nhập (theo từng vai trò) -> Bán hàng -> Trừ kho -> Nhập kho -> Báo cáo.
 */
class EndToEndFlowTest {

    private static final int TEST_CUSTOMER_ID = 1;
    private static final int TEST_STAFF_ID = 3; // staff1
    private static final int TEST_SPEC_ID = 1;

    private final AuthBUS authBUS = new AuthBUS();
    private final ProductBUS productBUS = new ProductBUS(
            new ProductDAO(), new ProductSpecDAO(), new LookupDAO(), authBUS);
    private final OrderBUS orderBUS = new OrderBUS(
            new OrderDAO(), new ProductSpecDAO(), new ProductDAO(), authBUS);
    private final InventoryBUS inventoryBUS = new InventoryBUS(
            new InventoryDAO(), authBUS);
    private final ReportBUS reportBUS = new ReportBUS(
            new ReportDAO(), new ProductDAO(), authBUS);
    private final UserBUS userBUS = new UserBUS();

    private String marker;
    private int originalStock;
    private BigDecimal originalSpent;

    @BeforeAll
    static void requireDatabase() {
        boolean ok;
        try (Connection c = DBConnection.getConnection()) {
            ok = c.isValid(2);
        } catch (Exception e) {
            ok = false;
        }
        assumeTrue(ok, "Không kết nối được MySQL Docker, bỏ qua EndToEndFlowTest");
    }

    @BeforeEach
    void setUp() throws Exception {
        marker = "E2E-" + UUID.randomUUID();
        originalStock = queryInt("SELECT stock_quantity FROM ProductSpecs WHERE spec_id = ?", TEST_SPEC_ID);
        originalSpent = queryDecimal("SELECT total_spent FROM Customers WHERE customer_id = ?", TEST_CUSTOMER_ID);
    }

    @AfterEach
    void tearDown() throws Exception {
        SessionManager.logout();
        try (Connection c = DBConnection.getConnection()) {
            // Xóa đơn hàng tạo bởi test
            exec(c, "DELETE od FROM OrderDetails od JOIN Orders o ON o.order_id = od.order_id WHERE o.notes = ?",
                    marker);
            exec(c, "DELETE FROM Orders WHERE notes = ?", marker);
            // Xóa lịch sử kho tạo bởi test
            exec(c, "DELETE FROM Inventory WHERE notes = ?", marker);
            // Khôi phục tồn kho và total_spent ban đầu
            exec(c, "UPDATE ProductSpecs SET stock_quantity = ? WHERE spec_id = ?", originalStock, TEST_SPEC_ID);
            exec(c, "UPDATE Customers SET total_spent = ? WHERE customer_id = ?", originalSpent, TEST_CUSTOMER_ID);
        }
    }

    @Test
    void customerRole_CanOnlyViewProducts_BlockedFromSalesInventoryReports() {
        authBUS.login("customer1", "123456");

        // Được xem sản phẩm
        assertFalse(productBUS.searchProducts(new ProductFilter()).isEmpty());

        // Bị chặn tạo đơn, nhập kho, xem báo cáo, quản lý user
        assertThrows(BusinessException.class, () -> orderBUS.createOrder(
                TEST_CUSTOMER_ID, TEST_STAFF_ID, OrderBUS.PAYMENT_CASH, marker, List.of(sampleItem(1))));
        assertThrows(BusinessException.class, () -> inventoryBUS.importStock(TEST_SPEC_ID, 1, marker));
        assertThrows(BusinessException.class, () -> reportBUS.getDashboardSummary());
        assertThrows(BusinessException.class, () -> userBUS.getAllUsers());
    }

    @Test
    void staffRole_CanCreateOrderAndDeductStock_BlockedFromInventoryAndReports() throws Exception {
        authBUS.login("staff1", "123456");

        // Tạo đơn hàng với số lượng = 1
        OrderDTO order = orderBUS.createOrder(
                TEST_CUSTOMER_ID, TEST_STAFF_ID, OrderBUS.PAYMENT_CASH, marker, List.of(sampleItem(1)));
        assertTrue(order.getOrderId() > 0);

        // Kiểm tra tồn kho đã bị trừ 1
        int stockAfter = queryInt("SELECT stock_quantity FROM ProductSpecs WHERE spec_id = ?", TEST_SPEC_ID);
        assertEquals(originalStock - 1, stockAfter);

        // Kiểm tra total_spent của khách hàng đã tăng
        BigDecimal spentAfter = queryDecimal("SELECT total_spent FROM Customers WHERE customer_id = ?",
                TEST_CUSTOMER_ID);
        assertTrue(spentAfter.compareTo(originalSpent) > 0);

        // Staff bị chặn ở Kho, Báo cáo và Quản lý User
        assertThrows(BusinessException.class, () -> inventoryBUS.importStock(TEST_SPEC_ID, 2, marker));
        assertThrows(BusinessException.class, () -> reportBUS.getDashboardSummary());
        assertThrows(BusinessException.class, () -> userBUS.getAllUsers());
    }

    @Test
    void managerRole_FullFlow_ImportStock_Sell_VerifyReport_BlockedFromManageUsers() throws Exception {
        authBUS.login("manager", "123456");

        // 1. Nhập kho +5
        inventoryBUS.importStock(TEST_SPEC_ID, 5, marker);
        int stockAfterImport = queryInt("SELECT stock_quantity FROM ProductSpecs WHERE spec_id = ?", TEST_SPEC_ID);
        assertEquals(originalStock + 5, stockAfterImport);

        // 2. Bán hàng -2 -> tồn kho còn originalStock + 3
        OrderDTO order = orderBUS.createOrder(
                TEST_CUSTOMER_ID, TEST_STAFF_ID, OrderBUS.PAYMENT_TRANSFER, marker, List.of(sampleItem(2)));
        assertTrue(order.getOrderId() > 0);

        int stockAfterSale = queryInt("SELECT stock_quantity FROM ProductSpecs WHERE spec_id = ?", TEST_SPEC_ID);
        assertEquals(originalStock + 3, stockAfterSale);

        // 3. Xem báo cáo (doanh thu hôm nay & tổng quan dashboard)
        LocalDate today = LocalDate.now();
        List<RevenuePointDTO> rev = reportBUS.getRevenueByDay(today, today);
        assertNotNull(rev);
        DashboardSummaryDTO summary = reportBUS.getDashboardSummary();
        assertNotNull(summary);
        assertTrue(summary.getOrdersToday() >= 1);

        // 4. Manager vẫn bị chặn quản lý User
        assertThrows(BusinessException.class, () -> userBUS.getAllUsers());
    }

    @Test
    void adminRole_HasFullAccessAcrossAllModules() {
        authBUS.login("admin", "123456");

        assertFalse(userBUS.getAllUsers().isEmpty());
        assertFalse(productBUS.searchProducts(new ProductFilter()).isEmpty());
        assertNotNull(reportBUS.getDashboardSummary());
        assertDoesNotThrow(() -> inventoryBUS.getLowStockSpecs());
    }

    // ---------------- Helpers ----------------

    private OrderDetailDTO sampleItem(int qty) throws Exception {
        ProductSpecDTO spec = new ProductSpecDAO().findById(TEST_SPEC_ID);
        assertNotNull(spec, "Cần có ProductSpec id=1 trong DB mẫu");
        OrderDetailDTO item = new OrderDetailDTO();
        item.setSpecId(TEST_SPEC_ID);
        item.setQuantity(qty);
        item.setUnitPrice(spec.getPrice());
        item.setDiscountPercent(BigDecimal.ZERO);
        return item;
    }

    private int queryInt(String sql, Object param) throws Exception {
        try (Connection c = DBConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private BigDecimal queryDecimal(String sql, Object param) throws Exception {
        try (Connection c = DBConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getBigDecimal(1);
            }
        }
    }

    private void exec(Connection c, String sql, Object... params) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }
}
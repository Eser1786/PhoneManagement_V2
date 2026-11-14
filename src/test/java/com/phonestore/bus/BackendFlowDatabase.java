package com.phonestore.bus;

import com.phonestore.config.DBConnection;
import com.phonestore.dto.DashboardSummaryDTO;
import com.phonestore.dto.InvoiceDTO;
import com.phonestore.dto.OrderDetailDTO;
import com.phonestore.dto.OrderDTO;
import com.phonestore.dto.OrderFilter;
import com.phonestore.dto.OrderListItemDTO;
import com.phonestore.dto.ProductDTO;
import com.phonestore.dto.ProductFilter;
import com.phonestore.dto.ProductListItemDTO;
import com.phonestore.dto.ProductSpecDTO;
import com.phonestore.dto.RevenuePointDTO;
import com.phonestore.dto.TopProductDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.InvoiceFormatter;
import com.phonestore.util.ProductStatuses;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Chạy ProductBUS, OrderBUS, ReportBUS trên database thật (cần docker + 02_insert.sql; bỏ qua nếu không kết nối được).
 * Dùng AuthBUS "cho phép tất cả" để không phụ thuộc đăng nhập. Mỗi test tự dọn dữ liệu nó tạo ra.
 */
class BackendFlowDatabaseTest {

    private static final int CUSTOMER_ID = 1;
    private static final int BRAND = 1;
    private static final int CATEGORY = 1;

    private final AuthBUS allowAll = new AuthBUS() {
        @Override public boolean hasPermission(String p) { return true; }
        @Override public void requirePermission(String p) { }
    };
    private final ProductBUS productBUS = new ProductBUS(new com.phonestore.dao.ProductDAO(),
            new com.phonestore.dao.ProductSpecDAO(), new com.phonestore.dao.LookupDAO(), allowAll);
    private final OrderBUS orderBUS = new OrderBUS(new com.phonestore.dao.OrderDAO(),
            new com.phonestore.dao.ProductSpecDAO(), new com.phonestore.dao.ProductDAO(), allowAll);
    private final ReportBUS reportBUS = new ReportBUS(new com.phonestore.dao.ReportDAO(),
            new com.phonestore.dao.ProductDAO(), allowAll);

    private String marker;
    private BigDecimal originalSpent;
    private final List<Integer> productIds = new ArrayList<>();

    @BeforeAll
    static void requireDatabase() {
        boolean ok;
        try (Connection c = DBConnection.getConnection()) {
            ok = c.isValid(2);
        } catch (Exception e) {
            ok = false;
        }
        assumeTrue(ok, "Không kết nối được database, bỏ qua test tích hợp");
    }

    @BeforeEach
    void setUp() throws Exception {
        marker = "TEST-" + UUID.randomUUID();
        originalSpent = scalarDecimal("SELECT total_spent FROM Customers WHERE customer_id = ?", CUSTOMER_ID);
    }

    @AfterEach
    void cleanUp() throws Exception {
        try (Connection c = DBConnection.getConnection()) {
            exec(c, "DELETE od FROM OrderDetails od JOIN Orders o ON o.order_id = od.order_id WHERE o.notes = ?", marker);
            exec(c, "DELETE FROM Orders WHERE notes = ?", marker);
            for (int id : productIds) {
                exec(c, "DELETE i FROM Inventory i JOIN ProductSpecs s ON s.spec_id = i.spec_id WHERE s.product_id = ?", id);
                exec(c, "DELETE FROM Products WHERE product_id = ?", id);
            }
            exec(c, "UPDATE Customers SET total_spent = ? WHERE customer_id = ?", originalSpent, CUSTOMER_ID);
        }
    }

    // ------------------------------------------------------------ ProductBUS

    @Test
    void productLifecycle() throws Exception {
        int productId = createProduct(10, 3);
        assertEquals(2, productBUS.getSpecs(productId).size());
        assertEquals(2, countSql("SELECT COUNT(*) FROM Inventory i JOIN ProductSpecs s ON s.spec_id = i.spec_id "
                + "WHERE s.product_id = ? AND i.transaction_type = 'Nhập kho' AND i.notes = 'Tồn kho ban đầu'", productId));

        ProductFilter f = new ProductFilter();
        f.setKeyword(marker);
        List<ProductListItemDTO> found = productBUS.searchProducts(f);
        assertEquals(2, found.size());
        assertNotNull(found.get(0).getBrandName());

        // sửa phiên bản không đổi tồn kho
        ProductSpecDTO spec = productBUS.getSpecs(productId).get(0);
        int stockBefore = spec.getStockQuantity();
        spec.setPrice(new BigDecimal("12345000"));
        spec.setStockQuantity(9999);
        productBUS.updateSpec(spec);
        ProductSpecDTO after = productBUS.getSpec(spec.getSpecId());
        assertEquals(0, new BigDecimal("12345000").compareTo(after.getPrice()));
        assertEquals(stockBefore, after.getStockQuantity());

        productBUS.changeSpecStatus(spec.getSpecId(), ProductStatuses.DISCONTINUED);
        assertEquals(ProductStatuses.DISCONTINUED, productBUS.getSpec(spec.getSpecId()).getStatus());

        // chưa có lịch sử -> xóa hẳn
        assertTrue(productBUS.deleteProduct(productId));
        assertEquals(0, countSql("SELECT COUNT(*) FROM Products WHERE product_id = ?", productId));
        assertEquals(0, countSql("SELECT COUNT(*) FROM ProductSpecs WHERE product_id = ?", productId));
    }

    @Test
    void productValidation() throws Exception {
        ProductDTO p = product("");
        assertEquals("Tên sản phẩm không được để trống",
                assertThrows(BusinessException.class, () -> productBUS.createProduct(p, List.of(spec(1, "1000")))).getMessage());
        assertEquals("Sản phẩm cần có ít nhất một phiên bản (cấu hình, giá)",
                assertThrows(BusinessException.class, () -> productBUS.createProduct(product(marker), List.of())).getMessage());
        assertEquals("Giá bán phải lớn hơn 0",
                assertThrows(BusinessException.class,
                        () -> productBUS.createProduct(product(marker), List.of(spec(1, "0")))).getMessage());
        ProductDTO noBrand = product(marker);
        noBrand.setBrandId(0);
        assertEquals("Vui lòng chọn hãng",
                assertThrows(BusinessException.class,
                        () -> productBUS.createProduct(noBrand, List.of(spec(1, "1000")))).getMessage());
        assertEquals(0, countSql("SELECT COUNT(*) FROM Products WHERE name = ?", marker));
    }

    @Test
    void failedCreateRollsBackTheProduct() throws Exception {
        ProductSpecDTO bad = spec(1, "1000");
        bad.setColor("x".repeat(60));   // BUS chặn trước khi vào DB
        assertThrows(BusinessException.class, () -> productBUS.createProduct(product(marker), List.of(bad)));
        assertEquals(0, countSql("SELECT COUNT(*) FROM Products WHERE name = ?", marker));
    }

    @Test
    void brandAndCategoryOptions() {
        assertFalse(productBUS.getBrandOptions().isEmpty());
        assertFalse(productBUS.getCategoryOptions().isEmpty());
    }

    // ------------------------------------------------------------ OrderBUS

    @Test
    void orderQueryStatusAndInvoice() throws Exception {
        int productId = createProduct(10, 5);
        ProductSpecDTO spec = productBUS.getSpecs(productId).get(0);

        OrderDTO order = orderBUS.createOrder(CUSTOMER_ID, null, OrderBUS.PAYMENT_CASH, marker,
                List.of(line(spec.getSpecId(), 2, "10")));
        int orderId = order.getOrderId();

        List<OrderListItemDTO> mine = orderBUS.getOrdersByCustomer(CUSTOMER_ID);
        assertTrue(mine.stream().anyMatch(o -> o.getOrderId() == orderId));

        OrderFilter filter = new OrderFilter();
        filter.setKeyword(String.valueOf(orderId));   // tìm theo mã đơn / tên / số điện thoại khách
        assertTrue(orderBUS.searchOrders(filter).stream().anyMatch(o -> o.getOrderId() == orderId));
        OrderFilter byStatus = new OrderFilter();
        byStatus.setCustomerId(CUSTOMER_ID);
        byStatus.setOrderStatus("Chờ xử lý");
        assertTrue(orderBUS.searchOrders(byStatus).stream().anyMatch(o -> o.getOrderId() == orderId));

        InvoiceDTO invoice = orderBUS.getInvoice(orderId);
        assertEquals(1, invoice.getItems().size());
        assertEquals(0, invoice.getTotalAmount().compareTo(order.getTotalAmount()));
        String text = InvoiceFormatter.toText(invoice);
        assertTrue(text.contains("#" + orderId) || text.contains(String.valueOf(orderId)));

        // chuyển trạng thái hợp lệ / không hợp lệ
        orderBUS.updateOrderStatus(orderId, "Đang giao");
        assertThrows(BusinessException.class, () -> orderBUS.updateOrderStatus(orderId, "Chờ xử lý"));
        orderBUS.updateOrderStatus(orderId, "Hoàn thành");
        assertThrows(BusinessException.class, () -> orderBUS.updateOrderStatus(orderId, "Đang giao"));
        assertEquals("Hoàn thành", orderBUS.getOrder(orderId).getOrderStatus());
        assertNotNull(orderBUS.getOrder(orderId).getDeliveryDate());
        orderBUS.markAsPaid(orderId);
        assertEquals("Đã thanh toán", orderBUS.getOrder(orderId).getPaymentStatus());
        assertThrows(BusinessException.class, () -> orderBUS.markAsPaid(orderId));
        assertThrows(BusinessException.class, () -> orderBUS.cancelOrder(orderId));   // đã hoàn thành

        // đơn đã bán -> xóa sản phẩm chỉ là xóa mềm
        assertFalse(productBUS.deleteProduct(productId));
        assertEquals(ProductStatuses.DISCONTINUED, productBUS.getSpec(spec.getSpecId()).getStatus());
    }

    @Test
    void cancelRestoresStockAndSpending() throws Exception {
        int productId = createProduct(10, 5);
        ProductSpecDTO spec = productBUS.getSpecs(productId).get(0);

        OrderDTO order = orderBUS.createOrder(CUSTOMER_ID, null, OrderBUS.PAYMENT_CASH, marker,
                List.of(line(spec.getSpecId(), 3, "0")));
        assertEquals(spec.getStockQuantity() - 3, productBUS.getSpec(spec.getSpecId()).getStockQuantity());
        assertEquals(1, originalSpent.add(order.getTotalAmount()).compareTo(originalSpent));

        orderBUS.markAsPaid(order.getOrderId());
        orderBUS.cancelOrder(order.getOrderId());

        assertEquals(spec.getStockQuantity(), productBUS.getSpec(spec.getSpecId()).getStockQuantity());
        assertEquals(0, originalSpent.compareTo(
                scalarDecimal("SELECT total_spent FROM Customers WHERE customer_id = ?", CUSTOMER_ID)));
        OrderListItemDTO after = orderBUS.getOrder(order.getOrderId());
        assertEquals("Đã hủy", after.getOrderStatus());
        assertEquals("Đã hoàn tiền", after.getPaymentStatus());
        assertEquals(1, countSql("SELECT COUNT(*) FROM Inventory WHERE spec_id = ? AND transaction_type = 'Nhập kho' "
                + "AND notes = ?", spec.getSpecId(), "Hủy đơn #" + order.getOrderId()));
        assertThrows(BusinessException.class, () -> orderBUS.cancelOrder(order.getOrderId()));
    }

    // ------------------------------------------------------------ ReportBUS

    @Test
    void revenueReportsExcludeCancelledOrdersAndFillGaps() throws Exception {
        int productId = createProduct(20, 5);
        ProductSpecDTO spec = productBUS.getSpecs(productId).get(0);
        int specId = spec.getSpecId();

        OrderDTO sold = orderBUS.createOrder(CUSTOMER_ID, null, OrderBUS.PAYMENT_CASH, marker,
                List.of(line(specId, 2, "0")));
        OrderDTO cancelled = orderBUS.createOrder(CUSTOMER_ID, null, OrderBUS.PAYMENT_CASH, marker,
                List.of(line(specId, 1, "0")));
        orderBUS.cancelOrder(cancelled.getOrderId());
        moveOrderTo(sold.getOrderId(), "2001-03-05 10:00:00");
        moveOrderTo(cancelled.getOrderId(), "2001-03-05 11:00:00");

        List<RevenuePointDTO> days = reportBUS.getRevenueByDay(LocalDate.of(2001, 3, 1), LocalDate.of(2001, 3, 10));
        assertEquals(10, days.size());
        RevenuePointDTO day = days.get(4);
        assertEquals("2001-03-05", day.getPeriod());
        assertEquals(1, day.getOrderCount());
        assertEquals(0, sold.getTotalAmount().compareTo(day.getRevenue()));
        assertEquals(0, new BigDecimal("2").multiply(spec.getCostPrice()).compareTo(day.getCost()));
        assertEquals(0, day.getRevenue().subtract(day.getCost()).compareTo(day.getProfit()));
        assertEquals(0, days.get(0).getRevenue().signum());

        List<RevenuePointDTO> months = reportBUS.getRevenueByMonth(2001);
        assertEquals(12, months.size());
        assertEquals("2001-03", months.get(2).getPeriod());
        assertEquals(0, sold.getTotalAmount().compareTo(months.get(2).getRevenue()));
        assertEquals(0, months.get(3).getRevenue().signum());

        List<TopProductDTO> top = reportBUS.getTopProducts(LocalDate.of(2001, 1, 1), LocalDate.of(2001, 12, 31), 5);
        assertEquals(1, top.size());
        assertEquals(specId, top.get(0).getSpecId());
        assertEquals(2, top.get(0).getQuantitySold());

        assertThrows(BusinessException.class, () -> reportBUS.getRevenueByDay(LocalDate.of(2001, 3, 10), LocalDate.of(2001, 3, 1)));
        assertThrows(BusinessException.class, () -> reportBUS.getRevenueByDay(LocalDate.of(2000, 1, 1), LocalDate.of(2001, 12, 31)));
        assertThrows(BusinessException.class, () -> reportBUS.getTopProducts(LocalDate.of(2001, 1, 1), LocalDate.of(2001, 2, 1), 0));
    }

    @Test
    void lowStockAndDashboard() throws Exception {
        int productId = createProduct(2, 50);
        List<ProductListItemDTO> low = reportBUS.getLowStock(5);
        assertTrue(low.stream().anyMatch(i -> i.getProductId() == productId && i.getStockQuantity() == 2));
        assertTrue(low.stream().noneMatch(i -> i.getProductId() == productId && i.getStockQuantity() == 50));
        for (int i = 1; i < low.size(); i++) {
            assertTrue(low.get(i - 1).getStockQuantity() <= low.get(i).getStockQuantity());
        }
        assertThrows(BusinessException.class, () -> reportBUS.getLowStock(-1));

        DashboardSummaryDTO d = reportBUS.getDashboardSummary();
        assertTrue(d.getTotalCustomers() >= 5);
        assertTrue(d.getLowStockCount() >= 1);
        assertTrue(d.getProductsOnSale() >= 2);
        assertNotNull(d.getRevenueToday());
    }

    // ------------------------------------------------------------ helpers

    private int createProduct(int stockA, int stockB) {
        int id = productBUS.createProduct(product(marker), List.of(spec(stockA, "20000000"), spec(stockB, "30000000")));
        productIds.add(id);
        return id;
    }

    private ProductDTO product(String name) {
        ProductDTO p = new ProductDTO();
        p.setName(name);
        p.setBrandId(BRAND);
        p.setCategoryId(CATEGORY);
        p.setDescription("test");
        return p;
    }

    private ProductSpecDTO spec(int stock, String price) {
        ProductSpecDTO s = new ProductSpecDTO();
        s.setPrice(new BigDecimal(price));
        s.setCostPrice(new BigDecimal("15000000"));
        s.setStockQuantity(stock);
        s.setScreenSize("6.1");
        s.setProcessor("Chip");
        s.setResolution("1080p");
        s.setRam("8GB");
        s.setStorage("128GB" + stock);
        s.setBattery("4000mAh");
        s.setCameraMain("48MP");
        s.setCameraFront("12MP");
        s.setWeight("170g");
        s.setOs("Android");
        s.setColor("Đen");
        s.setWarrantyPeriod("12 tháng");
        s.setStatus(ProductStatuses.ON_SALE);
        return s;
    }

    private OrderDetailDTO line(int specId, int qty, String discount) {
        OrderDetailDTO d = new OrderDetailDTO();
        d.setSpecId(specId);
        d.setQuantity(qty);
        d.setDiscountPercent(new BigDecimal(discount));
        return d;
    }

    private void moveOrderTo(int orderId, String ts) throws Exception {
        try (Connection c = DBConnection.getConnection()) {
            exec(c, "UPDATE Orders SET order_date = ? WHERE order_id = ?", ts, orderId);
        }
    }

    private static void exec(Connection c, String sql, Object... params) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }

    private static int countSql(String sql, Object... params) throws Exception {
        return scalarDecimal(sql, params).intValue();
    }

    private static BigDecimal scalarDecimal(String sql, Object... params) throws Exception {
        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }
}

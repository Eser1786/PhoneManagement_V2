package com.phonestore.dao;

import com.phonestore.bus.AuthBUS;
import com.phonestore.bus.OrderBUS;
import com.phonestore.config.DBConnection;
import com.phonestore.dto.OrderDTO;
import com.phonestore.dto.OrderDetailDTO;
import com.phonestore.dto.ProductDTO;
import com.phonestore.dto.ProductSpecDTO;
import com.phonestore.dto.UserDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.exception.OutOfStockException;
import com.phonestore.util.PermissionNames;
import com.phonestore.util.SessionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test transaction tạo đơn hàng trên database thật (cần 'docker compose up -d' và dữ liệu
 * mẫu 02_insert.sql; bỏ qua nếu không kết nối được). Cần cột tiền là DECIMAL(15,2) như
 * 01_schema.sql đã sửa.
 *
 * Mỗi test tự tạo sản phẩm riêng và tự dọn (xóa đơn, dòng kho, sản phẩm, khôi phục total_spent
 * của khách mẫu), nên không làm thay đổi dữ liệu mẫu.
 */
class OrderDAODatabaseTest {

    private static final int CUSTOMER_ID = 1;      // khách hàng mẫu
    private static final BigDecimal PRICE = new BigDecimal("20000000");

    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final ProductSpecDAO specDAO = new ProductSpecDAO();

    private String marker;
    private BigDecimal originalTotalSpent;
    private final List<Integer> productIds = new ArrayList<>();
    private final List<Integer> specIds = new ArrayList<>();

    @BeforeAll
    static void requireDatabase() {
        boolean ok;
        try (Connection c = DBConnection.getConnection()) {
            ok = c.isValid(2);
        } catch (Exception e) {
            ok = false;
        }
        assumeTrue(ok, "Không kết nối được database, bỏ qua test");
    }

    @AfterAll
    static void closePool() {
        DBConnection.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        marker = "TEST-" + UUID.randomUUID();
        originalTotalSpent = totalSpent(CUSTOMER_ID);
        SessionManager.logout();
    }

    @AfterEach
    void cleanUp() throws Exception {
        SessionManager.logout();
        try (Connection c = DBConnection.getConnection()) {
            exec(c, "DELETE FROM Orders WHERE notes = ?", marker);     // OrderDetails xóa theo (CASCADE)
            for (int specId : specIds) {
                exec(c, "DELETE FROM Inventory WHERE spec_id = ?", specId);
            }
            exec(c, "UPDATE Customers SET total_spent = ? WHERE customer_id = ?", originalTotalSpent, CUSTOMER_ID);
        }
        for (int productId : productIds) {
            productDAO.delete(productId);                               // ProductSpecs xóa theo (CASCADE)
        }
    }

    // ------------------------------------------------------------ thành công

    @Test
    void createOrderWritesEverythingInOneTransaction() throws Exception {
        int spec = newSpec(10);
        OrderDTO order = order(CUSTOMER_ID, "60000000");
        int orderId = orderDAO.createOrder(order, List.of(detail(spec, 3, "0", "60000000")));

        assertTrue(orderId > 0);
        assertEquals(1, count("SELECT COUNT(*) FROM Orders WHERE order_id = ? AND notes = ?", orderId, marker));
        assertEquals(1, count("SELECT COUNT(*) FROM OrderDetails WHERE order_id = ? AND spec_id = ? AND quantity = 3",
                orderId, spec));
        assertEquals(7, stock(spec));
        assertEquals(1, count("SELECT COUNT(*) FROM Inventory WHERE spec_id = ? AND quantity_out = 3 "
                + "AND quantity_in = 0 AND transaction_type = 'Xuất kho' AND notes = ?", spec, "Bán hàng - đơn #" + orderId));
        assertEquals(0, originalTotalSpent.add(new BigDecimal("60000000")).compareTo(totalSpent(CUSTOMER_ID)));
    }

    @Test
    void largeOrderAbove100MillionFitsInTheColumns() throws Exception {
        int spec = newSpec(10);
        // 6 x 20.000.000 = 120.000.000, vượt giới hạn cũ DECIMAL(10,2) (99.999.999,99)
        int orderId = orderDAO.createOrder(order(CUSTOMER_ID, "120000000"),
                List.of(detail(spec, 6, "0", "120000000")));
        assertTrue(orderId > 0);
        assertEquals(4, stock(spec));
    }

    @Test
    void guestOrderDoesNotTouchAnyCustomer() throws Exception {
        int spec = newSpec(5);
        int orderId = orderDAO.createOrder(order(null, "20000000"), List.of(detail(spec, 1, "0", "20000000")));

        assertEquals(1, count("SELECT COUNT(*) FROM Orders WHERE order_id = ? AND customer_id IS NULL", orderId));
        assertEquals(0, originalTotalSpent.compareTo(totalSpent(CUSTOMER_ID)));
        assertEquals(4, stock(spec));
    }

    // ------------------------------------------------------------ rollback

    @Test
    void rollsBackEverythingWhenALaterLineIsOutOfStock() throws Exception {
        int specA = newSpec(10);
        int specB = newSpec(1);

        OutOfStockException e = assertThrows(OutOfStockException.class, () ->
                orderDAO.createOrder(order(CUSTOMER_ID, "140000000"), List.of(
                        detail(specA, 2, "0", "40000000"),        // dòng A hợp lệ, được xử lý trước
                        detail(specB, 5, "0", "100000000"))));    // dòng B vượt tồn kho

        assertEquals(specB, e.getSpecId());
        assertEquals(0, count("SELECT COUNT(*) FROM Orders WHERE notes = ?", marker), "không còn đơn nào");
        assertEquals(0, count("SELECT COUNT(*) FROM OrderDetails od JOIN Orders o USING(order_id) WHERE o.notes = ?", marker));
        assertEquals(10, stock(specA), "kho của dòng A phải được hoàn lại");
        assertEquals(1, stock(specB));
        assertEquals(0, count("SELECT COUNT(*) FROM Inventory WHERE spec_id IN (?, ?)", specA, specB));
        assertEquals(0, originalTotalSpent.compareTo(totalSpent(CUSTOMER_ID)), "total_spent không đổi");
    }

    @Test
    void rollsBackWhenCustomerDoesNotExist() throws Exception {
        int spec = newSpec(10);

        SQLException e = assertThrows(SQLException.class, () ->
                orderDAO.createOrder(order(999_999, "20000000"), List.of(detail(spec, 1, "0", "20000000"))));

        assertInstanceOf(SQLIntegrityConstraintViolationException.class, e,
                "OrderBUS dựa vào kiểu lỗi này để báo 'khách hàng không tồn tại'");
        assertEquals(0, count("SELECT COUNT(*) FROM Orders WHERE notes = ?", marker));
        assertEquals(10, stock(spec));
        assertEquals(0, count("SELECT COUNT(*) FROM Inventory WHERE spec_id = ?", spec));
    }

    @Test
    void rollsBackWhenASpecDoesNotExist() throws Exception {
        int spec = newSpec(10);

        assertThrows(SQLException.class, () ->
                orderDAO.createOrder(order(CUSTOMER_ID, "40000000"), List.of(
                        detail(spec, 1, "0", "20000000"),
                        detail(987_654, 1, "0", "20000000"))));   // spec_id không tồn tại (khóa ngoại)

        assertEquals(0, count("SELECT COUNT(*) FROM Orders WHERE notes = ?", marker));
        assertEquals(10, stock(spec));
        assertEquals(0, originalTotalSpent.compareTo(totalSpent(CUSTOMER_ID)));
    }

    // ------------------------------------------------------------ tranh chấp

    @Test
    void onlyOneBuyerGetsTheLastItem() throws Exception {
        int spec = newSpec(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return orderDAO.createOrder(order(null, "20000000"),
                            List.of(detail(spec, 1, "0", "20000000")));
                }));
            }
            start.countDown();

            int succeeded = 0;
            int outOfStock = 0;
            for (Future<Integer> f : results) {
                try {
                    f.get();
                    succeeded++;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof OutOfStockException) {
                        outOfStock++;
                    } else {
                        throw e;
                    }
                }
            }
            assertEquals(1, succeeded);
            assertEquals(1, outOfStock);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, stock(spec), "kho không được âm");
        assertEquals(1, count("SELECT COUNT(*) FROM Orders WHERE notes = ?", marker));
        assertEquals(1, count("SELECT COUNT(*) FROM Inventory WHERE spec_id = ? AND quantity_out = 1", spec));
    }

    // ------------------------------------------------------------ đi qua OrderBUS

    @Test
    void orderBusCreatesAnOrderEndToEnd() throws Exception {
        int specA = newSpec(10);
        int specB = newSpec(4);

        UserDTO staff = new UserDTO();
        staff.setUserId(3);
        staff.setUsername("staff1");
        SessionManager.login(staff, List.of(PermissionNames.PROCESS_ORDERS));
        OrderBUS bus = new OrderBUS();

        OrderDetailDTO lineA = new OrderDetailDTO();
        lineA.setSpecId(specA);
        lineA.setQuantity(2);
        lineA.setDiscountPercent(new BigDecimal("10"));          // 2 x 20.000.000 giảm 10% = 36.000.000
        OrderDetailDTO lineB = new OrderDetailDTO();
        lineB.setSpecId(specB);
        lineB.setQuantity(1);                                    // 20.000.000

        OrderDTO order = bus.createOrder(CUSTOMER_ID, null, OrderBUS.PAYMENT_TRANSFER, marker, List.of(lineA, lineB));

        assertEquals(0, new BigDecimal("56000000").compareTo(order.getTotalAmount()));
        assertEquals(8, stock(specA));
        assertEquals(3, stock(specB));
        assertEquals(0, originalTotalSpent.add(new BigDecimal("56000000")).compareTo(totalSpent(CUSTOMER_ID)));
        assertEquals(1, count("SELECT COUNT(*) FROM Orders WHERE order_id = ? AND payment_status = 'Chưa thanh toán' "
                + "AND order_status = 'Chờ xử lý' AND payment_method = 'Chuyển khoản'", order.getOrderId()));
        assertEquals(2, count("SELECT COUNT(*) FROM OrderDetails WHERE order_id = ?", order.getOrderId()));

        // Hết hàng ở lần mua sau: BUS báo lỗi tiếng Việt và không đổi dữ liệu
        OrderDetailDTO tooMany = new OrderDetailDTO();
        tooMany.setSpecId(specB);
        tooMany.setQuantity(4);
        BusinessException e = assertThrows(BusinessException.class,
                () -> bus.createOrder(CUSTOMER_ID, null, OrderBUS.PAYMENT_CASH, marker, List.of(tooMany)));
        assertTrue(e.getMessage().startsWith("Không đủ hàng"));
        assertEquals(3, stock(specB));
    }

    // ------------------------------------------------------------ helpers

    private int newSpec(int stock) throws SQLException {
        ProductDTO p = new ProductDTO();
        p.setName("ZZ_TEST_" + UUID.randomUUID().toString().substring(0, 8));
        p.setBrandId(1);
        p.setCategoryId(1);
        p.setDescription("Dùng cho test, sẽ bị xóa");
        int productId = productDAO.insert(p);
        productIds.add(productId);

        ProductSpecDTO s = new ProductSpecDTO();
        s.setProductId(productId);
        s.setPrice(PRICE);
        s.setCostPrice(new BigDecimal("18000000"));
        s.setStockQuantity(stock);
        s.setScreenSize("6.1 inch");
        s.setProcessor("Chip test");
        s.setResolution("2556x1179");
        s.setRam("8GB");
        s.setStorage("128GB");
        s.setBattery("3000 mAh");
        s.setCameraMain("48MP");
        s.setCameraFront("12MP");
        s.setWeight("180 g");
        s.setOs("Android");
        s.setColor("Đen");
        s.setWarrantyPeriod("12 tháng");
        s.setStatus("Đang bán");
        int specId = specDAO.insert(s);
        specIds.add(specId);
        return specId;
    }

    private OrderDTO order(Integer customerId, String total) {
        OrderDTO o = new OrderDTO();
        o.setCustomerId(customerId);
        o.setTotalAmount(new BigDecimal(total));
        o.setPaymentMethod("Tiền mặt");
        o.setPaymentStatus("Chưa thanh toán");
        o.setOrderStatus("Chờ xử lý");
        o.setNotes(marker);
        return o;
    }

    private static OrderDetailDTO detail(int specId, int quantity, String discount, String subtotal) {
        OrderDetailDTO d = new OrderDetailDTO();
        d.setSpecId(specId);
        d.setQuantity(quantity);
        d.setUnitPrice(PRICE);
        d.setDiscountPercent(new BigDecimal(discount));
        d.setSubtotal(new BigDecimal(subtotal));
        return d;
    }

    private static int stock(int specId) throws SQLException {
        return count("SELECT stock_quantity FROM ProductSpecs WHERE spec_id = ?", specId);
    }

    private static BigDecimal totalSpent(int customerId) throws SQLException {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT total_spent FROM Customers WHERE customer_id = ?")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    /** Chạy câu SELECT trả về một số nguyên ở cột 1. */
    private static int count(String sql, Object... params) throws SQLException {
        try (Connection c = DBConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void exec(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }
}
package com.phonestore.bus;

import com.phonestore.dao.OrderDAO;
import com.phonestore.dao.ProductDAO;
import com.phonestore.dao.ProductSpecDAO;
import com.phonestore.dto.OrderDTO;
import com.phonestore.dto.OrderDetailDTO;
import com.phonestore.dto.ProductDTO;
import com.phonestore.dto.ProductSpecDTO;
import com.phonestore.dto.UserDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.exception.OutOfStockException;
import com.phonestore.util.PermissionNames;
import com.phonestore.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test OrderBUS bằng DAO giả, không cần database. */
class OrderBUSTest {

    private static final int IPHONE_SPEC = 1;     // 27.990.000, còn 10
    private static final int REDMI_SPEC = 2;      //  9.990.000, còn 5
    private static final int PHANTOM_SPEC = 99;   // không tồn tại
    private static final int SOLD_OUT_SPEC = 3;   // Hết hàng

    private final Map<Integer, ProductSpecDTO> specs = new HashMap<>();
    private final List<OrderDTO> savedOrders = new ArrayList<>();
    private final List<List<OrderDetailDTO>> savedDetails = new ArrayList<>();
    private SQLException daoSqlError;
    private OutOfStockException daoStockError;

    private OrderBUS orderBUS;

    @BeforeEach
    void setUp() {
        specs.clear();
        savedOrders.clear();
        savedDetails.clear();
        daoSqlError = null;
        daoStockError = null;

        specs.put(IPHONE_SPEC, spec(IPHONE_SPEC, 1, "27990000", 10, "Đang bán", "128GB", "Titan"));
        specs.put(REDMI_SPEC, spec(REDMI_SPEC, 2, "9990000", 5, "Đang bán", "256GB", "Tím"));
        specs.put(SOLD_OUT_SPEC, spec(SOLD_OUT_SPEC, 3, "4290000", 0, "Hết hàng", "128GB", "Đỏ"));

        OrderDAO fakeOrderDAO = new OrderDAO() {
            @Override
            public int createOrder(OrderDTO order, List<OrderDetailDTO> details)
                    throws SQLException, OutOfStockException {
                if (daoSqlError != null) {
                    throw daoSqlError;
                }
                if (daoStockError != null) {
                    throw daoStockError;
                }
                savedOrders.add(order);
                savedDetails.add(details);
                return 100 + savedOrders.size();
            }
        };
        ProductSpecDAO fakeSpecDAO = new ProductSpecDAO() {
            @Override
            public ProductSpecDTO findById(int specId) {
                return specs.get(specId);
            }
        };
        ProductDAO fakeProductDAO = new ProductDAO() {
            @Override
            public ProductDTO findById(int productId) {
                ProductDTO p = new ProductDTO();
                p.setProductId(productId);
                p.setName(productId == 1 ? "iPhone 15 Pro" : productId == 2 ? "Redmi Note 13 Pro" : "Vivo Y28");
                return p;
            }
        };
        orderBUS = new OrderBUS(fakeOrderDAO, fakeSpecDAO, fakeProductDAO, new AuthBUS());

        loginWith(PermissionNames.PROCESS_ORDERS);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    // ------------------------------------------------------------ tính tiền

    @Test
    void calculatesSubtotalsAndTotalWithDiscount() {
        List<OrderDetailDTO> items = List.of(
                item(IPHONE_SPEC, 2, "10"),    // 27.990.000 x 2 giảm 10% = 50.382.000
                item(REDMI_SPEC, 3, null));    //  9.990.000 x 3 không giảm = 29.970.000

        OrderDTO order = orderBUS.createOrder(5, 3, OrderBUS.PAYMENT_CASH, "  giao nhanh ", items);

        assertEquals(101, order.getOrderId());
        assertMoney("80352000", order.getTotalAmount());
        assertEquals("giao nhanh", order.getNotes());
        assertEquals(OrderBUS.PAYMENT_STATUS_UNPAID, order.getPaymentStatus());
        assertEquals(OrderBUS.ORDER_STATUS_PENDING, order.getOrderStatus());
        assertEquals(5, order.getCustomerId());
        assertEquals(3, order.getStaffId());

        List<OrderDetailDTO> saved = savedDetails.get(0);
        assertEquals(2, saved.size());
        assertMoney("50382000", saved.get(0).getSubtotal());
        assertMoney("10", saved.get(0).getDiscountPercent());
        assertMoney("29970000", saved.get(1).getSubtotal());
        assertMoney("0", saved.get(1).getDiscountPercent());
    }

    @Test
    void roundsSubtotalToWholeDong() {
        // 1.000.001 x (100 - 33,33)% = 666.700,6667 => làm tròn thành 666.701
        assertMoney("666701", OrderBUS.calculateSubtotal(new BigDecimal("1000001"), 1, new BigDecimal("33.33")));
        assertMoney("1000000", OrderBUS.calculateSubtotal(new BigDecimal("1000000"), 1, null));
        assertMoney("0", OrderBUS.calculateSubtotal(new BigDecimal("1000000"), 3, new BigDecimal("100")));
    }

    @Test
    void ignoresUnitPriceAndSubtotalSentByTheScreen() {
        OrderDetailDTO tampered = item(IPHONE_SPEC, 1, null);
        tampered.setUnitPrice(BigDecimal.ONE);
        tampered.setSubtotal(BigDecimal.ONE);

        OrderDTO order = orderBUS.createOrder(5, null, OrderBUS.PAYMENT_CARD, null, List.of(tampered));

        assertMoney("27990000", savedDetails.get(0).get(0).getUnitPrice());
        assertMoney("27990000", order.getTotalAmount());
        assertNull(order.getNotes());
        assertNull(order.getStaffId());
    }

    @Test
    void guestCustomerIsAllowed() {
        OrderDTO order = orderBUS.createOrder(null, 3, OrderBUS.PAYMENT_TRANSFER, null,
                List.of(item(REDMI_SPEC, 1, null)));
        assertNull(order.getCustomerId());
        assertNull(savedOrders.get(0).getCustomerId());
    }

    // ------------------------------------------------------------ kiểm tra dữ liệu

    @Test
    void rejectsEmptyOrNullItems() {
        assertMessage("Đơn hàng phải có ít nhất một sản phẩm",
                () -> orderBUS.createOrder(5, 3, OrderBUS.PAYMENT_CASH, null, List.of()));
        assertMessage("Đơn hàng phải có ít nhất một sản phẩm",
                () -> orderBUS.createOrder(5, 3, OrderBUS.PAYMENT_CASH, null, null));
    }

    @Test
    void rejectsInvalidPaymentMethod() {
        List<OrderDetailDTO> items = List.of(item(REDMI_SPEC, 1, null));
        assertMessage("Phương thức thanh toán không hợp lệ",
                () -> orderBUS.createOrder(5, 3, "Bitcoin", null, items));
        assertMessage("Phương thức thanh toán không hợp lệ",
                () -> orderBUS.createOrder(5, 3, null, null, items));
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertMessage("Số lượng phải lớn hơn 0",
                () -> orderBUS.createOrder(5, 3, OrderBUS.PAYMENT_CASH, null, List.of(item(REDMI_SPEC, 0, null))));
        assertMessage("Số lượng phải lớn hơn 0",
                () -> orderBUS.createOrder(5, 3, OrderBUS.PAYMENT_CASH, null, List.of(item(REDMI_SPEC, -2, null))));
    }

    @Test
    void rejectsDiscountOutsideZeroToHundred() {
        assertMessage("Chiết khấu phải nằm trong khoảng từ 0 đến 100%",
                () -> orderBUS.createOrder(5, 3, OrderBUS.PAYMENT_CASH, null, List.of(item(REDMI_SPEC, 1, "-1"))));
        assertMessage("Chiết khấu phải nằm trong khoảng từ 0 đến 100%",
                () -> orderBUS.createOrder(5, 3, OrderBUS.PAYMENT_CASH, null, List.of(item(REDMI_SPEC, 1, "100.01"))));
    }

    @Test
    void rejectsDuplicateSpecLines() {
        List<OrderDetailDTO> items = List.of(item(REDMI_SPEC, 1, null), item(REDMI_SPEC, 2, null));
        BusinessException e = assertThrows(BusinessException.class,
                () -> orderBUS.createOrder(5, 3, OrderBUS.PAYMENT_CASH, null, items));
        assertTrue(e.getMessage().contains("nhiều lần"));
    }

    @Test
    void rejectsUnknownSpec() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> orderBUS.createOrder(5, 3, OrderBUS.PAYMENT_CASH, null, List.of(item(PHANTOM_SPEC, 1, null))));
        assertTrue(e.getMessage().contains("Không tìm thấy sản phẩm"));
    }

    @Test
    void rejectsSpecThatIsNotOnSale() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> orderBUS.createOrder(5, 3, OrderBUS.PAYMENT_CASH, null, List.of(item(SOLD_OUT_SPEC, 1, null))));
        assertTrue(e.getMessage().contains("không được bán"));
        assertTrue(e.getMessage().contains("Vivo Y28"));
    }

    @Test
    void rejectsWhenStockIsNotEnoughAndNamesTheProduct() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> orderBUS.createOrder(5, 3, OrderBUS.PAYMENT_CASH, null, List.of(item(REDMI_SPEC, 6, null))));
        assertTrue(e.getMessage().contains("Redmi Note 13 Pro"));
        assertTrue(e.getMessage().contains("còn 5"));
        assertTrue(e.getMessage().contains("cần 6"));
        assertTrue(savedOrders.isEmpty(), "không được lưu đơn khi thiếu hàng");
    }

    @Test
    void oneBadLineMeansNothingIsSaved() {
        List<OrderDetailDTO> items = List.of(item(IPHONE_SPEC, 1, null), item(REDMI_SPEC, 99, null));
        assertThrows(BusinessException.class,
                () -> orderBUS.createOrder(5, 3, OrderBUS.PAYMENT_CASH, null, items));
        assertTrue(savedOrders.isEmpty());
    }

    // ------------------------------------------------------------ quyền

    @Test
    void requiresLogin() {
        SessionManager.logout();
        assertMessage("Vui lòng đăng nhập để tiếp tục",
                () -> orderBUS.createOrder(5, 3, OrderBUS.PAYMENT_CASH, null, List.of(item(REDMI_SPEC, 1, null))));
    }

    @Test
    void requiresProcessOrdersPermission() {
        loginWith(PermissionNames.VIEW_PRODUCTS);
        assertMessage("Bạn không có quyền thực hiện chức năng này",
                () -> orderBUS.createOrder(5, 3, OrderBUS.PAYMENT_CASH, null, List.of(item(REDMI_SPEC, 1, null))));
        assertTrue(savedOrders.isEmpty());
    }

    // ------------------------------------------------------------ lỗi từ DAO

    @Test
    void outOfStockFromDaoBecomesFriendlyMessage() {
        daoStockError = new OutOfStockException(REDMI_SPEC);
        BusinessException e = assertThrows(BusinessException.class,
                () -> orderBUS.createOrder(5, 3, OrderBUS.PAYMENT_CASH, null, List.of(item(REDMI_SPEC, 1, null))));
        assertTrue(e.getMessage().contains("hết hàng"));
        assertSame(daoStockError, e.getCause());
    }

    @Test
    void foreignKeyViolationBecomesMessageAboutCustomerOrStaff() {
        daoSqlError = new SQLIntegrityConstraintViolationException("FK");
        BusinessException e = assertThrows(BusinessException.class,
                () -> orderBUS.createOrder(999, 3, OrderBUS.PAYMENT_CASH, null, List.of(item(REDMI_SPEC, 1, null))));
        assertTrue(e.getMessage().contains("không tồn tại"));
    }

    @Test
    void otherSqlErrorBecomesGenericDatabaseMessage() {
        daoSqlError = new SQLException("mất kết nối");
        BusinessException e = assertThrows(BusinessException.class,
                () -> orderBUS.createOrder(5, 3, OrderBUS.PAYMENT_CASH, null, List.of(item(REDMI_SPEC, 1, null))));
        assertTrue(e.getMessage().contains("lỗi cơ sở dữ liệu"));
        assertSame(daoSqlError, e.getCause());
    }

    // ------------------------------------------------------------ helpers

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "mong đợi " + expected + " nhưng là " + actual);
    }

    private static void assertMessage(String expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(BusinessException.class, action).getMessage());
    }

    private static void loginWith(String... permissions) {
        UserDTO u = new UserDTO();
        u.setUserId(3);
        u.setUsername("staff1");
        SessionManager.login(u, List.of(permissions));
    }

    private static OrderDetailDTO item(int specId, int quantity, String discount) {
        OrderDetailDTO d = new OrderDetailDTO();
        d.setSpecId(specId);
        d.setQuantity(quantity);
        d.setDiscountPercent(discount == null ? null : new BigDecimal(discount));
        return d;
    }

    private static ProductSpecDTO spec(int specId, int productId, String price, int stock, String status,
                                       String storage, String color) {
        ProductSpecDTO s = new ProductSpecDTO();
        s.setSpecId(specId);
        s.setProductId(productId);
        s.setPrice(new BigDecimal(price));
        s.setStockQuantity(stock);
        s.setStatus(status);
        s.setStorage(storage);
        s.setColor(color);
        return s;
    }
}
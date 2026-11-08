package com.phonestore.bus;

import com.phonestore.dao.OrderDAO;
import com.phonestore.dao.ProductDAO;
import com.phonestore.dao.ProductSpecDAO;
import com.phonestore.dto.OrderDTO;
import com.phonestore.dto.OrderDetailDTO;
import com.phonestore.dto.ProductDTO;
import com.phonestore.dto.ProductSpecDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.exception.OutOfStockException;
import com.phonestore.util.PermissionNames;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OrderBUS {

    public static final String PAYMENT_CASH = "Tiền mặt";
    public static final String PAYMENT_TRANSFER = "Chuyển khoản";
    public static final String PAYMENT_CARD = "Thẻ";
    public static final List<String> PAYMENT_METHODS = List.of(PAYMENT_CASH, PAYMENT_TRANSFER, PAYMENT_CARD);

    public static final String PAYMENT_STATUS_UNPAID = "Chưa thanh toán";
    public static final String ORDER_STATUS_PENDING = "Chờ xử lý";

    static final String SPEC_STATUS_ON_SALE = "Đang bán";

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final OrderDAO orderDAO;
    private final ProductSpecDAO specDAO;
    private final ProductDAO productDAO;
    private final AuthBUS authBUS;

    public OrderBUS() {
        this(new OrderDAO(), new ProductSpecDAO(), new ProductDAO(), new AuthBUS());
    }

    public OrderBUS(OrderDAO orderDAO, ProductSpecDAO specDAO, ProductDAO productDAO, AuthBUS authBUS) {
        this.orderDAO = orderDAO;
        this.specDAO = specDAO;
        this.productDAO = productDAO;
        this.authBUS = authBUS;
    }

   
    public OrderDTO createOrder(Integer customerId, Integer staffId, String paymentMethod,
                                String notes, List<OrderDetailDTO> items) {
        authBUS.requirePermission(PermissionNames.PROCESS_ORDERS);

        if (items == null || items.isEmpty()) {
            throw new BusinessException("Đơn hàng phải có ít nhất một sản phẩm");
        }
        if (paymentMethod == null || !PAYMENT_METHODS.contains(paymentMethod)) {
            throw new BusinessException("Phương thức thanh toán không hợp lệ");
        }

        try {
            List<OrderDetailDTO> details = new ArrayList<>();
            Set<Integer> seenSpecs = new HashSet<>();
            BigDecimal total = BigDecimal.ZERO;

            for (OrderDetailDTO item : items) {
                OrderDetailDTO line = buildLine(item, seenSpecs);
                details.add(line);
                total = total.add(line.getSubtotal());
            }

            OrderDTO order = new OrderDTO();
            order.setCustomerId(customerId);
            order.setStaffId(staffId);
            order.setTotalAmount(total);
            order.setPaymentMethod(paymentMethod);
            order.setPaymentStatus(PAYMENT_STATUS_UNPAID);
            order.setOrderStatus(ORDER_STATUS_PENDING);
            order.setNotes(notes == null || notes.isBlank() ? null : notes.trim());

            order.setOrderId(orderDAO.createOrder(order, details));
            return order;

        } catch (OutOfStockException e) {
            throw new BusinessException(
                    "Có sản phẩm vừa hết hàng hoặc không đủ số lượng, vui lòng kiểm tra lại giỏ hàng", e);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new BusinessException("Khách hàng hoặc nhân viên của đơn hàng không tồn tại", e);
        } catch (SQLException e) {
            throw new BusinessException("Không thể tạo đơn hàng do lỗi cơ sở dữ liệu", e);
        }
    }

    public static BigDecimal calculateSubtotal(BigDecimal unitPrice, int quantity, BigDecimal discountPercent) {
        BigDecimal discount = discountPercent == null ? BigDecimal.ZERO : discountPercent;
        return unitPrice
                .multiply(BigDecimal.valueOf(quantity))
                .multiply(HUNDRED.subtract(discount))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP);
    }

    private OrderDetailDTO buildLine(OrderDetailDTO item, Set<Integer> seenSpecs) throws SQLException {
        if (item.getQuantity() <= 0) {
            throw new BusinessException("Số lượng phải lớn hơn 0");
        }
        BigDecimal discount = item.getDiscountPercent() == null ? BigDecimal.ZERO : item.getDiscountPercent();
        if (discount.compareTo(BigDecimal.ZERO) < 0 || discount.compareTo(HUNDRED) > 0) {
            throw new BusinessException("Chiết khấu phải nằm trong khoảng từ 0 đến 100%");
        }
        if (!seenSpecs.add(item.getSpecId())) {
            throw new BusinessException("Một sản phẩm xuất hiện nhiều lần trong đơn, hãy gộp thành một dòng");
        }

        ProductSpecDTO spec = specDAO.findById(item.getSpecId());
        if (spec == null) {
            throw new BusinessException("Không tìm thấy sản phẩm (mã cấu hình " + item.getSpecId() + ")");
        }
        String label = describe(spec);
        if (!SPEC_STATUS_ON_SALE.equals(spec.getStatus())) {
            throw new BusinessException(label + " hiện không được bán (" + spec.getStatus() + ")");
        }
        if (spec.getStockQuantity() < item.getQuantity()) {
            throw new BusinessException("Không đủ hàng: " + label + " (còn " + spec.getStockQuantity()
                    + ", cần " + item.getQuantity() + ")");
        }

        OrderDetailDTO line = new OrderDetailDTO();
        line.setSpecId(spec.getSpecId());
        line.setQuantity(item.getQuantity());
        line.setUnitPrice(spec.getPrice());          
        line.setDiscountPercent(discount);
        line.setSubtotal(calculateSubtotal(spec.getPrice(), item.getQuantity(), discount));
        return line;
    }

    private String describe(ProductSpecDTO spec) throws SQLException {
        ProductDTO product = productDAO.findById(spec.getProductId());
        String name = product == null ? "Sản phẩm #" + spec.getProductId() : product.getName();
        return (name + " " + nullToEmpty(spec.getStorage()) + " " + nullToEmpty(spec.getColor())).trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
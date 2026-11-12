package com.phonestore.bus;

import com.phonestore.dao.OrderDAO;
import com.phonestore.dao.ProductDAO;
import com.phonestore.dao.ProductSpecDAO;
import com.phonestore.dto.InvoiceDTO;
import com.phonestore.dto.OrderDTO;
import com.phonestore.dto.OrderDetailDTO;
import com.phonestore.dto.OrderDetailItemDTO;
import com.phonestore.dto.OrderFilter;
import com.phonestore.dto.OrderListItemDTO;
import com.phonestore.dto.ProductDTO;
import com.phonestore.dto.ProductSpecDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.exception.OutOfStockException;
import com.phonestore.util.OrderConstants;
import com.phonestore.util.PermissionNames;
import com.phonestore.util.SqlErrorMapper;
import com.phonestore.util.Validator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Nghiệp vụ đơn hàng: tạo đơn, xem và lọc đơn, hóa đơn để in, đổi trạng thái, thanh toán, hủy đơn.
 *
 * Quyền: xem đơn cần view_orders; tạo, đổi trạng thái, thanh toán, hủy cần process_orders.
 * Mọi lỗi là BusinessException với thông điệp tiếng Việt, GUI hiển thị trực tiếp.
 *
 * Các hàm GUI hay dùng:
 *   createOrder(...)                        màn hình bán hàng
 *   searchOrders(filter)                    danh sách đơn (lọc theo trạng thái, ngày, khách, từ khóa)
 *   getOrdersByCustomer(customerId)         lịch sử mua của một khách
 *   getOrderItems(orderId), getInvoice(id)  chi tiết đơn / dữ liệu để in hóa đơn
 *   getNextStatuses(current)                các trạng thái được phép chuyển tới (bật/tắt nút)
 *   updateOrderStatus / markAsPaid / cancelOrder
 */
public class OrderBUS {

    // Giữ các hằng số cũ làm lối tắt; giá trị thật nằm ở OrderConstants
    public static final String PAYMENT_CASH = OrderConstants.PAYMENT_CASH;
    public static final String PAYMENT_TRANSFER = OrderConstants.PAYMENT_TRANSFER;
    public static final String PAYMENT_CARD = OrderConstants.PAYMENT_CARD;
    public static final List<String> PAYMENT_METHODS = OrderConstants.PAYMENT_METHODS;

    public static final String PAYMENT_STATUS_UNPAID = OrderConstants.PAYMENT_STATUS_UNPAID;
    public static final String ORDER_STATUS_PENDING = OrderConstants.ORDER_STATUS_PENDING;

    /** Trạng thái của ProductSpecs cho phép bán. */
    static final String SPEC_STATUS_ON_SALE = "Đang bán";

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static final String STALE_MESSAGE =
            "Trạng thái đơn hàng vừa bị thay đổi bởi người khác, vui lòng tải lại danh sách";

    private final OrderDAO orderDAO;
    private final ProductSpecDAO specDAO;
    private final ProductDAO productDAO;
    private final AuthBUS authBUS;

    public OrderBUS() {
        this(new OrderDAO(), new ProductSpecDAO(), new ProductDAO(), new AuthBUS());
    }

    /** Dùng cho test: truyền DAO giả. */
    public OrderBUS(OrderDAO orderDAO, ProductSpecDAO specDAO, ProductDAO productDAO, AuthBUS authBUS) {
        this.orderDAO = orderDAO;
        this.specDAO = specDAO;
        this.productDAO = productDAO;
        this.authBUS = authBUS;
    }

    // ================================================================ tạo đơn

    /**
     * Tạo đơn hàng.
     *
     * @param customerId    mã khách hàng, null nếu là khách vãng lai (khi đó không cộng total_spent)
     * @param staffId       mã nhân viên lập đơn, có thể null
     * @param paymentMethod một trong PAYMENT_METHODS
     * @param notes         ghi chú, có thể null
     * @param items         các dòng hàng: chỉ cần specId, quantity, discountPercent (giá do BUS lấy từ database)
     * @return đơn vừa tạo (có orderId, totalAmount và các trạng thái ban đầu)
     */
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
            order.setPaymentStatus(OrderConstants.PAYMENT_STATUS_UNPAID);
            order.setOrderStatus(OrderConstants.ORDER_STATUS_PENDING);
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

    /**
     * Thành tiền của một dòng = đơn giá × số lượng × (100 − chiết khấu%) / 100,
     * làm tròn đến đồng (HALF_UP).
     */
    public static BigDecimal calculateSubtotal(BigDecimal unitPrice, int quantity, BigDecimal discountPercent) {
        BigDecimal discount = discountPercent == null ? BigDecimal.ZERO : discountPercent;
        return unitPrice
                .multiply(BigDecimal.valueOf(quantity))
                .multiply(HUNDRED.subtract(discount))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP);
    }

    // ================================================================ xem đơn

    /** Danh sách đơn theo bộ lọc, mới nhất trước. Filter null hoặc rỗng = tất cả. */
    public List<OrderListItemDTO> searchOrders(OrderFilter filter) {
        authBUS.requirePermission(PermissionNames.VIEW_ORDERS);

        if (filter != null) {
            if (filter.getOrderStatus() != null && !filter.getOrderStatus().isBlank()) {
                Validator.requireOneOf(filter.getOrderStatus(), "Trạng thái đơn hàng", OrderConstants.ORDER_STATUSES);
            }
            if (filter.getPaymentStatus() != null && !filter.getPaymentStatus().isBlank()) {
                Validator.requireOneOf(filter.getPaymentStatus(), "Trạng thái thanh toán", OrderConstants.PAYMENT_STATUSES);
            }
            if (filter.getFromDate() != null && filter.getToDate() != null
                    && filter.getFromDate().isAfter(filter.getToDate())) {
                throw new BusinessException("Ngày bắt đầu không được sau ngày kết thúc");
            }
        }
        try {
            return orderDAO.search(filter);
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể tải danh sách đơn hàng do lỗi cơ sở dữ liệu");
        }
    }

    /** Lịch sử mua của một khách hàng, mới nhất trước. */
    public List<OrderListItemDTO> getOrdersByCustomer(int customerId) {
        Validator.requireSelected(customerId, "khách hàng");
        OrderFilter filter = new OrderFilter();
        filter.setCustomerId(customerId);
        return searchOrders(filter);
    }

    /** Thông tin một đơn hàng. Ném lỗi nếu không tồn tại. */
    public OrderListItemDTO getOrder(int orderId) {
        authBUS.requirePermission(PermissionNames.VIEW_ORDERS);
        return loadOrder(orderId);
    }

    /** Các dòng hàng của một đơn (kèm tên sản phẩm). */
    public List<OrderDetailItemDTO> getOrderItems(int orderId) {
        authBUS.requirePermission(PermissionNames.VIEW_ORDERS);
        try {
            return orderDAO.findItems(orderId);
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể tải chi tiết đơn hàng do lỗi cơ sở dữ liệu");
        }
    }

    /**
     * Dữ liệu đầy đủ để in hóa đơn (đơn, địa chỉ khách, các dòng hàng, tiền hàng, giảm giá, tổng).
     * Dùng InvoiceFormatter.toText(invoice) để có bản in dạng chữ.
     */
    public InvoiceDTO getInvoice(int orderId) {
        authBUS.requirePermission(PermissionNames.VIEW_ORDERS);
        OrderListItemDTO order = loadOrder(orderId);
        try {
            List<OrderDetailItemDTO> items = orderDAO.findItems(orderId);

            BigDecimal gross = BigDecimal.ZERO;
            for (OrderDetailItemDTO item : items) {
                gross = gross.add(item.getGrossAmount());
            }

            InvoiceDTO invoice = new InvoiceDTO();
            invoice.setOrder(order);
            invoice.setItems(items);
            invoice.setGrossTotal(gross);
            invoice.setTotalAmount(order.getTotalAmount());
            invoice.setDiscountTotal(gross.subtract(order.getTotalAmount()));
            if (order.getCustomerId() != null) {
                invoice.setCustomerAddress(orderDAO.findCustomerAddress(order.getCustomerId()));
            }
            return invoice;
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể tải hóa đơn do lỗi cơ sở dữ liệu");
        }
    }

    // ================================================================ đổi trạng thái

    /** Các trạng thái đơn được phép chuyển tới từ trạng thái hiện tại (rỗng nếu đã là trạng thái cuối). */
    public List<String> getNextStatuses(String currentStatus) {
        return OrderConstants.NEXT_ORDER_STATUSES.getOrDefault(currentStatus, List.of());
    }

    /**
     * Chuyển đơn sang trạng thái mới (Đang giao, Hoàn thành hoặc Đã hủy). Chuyển sang Đã hủy tương đương
     * cancelOrder (hoàn kho). Chuyển sang Hoàn thành thì ghi ngày giao.
     */
    public void updateOrderStatus(int orderId, String newStatus) {
        authBUS.requirePermission(PermissionNames.PROCESS_ORDERS);
        Validator.requireOneOf(newStatus, "Trạng thái đơn hàng", OrderConstants.ORDER_STATUSES);

        if (OrderConstants.ORDER_STATUS_CANCELLED.equals(newStatus)) {
            cancelOrder(orderId);
            return;
        }

        OrderListItemDTO order = loadOrder(orderId);
        String current = order.getOrderStatus();
        if (!getNextStatuses(current).contains(newStatus)) {
            throw new BusinessException("Không thể chuyển đơn hàng từ \"" + current + "\" sang \"" + newStatus + "\"");
        }
        try {
            if (!orderDAO.updateOrderStatus(orderId, current, newStatus)) {
                throw new BusinessException(STALE_MESSAGE);
            }
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể cập nhật trạng thái đơn hàng do lỗi cơ sở dữ liệu");
        }
    }

    /** Đánh dấu đơn đã thanh toán. Chỉ áp dụng cho đơn chưa thanh toán và chưa hủy. */
    public void markAsPaid(int orderId) {
        authBUS.requirePermission(PermissionNames.PROCESS_ORDERS);
        OrderListItemDTO order = loadOrder(orderId);

        if (OrderConstants.ORDER_STATUS_CANCELLED.equals(order.getOrderStatus())) {
            throw new BusinessException("Đơn hàng đã hủy, không thể thanh toán");
        }
        if (!OrderConstants.PAYMENT_STATUS_UNPAID.equals(order.getPaymentStatus())) {
            throw new BusinessException("Đơn hàng này đã được thanh toán");
        }
        try {
            if (!orderDAO.updatePaymentStatus(orderId, OrderConstants.PAYMENT_STATUS_UNPAID,
                    OrderConstants.PAYMENT_STATUS_PAID)) {
                throw new BusinessException(STALE_MESSAGE);
            }
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể cập nhật thanh toán do lỗi cơ sở dữ liệu");
        }
    }

    /**
     * Hủy đơn: hoàn lại tồn kho, ghi lịch sử kho, trừ lại tổng chi tiêu của khách, đơn đã thanh toán
     * chuyển thành "Đã hoàn tiền". Chỉ hủy được đơn "Chờ xử lý" hoặc "Đang giao".
     */
    public void cancelOrder(int orderId) {
        authBUS.requirePermission(PermissionNames.PROCESS_ORDERS);
        OrderListItemDTO order = loadOrder(orderId);

        if (OrderConstants.ORDER_STATUS_CANCELLED.equals(order.getOrderStatus())) {
            throw new BusinessException("Đơn hàng đã được hủy trước đó");
        }
        if (OrderConstants.ORDER_STATUS_COMPLETED.equals(order.getOrderStatus())) {
            throw new BusinessException("Đơn hàng đã hoàn thành, không thể hủy");
        }
        try {
            if (!orderDAO.cancelOrder(orderId)) {
                throw new BusinessException(STALE_MESSAGE);
            }
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể hủy đơn hàng do lỗi cơ sở dữ liệu");
        }
    }

    // ================================================================ helpers

    private OrderListItemDTO loadOrder(int orderId) {
        try {
            OrderListItemDTO order = orderDAO.findListItemById(orderId);
            if (order == null) {
                throw new BusinessException("Không tìm thấy đơn hàng #" + orderId);
            }
            return order;
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể tải đơn hàng do lỗi cơ sở dữ liệu");
        }
    }

    /** Kiểm tra một dòng GUI gửi lên và dựng dòng chuẩn (giá lấy từ database). */
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
        line.setUnitPrice(spec.getPrice());          // giá lấy từ database, bỏ qua giá GUI gửi
        line.setDiscountPercent(discount);
        line.setSubtotal(calculateSubtotal(spec.getPrice(), item.getQuantity(), discount));
        return line;
    }

    /** Tên dễ đọc của một phiên bản, ví dụ "iPhone 15 Pro 128GB Titan tự nhiên". */
    private String describe(ProductSpecDTO spec) throws SQLException {
        ProductDTO product = productDAO.findById(spec.getProductId());
        String name = product == null ? "Sản phẩm #" + spec.getProductId() : product.getName();
        return (name + " " + nullToEmpty(spec.getStorage()) + " " + nullToEmpty(spec.getColor())).trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

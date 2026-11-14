package com.phonestore.gui;

import com.phonestore.bus.OrderBUS;
import com.phonestore.dto.OrderDetailItemDTO;
import com.phonestore.dto.OrderFilter;
import com.phonestore.dto.OrderListItemDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.PermissionNames;
import com.phonestore.util.SessionManager;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Function;

/**
 * Màn hình Quản lý đơn hàng (Order.fxml): danh sách, lọc, xem chi tiết,
 * cập nhật trạng thái, xem trước/in hóa đơn.
 * Dữ liệu lấy từ OrderBUS (searchOrders, getOrderItems, updateOrderStatus), chạy trong Task nền.
 */
public class OrderController implements Initializable {

    private static final String ALL_STATUS = "Tất cả trạng thái";
    /** Khớp OrderConstants của BE. */
    private static final List<String> STATUSES = List.of("Chờ xử lý", "Đang giao", "Hoàn thành", "Đã hủy");
    /** Chuyển trạng thái hợp lệ (khớp quy tắc ở OrderBUS). Trạng thái cuối: không còn lựa chọn. */
    private static final Map<String, List<String>> NEXT = Map.of(
            "Chờ xử lý", List.of("Đang giao", "Hoàn thành", "Đã hủy"),
            "Đang giao", List.of("Hoàn thành", "Đã hủy"));

    private static final String DB_ERROR =
            "Không kết nối được cơ sở dữ liệu. Hãy chạy \"docker compose up -d\" rồi bấm Làm mới.";

    private static final NumberFormat MONEY = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    static { MONEY.setMaximumFractionDigits(0); }

    // ---------- Thành phần trong Order.fxml ----------
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private DatePicker fromDate;
    @FXML private DatePicker toDate;
    @FXML private Label countLabel;

    @FXML private TableView<OrderRow> table;
    @FXML private TableColumn<OrderRow, OrderRow> colId;
    @FXML private TableColumn<OrderRow, OrderRow> colCustomer;
    @FXML private TableColumn<OrderRow, OrderRow> colDate;
    @FXML private TableColumn<OrderRow, OrderRow> colTotal;
    @FXML private TableColumn<OrderRow, OrderRow> colMethod;
    @FXML private TableColumn<OrderRow, OrderRow> colStatus;
    @FXML private TableColumn<OrderRow, OrderRow> colActions;

    private final ObservableList<OrderRow> allOrders = FXCollections.observableArrayList();
    private OrderBUS orderBUS;
    private boolean updatingFilters = false;

    // =====================================================================
    //  Khởi tạo
    // =====================================================================
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupColumns();

        statusFilter.getItems().add(ALL_STATUS);
        statusFilter.getItems().addAll(STATUSES);
        statusFilter.setValue(ALL_STATUS);

        searchField.textProperty().addListener((o, a, b) -> refresh());
        statusFilter.valueProperty().addListener((o, a, b) -> { if (!updatingFilters) refresh(); });
        fromDate.valueProperty().addListener((o, a, b) -> { if (!updatingFilters) refresh(); });
        toDate.valueProperty().addListener((o, a, b) -> { if (!updatingFilters) refresh(); });

        reload();
    }

    @FXML
    private void onRefresh() {
        reload();
    }

    /** Tải lại toàn bộ đơn ở thread nền. */
    private void reload() {
        table.setPlaceholder(new Label("Đang tải đơn hàng..."));
        Task<List<OrderRow>> task = new Task<>() {
            @Override protected List<OrderRow> call() {
                return fetchOrders();
            }
        };
        task.setOnSucceeded(e -> {
            allOrders.setAll(task.getValue());
            table.setPlaceholder(new Label("Không tìm thấy đơn hàng phù hợp. Thử đổi từ khóa hoặc xóa bộ lọc."));
            refresh();
        });
        task.setOnFailed(e -> {
            allOrders.clear();
            table.setPlaceholder(new Label(errorMessage(task.getException())));
            refresh();
        });
        startThread(task);
    }

    private void setupColumns() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        for (TableColumn<OrderRow, OrderRow> col :
                List.of(colId, colCustomer, colDate, colTotal, colMethod, colStatus, colActions)) {
            col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        }
        colId.setCellFactory(c -> textCell(r -> "DH" + r.id()));
        colCustomer.setCellFactory(c -> textCell(OrderRow::customer));
        colDate.setCellFactory(c -> textCell(r -> r.date() == null ? "" : r.date().format(DATETIME)));
        colTotal.setCellFactory(c -> textCell(r -> formatMoney(r.total())));
        colMethod.setCellFactory(c -> textCell(OrderRow::method));

        colStatus.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(OrderRow item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : statusBadge(item.status()));
            }
        });

        colActions.setCellFactory(c -> new TableCell<>() {
            private final Button view = new Button("Xem");
            { view.getStyleClass().add("ord-btn-small"); view.setOnAction(e -> showDetail(getItem())); }
            @Override protected void updateItem(OrderRow item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : view);
            }
        });
    }

    private TableCell<OrderRow, OrderRow> textCell(Function<OrderRow, String> getter) {
        return new TableCell<>() {
            @Override protected void updateItem(OrderRow item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : getter.apply(item));
            }
        };
    }

    // =====================================================================
    //  Lọc
    // =====================================================================
    private boolean matches(OrderRow r) {
        String kw = normalize(searchField.getText());
        if (!kw.isEmpty()) {
            String hay = normalize("DH" + r.id() + " " + r.id() + " " + r.customer());
            if (!hay.contains(kw)) return false;
        }
        String st = statusFilter.getValue();
        if (st != null && !st.equals(ALL_STATUS) && !st.equals(r.status())) return false;

        if (r.date() == null) return fromDate.getValue() == null && toDate.getValue() == null;
        LocalDate d = r.date().toLocalDate();
        if (fromDate.getValue() != null && d.isBefore(fromDate.getValue())) return false;
        return toDate.getValue() == null || !d.isAfter(toDate.getValue());
    }

    private void refresh() {
        List<OrderRow> filtered = allOrders.stream().filter(this::matches).toList();
        table.setItems(FXCollections.observableArrayList(filtered));
        countLabel.setText(filtered.size() + " đơn hàng");
    }

    @FXML
    private void onResetFilters() {
        updatingFilters = true;
        searchField.clear();
        statusFilter.setValue(ALL_STATUS);
        fromDate.setValue(null);
        toDate.setValue(null);
        updatingFilters = false;
        refresh();
    }

    // =====================================================================
    //  Hộp thoại: chi tiết đơn + cập nhật trạng thái
    // =====================================================================
    private void showDetail(OrderRow order) {
        Dialog<Void> dlg = newDialog("Chi tiết đơn hàng DH" + order.id());
        DialogPane pane = dlg.getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);

        Label name = new Label("Đơn hàng DH" + order.id());
        name.getStyleClass().add("ord-dialog-name");
        HBox head = new HBox(12, name, statusBadge(order.status()));
        head.setAlignment(Pos.CENTER_LEFT);

        GridPane info = new GridPane();
        info.setHgap(24);
        info.setVgap(8);
        addInfo(info, 0, "Khách hàng", order.customer());
        addInfo(info, 1, "Ngày đặt", order.date() == null ? "" : order.date().format(DATETIME));
        addInfo(info, 2, "Thanh toán", order.method() + " (" + order.payStatus() + ")");
        addInfo(info, 3, "Tổng tiền", formatMoney(order.total()));

        List<OrderLine> lines = new ArrayList<>();
        String linesError = null;
        try {
            lines.addAll(fetchLines(order.id()));
        } catch (BusinessException ex) {
            linesError = ex.getMessage();
        } catch (RuntimeException ex) {
            linesError = DB_ERROR;
        }

        TableView<OrderLine> items = new TableView<>(FXCollections.observableArrayList(lines));
        items.getStyleClass().add("ord-table");
        items.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        items.setPlaceholder(new Label(linesError != null ? linesError : "Đơn này chưa có chi tiết sản phẩm"));
        items.setPrefHeight(200);
        items.getColumns().add(lineColumn("SẢN PHẨM", 240, OrderLine::product));
        items.getColumns().add(lineColumn("SL", 50, l -> String.valueOf(l.qty())));
        items.getColumns().add(lineColumn("ĐƠN GIÁ", 110, l -> formatMoney(l.unitPrice())));
        items.getColumns().add(lineColumn("GIẢM", 60, l -> l.discountPercent().stripTrailingZeros().toPlainString() + "%"));
        items.getColumns().add(lineColumn("THÀNH TIỀN", 120, l -> formatMoney(l.subtotal())));

        // Cập nhật trạng thái: chỉ cho chọn các trạng thái hợp lệ tiếp theo
        List<String> next = NEXT.getOrDefault(order.status(), List.of());
        List<String> options = new ArrayList<>();
        options.add(order.status());
        options.addAll(next);
        ComboBox<String> statusBox = new ComboBox<>(FXCollections.observableArrayList(options));
        statusBox.setValue(order.status());
        Button save = new Button("Lưu trạng thái");
        save.getStyleClass().add("ord-primary-btn");
        Button print = new Button("In hóa đơn");
        print.getStyleClass().add("ord-outline-btn");
        Label msg = new Label();

        boolean canEdit = canProcessOrders() && !next.isEmpty();
        statusBox.setDisable(!canEdit);
        save.setDisable(!canEdit);
        if (!canProcessOrders()) {
            msg.setText("Bạn không có quyền cập nhật trạng thái đơn hàng.");
            msg.getStyleClass().add("ord-msg-err");
        } else if (next.isEmpty()) {
            msg.setText("Đơn ở trạng thái cuối, không thể đổi trạng thái.");
            msg.getStyleClass().add("ord-msg-err");
        }

        save.setOnAction(e -> {
            String target = statusBox.getValue();
            msg.getStyleClass().removeAll("ord-msg-ok", "ord-msg-err");
            if (target == null || target.equals(order.status())) {
                msg.setText("Hãy chọn trạng thái mới.");
                msg.getStyleClass().add("ord-msg-err");
                return;
            }
            save.setDisable(true);
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    bus().updateOrderStatus(order.id(), target);
                    return null;
                }
            };
            task.setOnSucceeded(ev -> {
                reload();
                dlg.close();
            });
            task.setOnFailed(ev -> {
                save.setDisable(false);
                msg.setText(errorMessage(task.getException()));
                msg.getStyleClass().add("ord-msg-err");
            });
            startThread(task);
        });
        print.setOnAction(e -> showInvoice(order, lines));

        HBox actions = new HBox(10, new Label("Trạng thái:"), statusBox, save, print);
        actions.setAlignment(Pos.CENTER_LEFT);

        Label sec = new Label("Sản phẩm trong đơn");
        sec.getStyleClass().add("ord-section-title");
        VBox content = new VBox(14, head, info, sec, items, actions, msg);
        content.setPrefWidth(640);
        pane.setContent(content);
        dlg.showAndWait();
    }

    private TableColumn<OrderLine, String> lineColumn(String title, double width, Function<OrderLine, String> getter) {
        TableColumn<OrderLine, String> col = new TableColumn<>(title);
        col.setPrefWidth(width);
        col.setSortable(false);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(getter.apply(c.getValue())));
        return col;
    }

    private void addInfo(GridPane grid, int row, String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add("ord-info-label");
        Label v = new Label(value);
        v.getStyleClass().add("ord-info-value");
        grid.add(l, 0, row);
        grid.add(v, 1, row);
    }

    // =====================================================================
    //  Xem trước hóa đơn (văn bản + sao chép). Chưa có thư viện PDF trong build.gradle.
    // =====================================================================
    private void showInvoice(OrderRow o, List<OrderLine> lines) {
        Dialog<Void> dlg = newDialog("Hóa đơn DH" + o.id());
        DialogPane pane = dlg.getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);

        TextArea area = new TextArea(buildInvoiceText(o, lines));
        area.setEditable(false);
        area.setPrefSize(520, 380);

        Button copy = new Button("Sao chép nội dung");
        copy.getStyleClass().add("ord-outline-btn");
        copy.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(area.getText());
            Clipboard.getSystemClipboard().setContent(cc);
        });
        pane.setContent(new VBox(10, area, copy));
        dlg.showAndWait();
    }

    private String buildInvoiceText(OrderRow o, List<OrderLine> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("        PHONE MANAGEMENT - HÓA ĐƠN BÁN HÀNG\n");
        sb.append("--------------------------------------------\n");
        sb.append("Mã đơn     : DH").append(o.id()).append('\n');
        sb.append("Ngày       : ").append(o.date() == null ? "" : o.date().format(DATETIME)).append('\n');
        sb.append("Khách hàng : ").append(o.customer()).append('\n');
        sb.append("Thanh toán : ").append(o.method()).append(" - ").append(o.payStatus()).append('\n');
        sb.append("Trạng thái : ").append(o.status()).append('\n');
        sb.append("--------------------------------------------\n");
        for (OrderLine l : lines) {
            sb.append(l.product()).append('\n');
            sb.append("   ").append(l.qty()).append(" x ").append(formatMoney(l.unitPrice()))
              .append(" (giảm ").append(l.discountPercent().stripTrailingZeros().toPlainString()).append("%) = ")
              .append(formatMoney(l.subtotal())).append('\n');
        }
        sb.append("--------------------------------------------\n");
        sb.append("TỔNG CỘNG  : ").append(formatMoney(o.total())).append('\n');
        sb.append("\n        Cảm ơn quý khách!\n");
        return sb.toString();
    }

    // =====================================================================
    //  Tiện ích giao diện
    // =====================================================================
    private <T> Dialog<T> newDialog(String title) {
        Dialog<T> dlg = new Dialog<>();
        dlg.setTitle(title);
        dlg.setHeaderText(null);
        if (table.getScene() != null) dlg.initOwner(table.getScene().getWindow());
        DialogPane pane = dlg.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/css/order.css").toExternalForm());
        pane.getStyleClass().add("ord-dialog");
        return dlg;
    }

    private Label statusBadge(String status) {
        Label b = new Label(status);
        b.getStyleClass().addAll("ord-badge", badgeClass(status));
        return b;
    }

    private String badgeClass(String status) {
        String s = normalize(status);
        if (s.contains("hoan thanh")) return "ord-badge-green";
        if (s.contains("huy")) return "ord-badge-red";
        if (s.contains("dang giao")) return "ord-badge-blue";
        return "ord-badge-yellow";
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').trim();
    }

    private static String formatMoney(BigDecimal amount) {
        return MONEY.format(amount == null ? BigDecimal.ZERO : amount) + " ₫";
    }

    private static String errorMessage(Throwable ex) {
        return ex instanceof BusinessException && ex.getMessage() != null ? ex.getMessage() : DB_ERROR;
    }

    private static void startThread(Task<?> task) {
        Thread t = new Thread(task, "order-loader");
        t.setDaemon(true);
        t.start();
    }

    // =====================================================================
    //  KẾT NỐI BUS
    // =====================================================================
    public record OrderRow(int id, String customer, LocalDateTime date, BigDecimal total,
                           String method, String payStatus, String status) { }

    public record OrderLine(String product, int qty, BigDecimal unitPrice,
                            BigDecimal discountPercent, BigDecimal subtotal) { }

    private OrderBUS bus() {
        if (orderBUS == null) orderBUS = new OrderBUS();
        return orderBUS;
    }

    /** Chạy ở thread nền. Cần quyền view_orders (BUS tự kiểm tra). */
    private List<OrderRow> fetchOrders() {
        List<OrderRow> rows = new ArrayList<>();
        for (OrderListItemDTO o : bus().searchOrders(new OrderFilter())) {
            Timestamp ts = o.getOrderDate();
            rows.add(new OrderRow(
                    o.getOrderId(),
                    o.getCustomerName() == null || o.getCustomerName().isBlank() ? "Khách lẻ" : o.getCustomerName(),
                    ts == null ? null : ts.toLocalDateTime(),
                    o.getTotalAmount(),
                    nz(o.getPaymentMethod()), nz(o.getPaymentStatus()), nz(o.getOrderStatus())));
        }
        return rows;
    }

    private List<OrderLine> fetchLines(int orderId) {
        List<OrderLine> rows = new ArrayList<>();
        for (OrderDetailItemDTO d : bus().getOrderItems(orderId)) {
            rows.add(new OrderLine(
                    productLabel(d.getProductName(), d.getStorage(), d.getColor()),
                    d.getQuantity(),
                    d.getUnitPrice() == null ? BigDecimal.ZERO : d.getUnitPrice(),
                    d.getDiscountPercent() == null ? BigDecimal.ZERO : d.getDiscountPercent(),
                    d.getSubtotal() == null ? BigDecimal.ZERO : d.getSubtotal()));
        }
        return rows;
    }

    /** Quyền thật: process_orders (BUS cũng kiểm tra lại khi cập nhật). */
    private boolean canProcessOrders() {
        return SessionManager.hasPermission(PermissionNames.PROCESS_ORDERS);
    }

    private static String productLabel(String name, String storage, String color) {
        StringBuilder sb = new StringBuilder(nz(name));
        if (storage != null && !storage.isBlank()) sb.append(' ').append(storage);
        if (color != null && !color.isBlank()) sb.append(" - ").append(color);
        return sb.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}

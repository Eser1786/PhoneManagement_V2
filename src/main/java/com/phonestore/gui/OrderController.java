package com.phonestore.gui;

import com.phonestore.util.FormatUtil;
import com.phonestore.util.PermissionNames;
import com.phonestore.util.SessionManager;
import com.phonestore.exception.BusinessException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Function;

/**
 * Màn hình Quản lý đơn hàng (Order.fxml): danh sách, lọc, xem chi tiết,
 * cập nhật trạng thái, xem trước/in hóa đơn.
 *
 * Quy ước 3 lớp của nhóm: GUI chỉ gọi BUS. OrderBUS hiện CHƯA có hàm đọc đơn,
 * nên dữ liệu lấy từ khối "KẾT NỐI BUS" ở cuối file (dữ liệu mẫu, có TODO).
 * Khi BE xong, chỉ sửa các hàm trong khối đó.
 */
public class OrderController implements Initializable {

    private static final String ALL_STATUS = "Tất cả trạng thái";
    /** Danh sách trạng thái đề xuất - cần nhóm thống nhất (hiện DB chỉ có "Chờ xử lý"). */
    private static final List<String> STATUSES = List.of("Chờ xử lý", "Đang giao", "Hoàn thành", "Đã hủy");

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
    private String loadError;
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

    private void reload() {
        loadError = null;
        allOrders.setAll(loadOrders());
        table.setPlaceholder(new Label(loadError != null
                ? loadError
                : "Không tìm thấy đơn hàng phù hợp. Thử đổi từ khóa hoặc xóa bộ lọc."));
        refresh();
    }

    private void setupColumns() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        for (TableColumn<OrderRow, OrderRow> col :
                List.of(colId, colCustomer, colDate, colTotal, colMethod, colStatus, colActions)) {
            col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        }
        colId.setCellFactory(c -> textCell(r -> "DH" + r.id()));
        colCustomer.setCellFactory(c -> textCell(OrderRow::customer));
        colDate.setCellFactory(c -> textCell(r -> FormatUtil.formatDateTime(r.date())));
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
            String hay = normalize("DH" + r.id() + " " + r.customer());
            if (!hay.contains(kw)) return false;
        }
        String st = statusFilter.getValue();
        if (st != null && !st.equals(ALL_STATUS) && !st.equals(r.status())) return false;

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
        Label badge = statusBadge(order.status());
        HBox head = new HBox(12, name, badge);
        head.setAlignment(Pos.CENTER_LEFT);

        GridPane info = new GridPane();
        info.setHgap(24);
        info.setVgap(8);
        addInfo(info, 0, "Khách hàng", order.customer());
        addInfo(info, 1, "Ngày đặt", FormatUtil.formatDateTime(order.date()));
        addInfo(info, 2, "Thanh toán", order.method() + " (" + order.payStatus() + ")");
        addInfo(info, 3, "Tổng tiền", formatMoney(order.total()));

        List<OrderLine> lines = loadOrderLines(order.id());
        TableView<OrderLine> items = new TableView<>(FXCollections.observableArrayList(lines));
        items.getStyleClass().add("ord-table");
        items.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        items.setPlaceholder(new Label("Đơn này chưa có chi tiết sản phẩm"));
        items.setPrefHeight(200);
        items.getColumns().add(lineColumn("SẢN PHẨM", 240, OrderLine::product));
        items.getColumns().add(lineColumn("SL", 50, l -> String.valueOf(l.qty())));
        items.getColumns().add(lineColumn("ĐƠN GIÁ", 110, l -> formatMoney(l.unitPrice())));
        items.getColumns().add(lineColumn("GIẢM", 60, l -> l.discountPercent().stripTrailingZeros().toPlainString() + "%"));
        items.getColumns().add(lineColumn("THÀNH TIỀN", 120, l -> formatMoney(l.subtotal())));

        // Cập nhật trạng thái
        ComboBox<String> statusBox = new ComboBox<>(FXCollections.observableArrayList(STATUSES));
        statusBox.setValue(order.status());
        Button save = new Button("Lưu trạng thái");
        save.getStyleClass().add("ord-primary-btn");
        Button print = new Button("In hóa đơn");
        print.getStyleClass().add("ord-outline-btn");
        Label msg = new Label();

        boolean canEdit = canProcessOrders();
        statusBox.setDisable(!canEdit);
        save.setDisable(!canEdit);
        if (!canEdit) {
            msg.setText("Bạn không có quyền cập nhật trạng thái đơn hàng.");
            msg.getStyleClass().add("ord-msg-err");
        }

        save.setOnAction(e -> {
            msg.getStyleClass().removeAll("ord-msg-ok", "ord-msg-err");
            if (!canProcessOrders()) {
                msg.setText("Bạn không có quyền cập nhật trạng thái đơn hàng.");
                msg.getStyleClass().add("ord-msg-err");
                return;
            }
            try {
                OrderRow updated = changeStatus(order, statusBox.getValue());
                allOrders.set(allOrders.indexOf(order), updated);
                refresh();
                badge.setText(updated.status());
                badge.getStyleClass().removeIf(s -> s.startsWith("ord-badge-") && !s.equals("ord-badge"));
                badge.getStyleClass().add(badgeClass(updated.status()));
                msg.setText("Đã cập nhật trạng thái.");
                msg.getStyleClass().add("ord-msg-ok");
            } catch (BusinessException ex) {
                msg.setText(ex.getMessage());
                msg.getStyleClass().add("ord-msg-err");
            } catch (RuntimeException ex) {
                msg.setText("Không cập nhật được trạng thái. Vui lòng thử lại.");
                msg.getStyleClass().add("ord-msg-err");
            }
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
    //  Xem trước hóa đơn (chưa có thư viện PDF nên dùng văn bản + sao chép)
    //  TODO: nếu nhóm thêm OpenPDF vào build.gradle thì xuất PDF ở đây.
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
        sb.append("Ngày       : ").append(FormatUtil.formatDateTime(o.date())).append('\n');
        sb.append("Khách hàng : ").append(o.customer()).append('\n');
        sb.append("Thanh toán : ").append(o.method()).append(" - ").append(o.payStatus()).append('\n');
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
        return FormatUtil.formatCurrency(amount);
    }

    // =====================================================================
    //  KẾT NỐI BUS  ←  thay bằng OrderBUS khi BE có
    //
    //  Cần BE bổ sung (OrderDAO/OrderBUS):
    //   - List<OrderDTO> getAllOrders()  (hoặc searchOrders(status, from, to, keyword))
    //   - List<OrderDetailDTO> getOrderDetails(int orderId)  (kèm tên sản phẩm)
    //   - boolean updateOrderStatus(int orderId, String status)
    //   - tên khách: join Orders -> Customers -> Users
    // =====================================================================
    public record OrderRow(int id, String customer, LocalDateTime date, BigDecimal total,
                           String method, String payStatus, String status) { }

    public record OrderLine(String product, int qty, BigDecimal unitPrice,
                            BigDecimal discountPercent, BigDecimal subtotal) { }

    private static BigDecimal vnd(long v) {
        return BigDecimal.valueOf(v);
    }

    /** TODO: gọi OrderBUS (bắt BusinessException -> gán loadError). */
    private List<OrderRow> loadOrders() {
        String[] names = {"Nguyễn Văn An", "Trần Thị Bích", "Lê Hoàng Cường", "Phạm Ngọc Dung", "Võ Quốc Em", "Đặng Minh Phúc"};
        String[] methods = {"Tiền mặt", "Chuyển khoản", "Thẻ"};
        long[] totals = {29_990_000, 14_490_000, 8_990_000, 33_990_000, 6_490_000, 12_990_000, 22_490_000};
        List<OrderRow> rows = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            String status = STATUSES.get(i % STATUSES.size());
            rows.add(new OrderRow(1018 - i, names[i % names.length],
                    LocalDateTime.now().minusDays(i / 3).minusHours(i),
                    vnd(totals[i % totals.length]), methods[i % methods.length],
                    status.equals("Hoàn thành") ? "Đã thanh toán" : "Chưa thanh toán", status));
        }
        return rows;
    }

    /** TODO: gọi OrderBUS.getOrderDetails(orderId) rồi map sang OrderLine. */
    private List<OrderLine> loadOrderLines(int orderId) {
        return List.of(
                new OrderLine("iPhone 15 Pro Max 256GB - Titan tự nhiên", 1, vnd(29_990_000), BigDecimal.ZERO, vnd(29_990_000)),
                new OrderLine("Ốp lưng chính hãng", 2, vnd(250_000), BigDecimal.valueOf(10), vnd(450_000)));
    }

    /** TODO: gọi OrderBUS.updateOrderStatus(...) (BUS tự kiểm tra quyền process_orders). */
    private OrderRow changeStatus(OrderRow o, String newStatus) {
        if (newStatus == null || newStatus.equals(o.status())) {
            throw new BusinessException("Trạng thái mới trùng với trạng thái hiện tại.");
        }
        return new OrderRow(o.id(), o.customer(), o.date(), o.total(), o.method(), o.payStatus(), newStatus);
    }

    /**
     * The BUS enforces this permission for mutations; the UI also gates the controls.
     */
    private boolean canProcessOrders() {
        return SessionManager.hasPermission(PermissionNames.PROCESS_ORDERS);
    }
}

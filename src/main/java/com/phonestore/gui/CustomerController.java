package com.phonestore.gui;

import com.phonestore.bus.CustomerBUS;
import com.phonestore.dto.CustomerDTO;
import com.phonestore.dto.UserDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.FormatUtil;
import com.phonestore.util.PermissionNames;
import com.phonestore.util.SessionManager;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.concurrent.Task;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.net.URL;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Màn hình Quản lý khách hàng (Customer.fxml).
 *
 * Quy ước 3 lớp của nhóm: GUI chỉ gọi BUS. Controller này gọi CustomerBUS
 * (xem khối "KẾT NỐI BUS" ở cuối file). Lịch sử mua hàng chưa có API nên đang để trống.
 */
public class CustomerController implements Initializable {

    private static final int PAGE_SIZE = 7;
    private static final String ALL_CITIES = "Tất cả thành phố";
    private static final String ALL_LEVELS = "Tất cả hạng";

    private static final Pattern PHONE = Pattern.compile("^0\\d{9}$");
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    // ---------- Thành phần trong Customer.fxml ----------
    @FXML private TextField searchField;
    @FXML private ComboBox<String> cityFilter;
    @FXML private ComboBox<String> levelFilter;
    @FXML private Label countLabel;
    @FXML private Label pageInfoLabel;
    @FXML private HBox pager;
    @FXML private Button addCustomerButton;

    @FXML private TableView<CustomerRow> table;
    @FXML private TableColumn<CustomerRow, CustomerRow> colIndex;
    @FXML private TableColumn<CustomerRow, CustomerRow> colName;
    @FXML private TableColumn<CustomerRow, CustomerRow> colPhone;
    @FXML private TableColumn<CustomerRow, CustomerRow> colEmail;
    @FXML private TableColumn<CustomerRow, CustomerRow> colCity;
    @FXML private TableColumn<CustomerRow, CustomerRow> colLevel;
    @FXML private TableColumn<CustomerRow, CustomerRow> colSpent;
    @FXML private TableColumn<CustomerRow, CustomerRow> colActions;

    private final ObservableList<CustomerRow> allCustomers = FXCollections.observableArrayList();
    private CustomerBUS customerBUS;
    private String loadError;
    private int currentPage = 1;
    private boolean updatingFilters = false;

    // =====================================================================
    //  Khởi tạo
    // =====================================================================
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupColumns();
        setManagementActionsVisible(SessionManager.hasPermission(PermissionNames.MANAGE_CUSTOMERS));
        rebuildFilters();

        searchField.textProperty().addListener((o, a, b) -> goToFirstPageAndRefresh());
        cityFilter.valueProperty().addListener((o, a, b) -> { if (!updatingFilters) goToFirstPageAndRefresh(); });
        levelFilter.valueProperty().addListener((o, a, b) -> { if (!updatingFilters) goToFirstPageAndRefresh(); });

        refresh();
        loadCustomersAsync();
    }

    private void setupColumns() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(loadError != null
                ? loadError
                : "Không tìm thấy khách hàng phù hợp. Thử đổi từ khóa hoặc xóa bộ lọc."));

        // Mỗi cột nhận nguyên dòng dữ liệu để cell tự vẽ.
        for (TableColumn<CustomerRow, CustomerRow> col :
                List.of(colIndex, colName, colPhone, colEmail, colCity, colLevel, colSpent, colActions)) {
            col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        }

        // STT chạy tiếp qua các trang
        colIndex.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(CustomerRow item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.valueOf((currentPage - 1) * PAGE_SIZE + getIndex() + 1));
            }
        });

        colName.setCellFactory(c -> textCell(CustomerRow::fullName));
        colPhone.setCellFactory(c -> textCell(CustomerRow::phone));
        colEmail.setCellFactory(c -> textCell(CustomerRow::email));
        colCity.setCellFactory(c -> textCell(CustomerRow::city));
        colSpent.setCellFactory(c -> textCell(r -> formatMoney(r.totalSpent())));

        colLevel.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(CustomerRow item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : levelBadge(item.level()));
            }
        });

        colActions.setCellFactory(c -> new TableCell<>() {
            private final Button view = new Button("Xem");
            private final Button edit = new Button("Sửa");
            private final HBox box = new HBox(6, view, edit);
            {
                view.getStyleClass().add("cus-btn-outline");
                edit.getStyleClass().add("cus-btn-outline");
                boolean canManage = SessionManager.hasPermission(PermissionNames.MANAGE_CUSTOMERS);
                edit.setVisible(canManage);
                edit.setManaged(canManage);
                box.setAlignment(Pos.CENTER_LEFT);
                view.setOnAction(e -> showDetail(getItem()));
                edit.setOnAction(e -> showForm(getItem()));
            }
            @Override protected void updateItem(CustomerRow item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : box);
            }
        });
    }

    private TableCell<CustomerRow, CustomerRow> textCell(Function<CustomerRow, String> getter) {
        return new TableCell<>() {
            @Override protected void updateItem(CustomerRow item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : getter.apply(item));
            }
        };
    }

    // =====================================================================
    //  Lọc + phân trang
    // =====================================================================
    private void rebuildFilters() {
        String keepCity = cityFilter.getValue();
        String keepLevel = levelFilter.getValue();
        updatingFilters = true;

        cityFilter.getItems().setAll(ALL_CITIES);
        cityFilter.getItems().addAll(allCustomers.stream().map(CustomerRow::city).distinct().sorted().toList());
        levelFilter.getItems().setAll(ALL_LEVELS);
        levelFilter.getItems().addAll(allCustomers.stream().map(CustomerRow::level).distinct().sorted().toList());

        cityFilter.setValue(keepCity != null && cityFilter.getItems().contains(keepCity) ? keepCity : ALL_CITIES);
        levelFilter.setValue(keepLevel != null && levelFilter.getItems().contains(keepLevel) ? keepLevel : ALL_LEVELS);
        updatingFilters = false;
    }

    private void goToFirstPageAndRefresh() {
        currentPage = 1;
        refresh();
    }

    private boolean matches(CustomerRow r) {
        String keyword = normalize(searchField.getText());
        if (!keyword.isEmpty()) {
            String haystack = normalize(r.fullName() + " " + r.phone() + " " + r.email());
            if (!haystack.contains(keyword)) return false;
        }
        String city = cityFilter.getValue();
        if (city != null && !city.equals(ALL_CITIES) && !city.equals(r.city())) return false;
        String level = levelFilter.getValue();
        return level == null || level.equals(ALL_LEVELS) || level.equals(r.level());
    }

    private void refresh() {
        List<CustomerRow> filtered = allCustomers.stream().filter(this::matches).toList();
        int total = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        currentPage = Math.min(currentPage, totalPages);

        int from = (currentPage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, total);
        table.setItems(FXCollections.observableArrayList(filtered.subList(from, to)));

        countLabel.setText(total + " khách hàng");
        pageInfoLabel.setText(total == 0
                ? "Không có khách hàng nào"
                : "Hiển thị " + (from + 1) + "–" + to + " trong tổng số " + total + " khách hàng");
        buildPager(totalPages);
    }

    private void buildPager(int totalPages) {
        pager.getChildren().clear();

        Button prev = pageButton("‹", false, () -> { currentPage--; refresh(); });
        prev.setDisable(currentPage <= 1);
        pager.getChildren().add(prev);

        for (int p = 1; p <= totalPages; p++) {
            final int page = p;
            pager.getChildren().add(pageButton(String.valueOf(p), p == currentPage, () -> { currentPage = page; refresh(); }));
        }

        Button next = pageButton("›", false, () -> { currentPage++; refresh(); });
        next.setDisable(currentPage >= totalPages);
        pager.getChildren().add(next);
    }

    private Button pageButton(String text, boolean active, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("cus-page-btn");
        if (active) b.getStyleClass().add("cus-page-btn-active");
        b.setOnAction(e -> action.run());
        return b;
    }

    @FXML
    private void onResetFilters() {
        updatingFilters = true;
        searchField.clear();
        cityFilter.setValue(ALL_CITIES);
        levelFilter.setValue(ALL_LEVELS);
        updatingFilters = false;
        goToFirstPageAndRefresh();
    }

    @FXML
    private void onAddCustomer() {
        showForm(null);
    }

    // =====================================================================
    //  Hộp thoại: xem chi tiết + lịch sử mua
    // =====================================================================
    private void showDetail(CustomerRow c) {
        Dialog<Void> dlg = newDialog("Chi tiết khách hàng");
        DialogPane pane = dlg.getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);

        HBox head = new HBox(12, boldLabel(c.fullName(), "cus-dialog-name"), levelBadge(c.level()));
        head.setAlignment(Pos.CENTER_LEFT);

        GridPane info = new GridPane();
        info.setHgap(24);
        info.setVgap(8);
        addInfo(info, 0, "Số điện thoại", c.phone());
        addInfo(info, 1, "Email", c.email());
        addInfo(info, 2, "Địa chỉ", c.address());
        addInfo(info, 3, "Thành phố", c.city());
        addInfo(info, 4, "Ngày tham gia", FormatUtil.formatDate(c.joinedDate()));
        addInfo(info, 5, "Tổng chi tiêu", formatMoney(c.totalSpent()));

        TableView<OrderRow> history = new TableView<>(FXCollections.observableArrayList(loadOrderHistory(c.id())));
        history.getStyleClass().add("cus-table");
        history.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        history.setPlaceholder(new Label("Chưa có dữ liệu đơn hàng (đang chờ API lấy đơn theo khách)"));
        history.setPrefHeight(230);
        history.getColumns().add(historyColumn("MÃ ĐƠN", 110, r -> "DH" + r.orderId()));
        history.getColumns().add(historyColumn("NGÀY MUA", 110, r -> FormatUtil.formatDate(r.date())));
        history.getColumns().add(historyColumn("THANH TOÁN", 120, OrderRow::paymentMethod));
        history.getColumns().add(historyColumn("TRẠNG THÁI", 120, OrderRow::orderStatus));
        history.getColumns().add(historyColumn("TỔNG TIỀN", 130, r -> formatMoney(r.total())));

        VBox content = new VBox(16, head, info, boldLabel("Lịch sử mua hàng", "cus-section-title"), history);
        content.setPrefWidth(640);
        pane.setContent(content);
        dlg.showAndWait();
    }

    private TableColumn<OrderRow, String> historyColumn(String title, double width, Function<OrderRow, String> getter) {
        TableColumn<OrderRow, String> col = new TableColumn<>(title);
        col.setPrefWidth(width);
        col.setSortable(false);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(getter.apply(c.getValue())));
        return col;
    }

    private void addInfo(GridPane grid, int row, String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add("cus-info-label");
        Label v = new Label(value);
        v.getStyleClass().add("cus-info-value");
        grid.add(l, 0, row);
        grid.add(v, 1, row);
    }

    // =====================================================================
    //  Hộp thoại: thêm / sửa khách hàng
    // =====================================================================
    private void showForm(CustomerRow existing) {
        boolean isEdit = existing != null;
        Dialog<ButtonType> dlg = newDialog(isEdit ? "Sửa khách hàng" : "Thêm khách hàng");
        DialogPane pane = dlg.getDialogPane();

        ButtonType saveType = new ButtonType(isEdit ? "Lưu thay đổi" : "Thêm khách hàng", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField name = new TextField(isEdit ? existing.fullName() : "");
        TextField phone = new TextField(isEdit ? existing.phone() : "");
        TextField email = new TextField(isEdit ? existing.email() : "");
        TextField city = new TextField(isEdit ? existing.city() : "");
        TextField address = new TextField(isEdit ? existing.address() : "");
        name.setPromptText("Ví dụ: Nguyễn Văn An");
        phone.setPromptText("10 chữ số, bắt đầu bằng 0");
        email.setPromptText("ten@example.com");
        city.setPromptText("Ví dụ: TP. Hồ Chí Minh");
        address.setPromptText("Số nhà, đường, phường, quận");

        Label error = new Label();
        error.getStyleClass().add("cus-error");
        error.setWrapText(true);
        error.managedProperty().bind(error.visibleProperty());
        error.setVisible(false);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.setPadding(new Insets(4, 0, 0, 0));
        addField(form, 0, "Họ tên", name);
        addField(form, 1, "Số điện thoại", phone);
        addField(form, 2, "Email", email);
        addField(form, 3, "Địa chỉ", address);
        addField(form, 4, "Thành phố", city);

        VBox content = new VBox(12, form, error);
        content.setPrefWidth(420);
        pane.setContent(content);

        // Keep the dialog open while the BUS operation runs off the JavaFX thread.
        final CustomerRow[] saved = new CustomerRow[1];
        final boolean[] saving = {false};
        Button saveBtn = (Button) pane.lookupButton(saveType);
        Button cancelBtn = (Button) pane.lookupButton(ButtonType.CANCEL);
        dlg.setOnCloseRequest(event -> {
            if (saving[0]) event.consume();
        });
        saveBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            String validationError = validate(name.getText(), phone.getText(), email.getText(),
                    address.getText(), city.getText());
            if (validationError != null) {
                error.setText(validationError);
                error.setVisible(true);
                ev.consume();
                return;
            }

            ev.consume();
            saving[0] = true;
            saveBtn.setDisable(true);
            cancelBtn.setDisable(true);
            CustomerBUS customerService = bus();
            String fullName = name.getText().trim();
            String phoneNumber = phone.getText().trim();
            String emailAddress = email.getText().trim();
            String customerAddress = address.getText().trim();
            String customerCity = city.getText().trim();
            Task<CustomerRow> task = new Task<>() {
                @Override
                protected CustomerRow call() {
                    return saveCustomer(customerService, existing, fullName, phoneNumber,
                            emailAddress, customerAddress, customerCity);
                }
            };
            task.setOnSucceeded(event -> {
                saving[0] = false;
                saved[0] = task.getValue();
                dlg.setResult(saveType);
                dlg.close();
            });
            task.setOnFailed(event -> {
                saving[0] = false;
                saveBtn.setDisable(false);
                cancelBtn.setDisable(false);
                Throwable failure = task.getException();
                if (failure instanceof BusinessException) {
                    error.setText(failure.getMessage());
                } else {
                    System.err.println("Không lưu được khách hàng: " + failure);
                    failure.printStackTrace();
                    error.setText(DB_ERROR);
                }
                error.setVisible(true);
            });
            Thread worker = new Thread(task, "customer-save");
            worker.setDaemon(true);
            worker.start();
        });

        dlg.showAndWait().filter(b -> b == saveType && saved[0] != null).ifPresent(b -> {
            if (isEdit) {
                allCustomers.set(allCustomers.indexOf(existing), saved[0]);
            } else {
                allCustomers.add(saved[0]);
            }
            rebuildFilters();
            refresh();
        });
    }

    private void addField(GridPane grid, int row, String label, TextField field) {
        Label l = new Label(label);
        l.getStyleClass().add("cus-field-label");
        GridPane.setHgrow(field, Priority.ALWAYS);
        field.setMaxWidth(Double.MAX_VALUE);
        grid.add(l, 0, row);
        grid.add(field, 1, row);
    }

    /** Kiểm tra nhanh để báo lỗi sớm. Kiểm tra đầy đủ (trùng SĐT/email...) do CustomerBUS làm. */
    private String validate(String name, String phone, String email, String address, String city) {
        if (name.isBlank()) return "Vui lòng nhập họ tên.";
        if (!PHONE.matcher(phone.trim()).matches()) return "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.";
        if (!EMAIL.matcher(email.trim()).matches()) return "Email chưa đúng định dạng, ví dụ ten@example.com.";
        if (address.isBlank()) return "Vui lòng nhập địa chỉ.";
        if (city.isBlank()) return "Vui lòng nhập thành phố.";
        return null;
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
        pane.getStylesheets().add(getClass().getResource("/css/customer.css").toExternalForm());
        pane.getStyleClass().add("cus-dialog");
        return dlg;
    }

    private Label boldLabel(String text, String styleClass) {
        Label l = new Label(text);
        l.getStyleClass().add(styleClass);
        return l;
    }

    private Label levelBadge(String level) {
        Label badge = new Label(level);
        badge.getStyleClass().addAll("cus-badge", badgeClass(level));
        return badge;
    }

    private String badgeClass(String level) {
        String s = normalize(level);
        if (s.contains("vip") || s.contains("platinum") || s.contains("diamond")) return "cus-badge-vip";
        if (s.contains("vang") || s.contains("gold")) return "cus-badge-gold";
        if (s.contains("bac") || s.contains("silver")) return "cus-badge-silver";
        if (s.contains("dong") || s.contains("bronze")) return "cus-badge-bronze";
        return "cus-badge-default";
    }

    /** Bỏ dấu + chữ thường để tìm kiếm "tran" vẫn ra "Trân". */
    private static String normalize(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd');
        return t.trim();
    }

    private static String formatMoney(BigDecimal amount) {
        return FormatUtil.formatCurrency(amount);
    }

    // =====================================================================
    //  KẾT NỐI BUS
    // =====================================================================
    private static final String DB_ERROR =
            "Không kết nối được cơ sở dữ liệu. Hãy chạy \"docker compose up -d\" rồi mở lại màn hình.";

    /** Dòng hiển thị trên bảng: ghép CustomerDTO (Customers) với UserDTO (Users). */
    public record CustomerRow(int id, String fullName, String phone, String email,
                              String address, String city, String level,
                              BigDecimal totalSpent, LocalDate joinedDate) { }

    /** Một đơn trong lịch sử mua. Khớp các field của OrderDTO. Chờ API lấy đơn theo khách. */
    public record OrderRow(int orderId, LocalDate date, String paymentMethod,
                           String paymentStatus, String orderStatus, BigDecimal total) { }

    private CustomerBUS bus() {
        if (customerBUS == null) customerBUS = new CustomerBUS();
        return customerBUS;
    }

    private void loadCustomersAsync() {
        table.setPlaceholder(new Label("Đang tải danh sách khách hàng..."));
        CustomerBUS customerService = bus();
        Task<List<CustomerRow>> task = new Task<>() {
            @Override
            protected List<CustomerRow> call() {
                List<CustomerRow> rows = new ArrayList<>();
                for (CustomerDTO customer : customerService.getAllCustomers()) {
                    rows.add(toRow(customer, customerService.getUserOfCustomer(customer)));
                }
                return rows;
            }
        };
        task.setOnSucceeded(event -> {
            loadError = null;
            allCustomers.setAll(task.getValue());
            table.setPlaceholder(new Label("Không tìm thấy khách hàng phù hợp. Thử đổi từ khóa hoặc xóa bộ lọc."));
            rebuildFilters();
            refresh();
        });
        task.setOnFailed(event -> {
            Throwable failure = task.getException();
            loadError = failure instanceof BusinessException ? failure.getMessage() : DB_ERROR;
            if (!(failure instanceof BusinessException)) {
                System.err.println("Không tải được khách hàng: " + failure);
                failure.printStackTrace();
            }
            allCustomers.clear();
            table.setPlaceholder(new Label(loadError));
            rebuildFilters();
            refresh();
        });
        Thread worker = new Thread(task, "customer-load");
        worker.setDaemon(true);
        worker.start();
    }

    /** TODO: chưa có hàm lấy đơn theo khách trong OrderDAO/OrderBUS. Khi có thì map OrderDTO -> OrderRow ở đây. */
    private List<OrderRow> loadOrderHistory(int customerId) {
        return List.of();
    }

    /** existing == null: thêm mới (tạo cả Users + Customers); ngược lại: cập nhật. */
    private CustomerRow saveCustomer(CustomerBUS customerService, CustomerRow existing,
                                     String name, String phone, String email,
                                     String address, String city) {
        CustomerDTO dto = existing == null
                ? customerService.addCustomerWithUserInfo(name, phone, email, address, city)
                : customerService.updateCustomerWithUserInfo(existing.id(), name, phone, email, address, city);
        return toRow(dto, customerService.getUserOfCustomer(dto));
    }

    private void setManagementActionsVisible(boolean visible) {
        addCustomerButton.setVisible(visible);
        addCustomerButton.setManaged(visible);
    }

    private static CustomerRow toRow(CustomerDTO c, UserDTO u) {
        return new CustomerRow(
                c.getCustomerId(),
                u != null && u.getFullName() != null ? u.getFullName() : "(chưa liên kết tài khoản)",
                u != null && u.getPhone() != null ? u.getPhone() : "",
                u != null && u.getEmail() != null ? u.getEmail() : "",
                nz(c.getAddress()), nz(c.getCity()), nz(c.getMembershipLevel()),
                c.getTotalSpent() == null ? BigDecimal.ZERO : c.getTotalSpent(),
                c.getCreatedAt() == null ? null : c.getCreatedAt().toLocalDate());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
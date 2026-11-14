package com.phonestore.gui;

import com.phonestore.bus.OrderBUS;
import com.phonestore.bus.ReportBUS;
import com.phonestore.dto.DashboardSummaryDTO;
import com.phonestore.dto.OrderFilter;
import com.phonestore.dto.OrderListItemDTO;
import com.phonestore.dto.ProductListItemDTO;
import com.phonestore.dto.RevenuePointDTO;
import com.phonestore.dto.TopProductDTO;
import com.phonestore.exception.BusinessException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;

import java.math.BigDecimal;
import java.net.URL;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Function;

/**
 * Màn hình Tổng quan (Dashboard.fxml).
 * Dữ liệu từ ReportBUS (getDashboardSummary, getRevenueByDay, getTopProducts, getLowStock)
 * và OrderBUS (searchOrders cho đơn gần đây). Chạy trong Task nền.
 * Lưu ý: ReportBUS yêu cầu quyền view_reports.
 */
public class DashboardController implements Initializable {

    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final String DB_ERROR =
            "Không kết nối được cơ sở dữ liệu. Hãy chạy \"docker compose up -d\" rồi bấm Làm mới.";

    private static final NumberFormat MONEY = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static { MONEY.setMaximumFractionDigits(0); }

    // ---------- Thành phần trong Dashboard.fxml ----------
    @FXML private Label dateLabel;
    @FXML private Label revenueValue;
    @FXML private Label revenueHint;
    @FXML private Label ordersValue;
    @FXML private Label ordersHint;
    @FXML private Label customersValue;
    @FXML private Label customersHint;
    @FXML private Label lowStockValue;
    @FXML private Label lowStockHint;

    @FXML private LineChart<String, Number> revenueChart;
    @FXML private BarChart<Number, String> topChart;

    @FXML private TableView<LowStockRow> lowStockTable;
    @FXML private TableColumn<LowStockRow, LowStockRow> colLowName;
    @FXML private TableColumn<LowStockRow, LowStockRow> colLowStock;
    @FXML private TableColumn<LowStockRow, LowStockRow> colLowStatus;

    @FXML private TableView<RecentOrderRow> recentTable;
    @FXML private TableColumn<RecentOrderRow, RecentOrderRow> colOrderId;
    @FXML private TableColumn<RecentOrderRow, RecentOrderRow> colOrderCustomer;
    @FXML private TableColumn<RecentOrderRow, RecentOrderRow> colOrderTotal;
    @FXML private TableColumn<RecentOrderRow, RecentOrderRow> colOrderStatus;

    // =====================================================================
    //  Khởi tạo
    // =====================================================================
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTables();
        // BE chưa có "khách mới theo ngày" -> thẻ này hiển thị TỔNG khách hàng.
        relabelCard(customersValue, "TỔNG KHÁCH HÀNG");
        customersHint.setText("Khách hàng đã đăng ký");
        lowStockHint.setText("Tồn kho từ " + LOW_STOCK_THRESHOLD + " trở xuống");
        refresh();
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    private void refresh() {
        dateLabel.setText("Đang tải dữ liệu...");
        Task<DashboardData> task = new Task<>() {
            @Override protected DashboardData call() {
                return loadData();
            }
        };
        task.setOnSucceeded(e -> {
            DashboardData data = task.getValue();
            dateLabel.setText("Tình hình kinh doanh hôm nay, " + LocalDate.now().format(FULL));
            fillCards(data);
            fillRevenueChart(data.revenueLast7Days());
            fillTopChart(data.topProducts());
            lowStockTable.getItems().setAll(data.lowStock());
            recentTable.getItems().setAll(data.recentOrders());
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            dateLabel.setText(ex instanceof BusinessException && ex.getMessage() != null ? ex.getMessage() : DB_ERROR);
        });
        Thread t = new Thread(task, "dashboard-loader");
        t.setDaemon(true);
        t.start();
    }

    /** Đổi chữ tiêu đề của thẻ chứa label giá trị (không cần sửa FXML). */
    private void relabelCard(Label valueLabel, String title) {
        if (valueLabel.getParent() instanceof Pane card) {
            for (Node n : card.getChildren()) {
                if (n instanceof Label l && l.getStyleClass().contains("dash-card-title")) {
                    l.setText(title);
                }
            }
        }
    }

    // =====================================================================
    //  Thẻ số liệu
    // =====================================================================
    private void fillCards(DashboardData d) {
        revenueValue.setText(formatMoney(d.revenueToday()));
        setHint(revenueHint, d.revenueToday().doubleValue(), d.revenueYesterday().doubleValue());

        ordersValue.setText(String.valueOf(d.ordersToday()));
        setHint(ordersHint, d.ordersToday(), d.ordersYesterday());

        customersValue.setText(String.valueOf(d.totalCustomers()));

        lowStockValue.setText(String.valueOf(d.lowStockCount()));
        lowStockValue.getStyleClass().remove("dash-card-value-danger");
        if (d.lowStockCount() > 0) lowStockValue.getStyleClass().add("dash-card-value-danger");
    }

    /** Ghi "+12,5% so với hôm qua" (xanh) hoặc "-8,0% ..." (đỏ). */
    private void setHint(Label label, double today, double yesterday) {
        label.getStyleClass().removeAll("dash-hint-up", "dash-hint-down");
        if (yesterday <= 0) {
            label.setText("Chưa có số liệu hôm qua để so sánh");
            return;
        }
        double pct = (today - yesterday) / yesterday * 100.0;
        label.setText(String.format(Locale.forLanguageTag("vi-VN"), "%+.1f%% so với hôm qua", pct));
        label.getStyleClass().add(pct >= 0 ? "dash-hint-up" : "dash-hint-down");
    }

    // =====================================================================
    //  Biểu đồ
    // =====================================================================
    private void fillRevenueChart(List<DayRevenue> days) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (DayRevenue d : days) {
            series.getData().add(new XYChart.Data<>(d.date().format(DAY), d.revenue().doubleValue() / 1_000_000.0));
        }
        revenueChart.getData().setAll(List.of(series));
    }

    private void fillTopChart(List<TopProduct> products) {
        XYChart.Series<Number, String> series = new XYChart.Series<>();
        // BarChart ngang vẽ mục đầu tiên ở dưới cùng, nên đảo thứ tự để top 1 nằm trên cùng
        List<TopProduct> reversed = new ArrayList<>(products);
        Collections.reverse(reversed);
        for (TopProduct p : reversed) {
            series.getData().add(new XYChart.Data<>(p.quantity(), p.name()));
        }
        topChart.getData().setAll(List.of(series));
    }

    // =====================================================================
    //  Bảng
    // =====================================================================
    private void setupTables() {
        lowStockTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        lowStockTable.setPlaceholder(new Label("Không có sản phẩm nào sắp hết hàng"));
        textColumn(colLowName, LowStockRow::name);
        textColumn(colLowStock, r -> String.valueOf(r.stock()));
        badgeColumn(colLowStatus,
                r -> r.stock() <= 0 ? "Hết hàng" : "Sắp hết",
                r -> r.stock() <= 0 ? "dash-badge-red" : "dash-badge-yellow");

        recentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        recentTable.setPlaceholder(new Label("Chưa có đơn hàng nào"));
        textColumn(colOrderId, r -> "DH" + r.orderId());
        textColumn(colOrderCustomer, RecentOrderRow::customer);
        textColumn(colOrderTotal, r -> formatMoney(r.total()));
        badgeColumn(colOrderStatus, RecentOrderRow::status, r -> statusClass(r.status()));
    }

    private static <T> void textColumn(TableColumn<T, T> col, Function<T, String> getter) {
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<T>(c.getValue()));
        col.setCellFactory(c -> new TableCell<T, T>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : getter.apply(item));
            }
        });
    }

    private static <T> void badgeColumn(TableColumn<T, T> col, Function<T, String> text, Function<T, String> styleClass) {
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<T>(c.getValue()));
        col.setCellFactory(c -> new TableCell<T, T>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label badge = new Label(text.apply(item));
                badge.getStyleClass().addAll("dash-badge", styleClass.apply(item));
                setGraphic(badge);
            }
        });
    }

    /** Trạng thái thật của BE: Chờ xử lý / Đang giao / Hoàn thành / Đã hủy. */
    private static String statusClass(String status) {
        String s = normalize(status);
        if (s.contains("hoan thanh")) return "dash-badge-green";
        if (s.contains("huy")) return "dash-badge-red";
        return "dash-badge-yellow";
    }

    // =====================================================================
    //  Tiện ích
    // =====================================================================
    private static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .trim();
    }

    private static String formatMoney(BigDecimal amount) {
        return MONEY.format(amount == null ? BigDecimal.ZERO : amount) + " ₫";
    }

    // =====================================================================
    //  KẾT NỐI BUS
    // =====================================================================
    public record DayRevenue(LocalDate date, BigDecimal revenue) { }

    public record TopProduct(String name, int quantity) { }

    public record LowStockRow(String name, int stock) { }

    public record RecentOrderRow(int orderId, String customer, BigDecimal total, String status) { }

    public record DashboardData(BigDecimal revenueToday, BigDecimal revenueYesterday,
                                int ordersToday, int ordersYesterday,
                                int totalCustomers, int lowStockCount,
                                List<DayRevenue> revenueLast7Days,
                                List<TopProduct> topProducts,
                                List<LowStockRow> lowStock,
                                List<RecentOrderRow> recentOrders) { }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Chạy ở thread nền: KHÔNG chạm vào control JavaFX trong hàm này. */
    private DashboardData loadData() {
        ReportBUS report = new ReportBUS();
        OrderBUS orders = new OrderBUS();

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(6);

        DashboardSummaryDTO summary = report.getDashboardSummary();

        // BUS điền đủ các ngày (ngày không có đơn = 0), thứ tự tăng dần theo ngày.
        List<RevenuePointDTO> points = report.getRevenueByDay(from, today);
        List<DayRevenue> days = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            days.add(new DayRevenue(from.plusDays(i), nz(points.get(i).getRevenue())));
        }
        BigDecimal revenueYesterday = BigDecimal.ZERO;
        int ordersYesterday = 0;
        if (points.size() >= 2) {
            RevenuePointDTO y = points.get(points.size() - 2);
            revenueYesterday = nz(y.getRevenue());
            ordersYesterday = y.getOrderCount();
        }

        List<TopProduct> top = new ArrayList<>();
        for (TopProductDTO p : report.getTopProducts(from, today, 5)) {
            top.add(new TopProduct(label(p.getProductName(), p.getStorage(), p.getColor()), p.getQuantitySold()));
        }

        List<LowStockRow> low = new ArrayList<>();
        for (ProductListItemDTO p : report.getLowStock(LOW_STOCK_THRESHOLD)) {
            low.add(new LowStockRow(label(p.getProductName(), p.getStorage(), p.getColor()), p.getStockQuantity()));
        }

        // searchOrders trả đơn mới nhất trước; lấy 6 dòng đầu
        List<RecentOrderRow> recent = new ArrayList<>();
        for (OrderListItemDTO o : orders.searchOrders(new OrderFilter())) {
            if (recent.size() >= 6) break;
            String customer = o.getCustomerName() == null || o.getCustomerName().isBlank()
                    ? "Khách lẻ" : o.getCustomerName();
            recent.add(new RecentOrderRow(o.getOrderId(), customer, nz(o.getTotalAmount()),
                    o.getOrderStatus() == null ? "" : o.getOrderStatus()));
        }

        return new DashboardData(
                nz(summary.getRevenueToday()), revenueYesterday,
                summary.getOrdersToday(), ordersYesterday,
                summary.getTotalCustomers(), summary.getLowStockCount(),
                days, top, low, recent);
    }

    private static String label(String name, String storage, String color) {
        StringBuilder sb = new StringBuilder(name == null ? "" : name);
        if (storage != null && !storage.isBlank()) sb.append(' ').append(storage);
        if (color != null && !color.isBlank()) sb.append(" - ").append(color);
        return sb.toString();
    }
}

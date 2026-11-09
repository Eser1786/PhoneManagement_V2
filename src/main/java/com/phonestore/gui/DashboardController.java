package com.phonestore.gui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

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
 *
 * Quy ước 3 lớp của nhóm: GUI chỉ gọi BUS. Hiện chưa có ReportBUS/ReportDAO nên dữ liệu
 * lấy từ hàm loadData() ở cuối file (dữ liệu mẫu). Khi có ReportBUS, chỉ cần sửa hàm đó,
 * phần giao diện giữ nguyên.
 */
public class DashboardController implements Initializable {

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
        refresh();
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    private void refresh() {
        DashboardData data = loadData();
        dateLabel.setText("Tình hình kinh doanh hôm nay, " + LocalDate.now().format(FULL));
        fillCards(data);
        fillRevenueChart(data.revenueLast7Days());
        fillTopChart(data.topProducts());
        lowStockTable.getItems().setAll(data.lowStock());
        recentTable.getItems().setAll(data.recentOrders());
    }

    // =====================================================================
    //  Thẻ số liệu
    // =====================================================================
    private void fillCards(DashboardData d) {
        revenueValue.setText(formatMoney(d.revenueToday()));
        setHint(revenueHint, d.revenueToday().doubleValue(), d.revenueYesterday().doubleValue());

        ordersValue.setText(String.valueOf(d.ordersToday()));
        setHint(ordersHint, d.ordersToday(), d.ordersYesterday());

        customersValue.setText(String.valueOf(d.newCustomersToday()));

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
            // đổi sang triệu đồng cho trục dễ đọc
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

    /** Xanh = hoàn thành, đỏ = hủy, vàng = còn lại (chờ xử lý/đang giao). Chỉnh khi biết giá trị order_status thật. */
    private static String statusClass(String status) {
        String s = normalize(status);
        if (s.contains("hoan thanh") || s.contains("da giao")) return "dash-badge-green";
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
    //  DỮ LIỆU TẠM  ←  thay bằng ReportBUS khi BE có
    //
    //  Dự kiến ReportBUS cần cung cấp (xem tin nhắn gửi bạn BE):
    //   - tổng hợp theo ngày: doanh thu, số đơn, khách mới, số sản phẩm sắp hết (hôm nay + hôm qua)
    //   - doanh thu từng ngày trong khoảng [từ, đến]
    //   - top sản phẩm bán chạy trong khoảng ngày
    //   - danh sách sản phẩm sắp hết hàng
    //   - các đơn gần nhất kèm tên khách
    // =====================================================================

    public record DayRevenue(LocalDate date, BigDecimal revenue) { }

    public record TopProduct(String name, int quantity) { }

    public record LowStockRow(String name, int stock) { }

    public record RecentOrderRow(int orderId, String customer, BigDecimal total, String status) { }

    public record DashboardData(BigDecimal revenueToday, BigDecimal revenueYesterday,
                                int ordersToday, int ordersYesterday,
                                int newCustomersToday, int lowStockCount,
                                List<DayRevenue> revenueLast7Days,
                                List<TopProduct> topProducts,
                                List<LowStockRow> lowStock,
                                List<RecentOrderRow> recentOrders) { }

    private static BigDecimal vnd(long v) {
        return BigDecimal.valueOf(v);
    }

    /** TODO: gọi ReportBUS (và bắt BusinessException). */
    private DashboardData loadData() {
        LocalDate today = LocalDate.now();
        long[] revenue = {42_300_000, 55_800_000, 38_900_000, 71_200_000, 64_500_000, 79_900_000, 86_450_000};
        List<DayRevenue> days = new ArrayList<>();
        for (int i = 0; i < revenue.length; i++) {
            days.add(new DayRevenue(today.minusDays(revenue.length - 1 - i), vnd(revenue[i])));
        }

        return new DashboardData(
                vnd(86_450_000), vnd(79_900_000),
                14, 12,
                5, 7,
                days,
                List.of(
                        new TopProduct("iPhone 15 Pro Max 256GB", 18),
                        new TopProduct("Galaxy S24 Ultra 512GB", 14),
                        new TopProduct("Xiaomi 14 12GB/256GB", 11),
                        new TopProduct("OPPO Reno11 F 8GB/256GB", 9),
                        new TopProduct("vivo V30 12GB/256GB", 7)),
                List.of(
                        new LowStockRow("iPhone 15 Pro Max 256GB - Titan tự nhiên", 2),
                        new LowStockRow("Galaxy S24 Ultra 512GB - Đen", 3),
                        new LowStockRow("Xiaomi 14 12GB/256GB - Trắng", 1),
                        new LowStockRow("OPPO Reno11 F 8GB/256GB - Xanh", 0),
                        new LowStockRow("vivo V30 12GB/256GB - Tím", 4)),
                List.of(
                        new RecentOrderRow(1014, "Nguyễn Văn An", vnd(29_990_000), "Hoàn thành"),
                        new RecentOrderRow(1013, "Trần Thị Bích", vnd(14_490_000), "Đang giao"),
                        new RecentOrderRow(1012, "Lê Hoàng Cường", vnd(8_990_000), "Chờ xác nhận"),
                        new RecentOrderRow(1011, "Phạm Ngọc Dung", vnd(33_990_000), "Hoàn thành"),
                        new RecentOrderRow(1010, "Võ Quốc Em", vnd(6_490_000), "Đã hủy"),
                        new RecentOrderRow(1009, "Nguyễn Văn An", vnd(12_990_000), "Hoàn thành")));
    }
}
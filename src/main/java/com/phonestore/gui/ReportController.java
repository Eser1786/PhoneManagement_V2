package com.phonestore.gui;

import com.phonestore.bus.ReportBUS;
import com.phonestore.dto.ProductListItemDTO;
import com.phonestore.dto.RevenuePointDTO;
import com.phonestore.dto.TopProductDTO;
import com.phonestore.exception.BusinessException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;

import java.math.BigDecimal;
import java.net.URL;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Function;

/**
 * Màn hình Báo cáo thống kê (Report.fxml).
 * Dữ liệu từ ReportBUS (getRevenueByDay, getTopProducts, getLowStock), chạy trong Task nền.
 * ReportBUS yêu cầu quyền view_reports (getLowStock: view_reports hoặc manage_inventory).
 *
 * BE chưa có "doanh thu theo hãng/danh mục", nên PieChart hiển thị tỷ trọng doanh thu top 5 sản phẩm.
 * Khi BE bổ sung, chỉ cần đổi nguồn dữ liệu của phần pie trong loadData().
 */
public class ReportController implements Initializable {

    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final int TOP_LIMIT = 10;
    private static final int PIE_LIMIT = 5;
    private static final int MAX_DAYS = 62;
    private static final String DB_ERROR =
            "Không kết nối được cơ sở dữ liệu. Hãy chạy \"docker compose up -d\" rồi bấm Áp dụng lại.";

    private static final NumberFormat MONEY = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static { MONEY.setMaximumFractionDigits(0); }

    // ---------- Thành phần trong Report.fxml ----------
    @FXML private Label rangeLabel;
    @FXML private Label statusLabel;
    @FXML private DatePicker fromDate;
    @FXML private DatePicker toDate;
    @FXML private Button applyBtn;
    @FXML private Label totalRevenue;
    @FXML private Label totalProfit;
    @FXML private Label totalOrders;

    @FXML private LineChart<String, Number> revenueChart;
    @FXML private BarChart<String, Number> profitChart;
    @FXML private PieChart brandChart;

    @FXML private TableView<TopRow> topTable;
    @FXML private TableColumn<TopRow, TopRow> colTopName;
    @FXML private TableColumn<TopRow, TopRow> colTopQty;
    @FXML private TableColumn<TopRow, TopRow> colTopRev;

    @FXML private TableView<StockRow> stockTable;
    @FXML private TableColumn<StockRow, StockRow> colStockName;
    @FXML private TableColumn<StockRow, StockRow> colStockQty;
    @FXML private TableColumn<StockRow, StockRow> colStockStatus;

    // =====================================================================
    //  Khởi tạo
    // =====================================================================
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        toDate.setValue(LocalDate.now());
        fromDate.setValue(LocalDate.now().minusDays(6));
        setupTables();
        retitlePie("Tỷ trọng doanh thu top " + PIE_LIMIT + " sản phẩm");
        onApply();
    }

    /** Đổi tiêu đề thẻ chứa PieChart (không cần sửa FXML). */
    private void retitlePie(String title) {
        if (brandChart.getParent() instanceof Pane card) {
            for (Node n : card.getChildren()) {
                if (n instanceof Label l && l.getStyleClass().contains("rep-section-title")) {
                    l.setText(title);
                }
            }
        }
    }

    @FXML
    private void onApply() {
        LocalDate from = fromDate.getValue();
        LocalDate to = toDate.getValue();
        if (from == null || to == null) {
            showStatus("Vui lòng chọn đủ ngày bắt đầu và ngày kết thúc.", true);
            return;
        }
        if (from.isAfter(to)) {
            showStatus("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.", true);
            return;
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            showStatus("Chỉ xem tối đa " + MAX_DAYS + " ngày mỗi lần.", true);
            return;
        }

        applyBtn.setDisable(true);
        showStatus("Đang tải dữ liệu...", false);

        Task<ReportData> task = new Task<>() {
            @Override protected ReportData call() {
                return loadData(from, to);
            }
        };
        task.setOnSucceeded(e -> {
            applyBtn.setDisable(false);
            fill(task.getValue(), from, to);
            showStatus("", false);
        });
        task.setOnFailed(e -> {
            applyBtn.setDisable(false);
            Throwable ex = task.getException();
            showStatus(ex instanceof BusinessException && ex.getMessage() != null ? ex.getMessage() : DB_ERROR, true);
        });
        Thread t = new Thread(task, "report-loader");
        t.setDaemon(true);
        t.start();
    }

    private void showStatus(String text, boolean error) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().remove("rep-status-error");
        if (error) statusLabel.getStyleClass().add("rep-status-error");
    }

    // =====================================================================
    //  Đổ dữ liệu
    // =====================================================================
    private void fill(ReportData d, LocalDate from, LocalDate to) {
        rangeLabel.setText("Từ " + from.format(FULL) + " đến " + to.format(FULL));

        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal profit = BigDecimal.ZERO;
        int orders = 0;
        for (DayStat s : d.days()) {
            revenue = revenue.add(s.revenue());
            profit = profit.add(s.profit());
            orders += s.orders();
        }
        totalRevenue.setText(formatMoney(revenue));
        totalProfit.setText(formatMoney(profit));
        totalOrders.setText(String.valueOf(orders));

        XYChart.Series<String, Number> rev = new XYChart.Series<>();
        XYChart.Series<String, Number> pro = new XYChart.Series<>();
        for (DayStat s : d.days()) {
            String label = s.date().format(DAY);
            rev.getData().add(new XYChart.Data<>(label, s.revenue().doubleValue() / 1_000_000.0));
            pro.getData().add(new XYChart.Data<>(label, s.profit().doubleValue() / 1_000_000.0));
        }
        revenueChart.getData().setAll(List.of(rev));
        profitChart.getData().setAll(List.of(pro));

        List<PieChart.Data> slices = new ArrayList<>();
        for (ProductShare p : d.shares()) {
            slices.add(new PieChart.Data(p.name(), p.revenue().doubleValue()));
        }
        brandChart.setData(FXCollections.observableArrayList(slices));

        topTable.getItems().setAll(d.topProducts());
        stockTable.getItems().setAll(d.lowStock());
    }

    // =====================================================================
    //  Bảng
    // =====================================================================
    private void setupTables() {
        topTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        topTable.setPlaceholder(new Label("Không có dữ liệu bán hàng trong khoảng thời gian này"));
        textColumn(colTopName, TopRow::name);
        textColumn(colTopQty, r -> String.valueOf(r.quantity()));
        textColumn(colTopRev, r -> formatMoney(r.revenue()));

        stockTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        stockTable.setPlaceholder(new Label("Không có sản phẩm nào sắp hết hàng"));
        textColumn(colStockName, StockRow::name);
        textColumn(colStockQty, r -> String.valueOf(r.stock()));
        colStockStatus.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        colStockStatus.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(StockRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                Label b = new Label(item.stock() <= 0 ? "Hết hàng" : "Sắp hết");
                b.getStyleClass().addAll("rep-badge", item.stock() <= 0 ? "rep-badge-red" : "rep-badge-yellow");
                setGraphic(b);
            }
        });
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

    private static String formatMoney(BigDecimal amount) {
        return MONEY.format(amount == null ? BigDecimal.ZERO : amount) + " ₫";
    }

    // =====================================================================
    //  KẾT NỐI BUS
    // =====================================================================
    public record DayStat(LocalDate date, BigDecimal revenue, BigDecimal profit, int orders) { }

    public record ProductShare(String name, BigDecimal revenue) { }

    public record TopRow(String name, int quantity, BigDecimal revenue) { }

    public record StockRow(String name, int stock) { }

    public record ReportData(List<DayStat> days, List<ProductShare> shares,
                             List<TopRow> topProducts, List<StockRow> lowStock) { }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Chạy ở thread nền: KHÔNG chạm vào control JavaFX trong hàm này. */
    private ReportData loadData(LocalDate from, LocalDate to) {
        ReportBUS bus = new ReportBUS();

        // BUS điền đủ các ngày (ngày không có đơn = 0), thứ tự tăng dần. Đơn "Đã hủy" đã bị loại.
        List<DayStat> days = new ArrayList<>();
        List<RevenuePointDTO> points = bus.getRevenueByDay(from, to);
        for (int i = 0; i < points.size(); i++) {
            RevenuePointDTO p = points.get(i);
            days.add(new DayStat(dateOf(p.getPeriod(), from, i), nz(p.getRevenue()), nz(p.getProfit()), p.getOrderCount()));
        }

        List<TopRow> top = new ArrayList<>();
        List<ProductShare> shares = new ArrayList<>();
        for (TopProductDTO p : bus.getTopProducts(from, to, TOP_LIMIT)) {
            String name = label(p.getProductName(), p.getStorage(), p.getColor());
            top.add(new TopRow(name, p.getQuantitySold(), nz(p.getRevenue())));
            if (shares.size() < PIE_LIMIT) shares.add(new ProductShare(name, nz(p.getRevenue())));
        }

        List<StockRow> low = new ArrayList<>();
        for (ProductListItemDTO p : bus.getLowStock(LOW_STOCK_THRESHOLD)) {
            low.add(new StockRow(label(p.getProductName(), p.getStorage(), p.getColor()), p.getStockQuantity()));
        }
        return new ReportData(days, shares, top, low);
    }

    /** period là String: thử đọc dạng yyyy-MM-dd, nếu không được thì suy ra từ vị trí. */
    private static LocalDate dateOf(String period, LocalDate from, int index) {
        try {
            return LocalDate.parse(period);
        } catch (RuntimeException e) {
            return from.plusDays(index);
        }
    }

    private static String label(String name, String storage, String color) {
        StringBuilder sb = new StringBuilder(name == null ? "" : name);
        if (storage != null && !storage.isBlank()) sb.append(' ').append(storage);
        if (color != null && !color.isBlank()) sb.append(" - ").append(color);
        return sb.toString();
    }
}

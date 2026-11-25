package com.phonestore.gui;

import java.lang.reflect.Method;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.phonestore.bus.InventoryBUS;
import com.phonestore.bus.ProductBUS;
import com.phonestore.dto.InventoryDTO;
import com.phonestore.dto.ProductSpecDTO;
import com.phonestore.exception.BusinessException;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

public class InventoryController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private TabPane tabPaneInventory;
    @FXML private Label lblLowStockAlert;
    @FXML private Label lblMovementInfo;

    // Tab 1: Tồn kho
    @FXML private TextField txtSearchStock;
    @FXML private ComboBox<String> cmbStockFilter;
    @FXML private Spinner<Integer> spnLowStockThreshold;
    @FXML private TableView<ProductSpecDTO> tblStock;
    @FXML private TableColumn<ProductSpecDTO, String> colSpecId;
    @FXML private TableColumn<ProductSpecDTO, String> colProductName;
    @FXML private TableColumn<ProductSpecDTO, String> colRam;
    @FXML private TableColumn<ProductSpecDTO, String> colStorage;
    @FXML private TableColumn<ProductSpecDTO, String> colColor;
    @FXML private TableColumn<ProductSpecDTO, Integer> colStockQty;
    @FXML private TableColumn<ProductSpecDTO, String> colStockStatus;

    // Tab 2: Nhập / Xuất kho
    @FXML private ComboBox<String> cmbMovementType;
    @FXML private ComboBox<ProductSpecDTO> cmbTargetSpec;
    @FXML private TextField txtQuantity;
    @FXML private TextField txtReferenceCode;
    @FXML private TextField txtNote;
    @FXML private Button btnSubmitMovement;

    // Tab 3: Lịch sử kho
    @FXML private TextField txtSearchHistory;
    @FXML private ComboBox<String> cmbHistoryTypeFilter;
    @FXML private TableView<InventoryDTO> tblHistory;
    @FXML private TableColumn<InventoryDTO, String> colReceiptId;
    @FXML private TableColumn<InventoryDTO, String> colHistoryProductName;
    @FXML private TableColumn<InventoryDTO, String> colHistorySpec;
    @FXML private TableColumn<InventoryDTO, String> colHistoryType;
    @FXML private TableColumn<InventoryDTO, Integer> colHistoryQty;
    @FXML private TableColumn<InventoryDTO, String> colHistoryNote;
    @FXML private TableColumn<InventoryDTO, String> colCreatedDate;

    private final InventoryBUS inventoryBUS = new InventoryBUS();
    private final ProductBUS productBUS = new ProductBUS();

    // Map tra cứu nhanh: productId -> tên sản phẩm, specId -> cấu hình
    private final Map<Integer, String> productNameMap = new HashMap<>();
    private final Map<Integer, ProductSpecDTO> specMap = new HashMap<>();

    private final ObservableList<ProductSpecDTO> allStockItems = FXCollections.observableArrayList();
    private final ObservableList<ProductSpecDTO> filteredStockItems = FXCollections.observableArrayList();
    private final ObservableList<InventoryDTO> allHistoryItems = FXCollections.observableArrayList();
    private final ObservableList<InventoryDTO> filteredHistoryItems = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupControls();
        setupStockTable();
        setupHistoryTable();
        loadAllData();
    }

    private void setupControls() {
        cmbStockFilter.setItems(FXCollections.observableArrayList(
                "Tất cả tồn kho",
                "Sắp hết hàng",
                "Hết hàng"
        ));
        cmbStockFilter.getSelectionModel().selectFirst();

        spnLowStockThreshold.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(
                        1, 1000, InventoryBUS.DEFAULT_LOW_STOCK_THRESHOLD
                )
        );

        cmbMovementType.setItems(FXCollections.observableArrayList(
                InventoryBUS.TYPE_IMPORT,
                InventoryBUS.TYPE_EXPORT
        ));
        cmbMovementType.getSelectionModel().selectFirst();
        cmbMovementType.valueProperty().addListener((obs, oldVal, newVal) -> updateMovementModeUI(newVal));
        updateMovementModeUI(cmbMovementType.getValue());

        cmbTargetSpec.setConverter(new StringConverter<>() {
            @Override
            public String toString(ProductSpecDTO spec) {
                if (spec == null) return "";
                String productName = getProductNameBySpec(spec);
                String detail = formatSpecDetail(spec);
                return productName
                        + (detail.isBlank() ? "" : " (" + detail + ")")
                        + " - Tồn: " + spec.getStockQuantity();
            }

            @Override
            public ProductSpecDTO fromString(String string) {
                return null;
            }
        });

        cmbHistoryTypeFilter.setItems(FXCollections.observableArrayList(
                "Tất cả",
                InventoryBUS.TYPE_IMPORT,
                InventoryBUS.TYPE_EXPORT
        ));
        cmbHistoryTypeFilter.getSelectionModel().selectFirst();

        txtSearchStock.textProperty().addListener((obs, o, n) -> applyStockFilter());
        cmbStockFilter.valueProperty().addListener((obs, o, n) -> applyStockFilter());
        spnLowStockThreshold.valueProperty().addListener((obs, o, n) -> {
            updateLowStockBadge();
            applyStockFilter();
        });
    }

    private void updateMovementModeUI(String mode) {
        if (InventoryBUS.TYPE_EXPORT.equals(mode)) {
            btnSubmitMovement.setText("Xác nhận phiếu xuất kho");
            lblMovementInfo.setText("Xuất kho sẽ trừ tồn kho của cấu hình đã chọn và ghi lịch sử giao dịch.");
        } else {
            btnSubmitMovement.setText("Xác nhận phiếu nhập kho");
            lblMovementInfo.setText("Nhập kho sẽ cộng tồn kho của cấu hình đã chọn và ghi lịch sử giao dịch.");
        }
    }

    private void setupStockTable() {
        colSpecId.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getSpecId())));
        colProductName.setCellValueFactory(c ->
                new SimpleStringProperty(getProductNameBySpec(c.getValue())));
        colRam.setCellValueFactory(c ->
                new SimpleStringProperty(safeStr(c.getValue().getRam())));
        colStorage.setCellValueFactory(c ->
                new SimpleStringProperty(safeStr(c.getValue().getStorage())));
        colColor.setCellValueFactory(c ->
                new SimpleStringProperty(safeStr(c.getValue().getColor())));
        colStockQty.setCellValueFactory(c ->
                new SimpleIntegerProperty(c.getValue().getStockQuantity()).asObject());
        colStockStatus.setCellValueFactory(c -> {
            int qty = c.getValue().getStockQuantity();
            int threshold = getThreshold();
            if (qty <= 0) return new SimpleStringProperty("Hết hàng");
            if (qty <= threshold) return new SimpleStringProperty("Sắp hết");
            return new SimpleStringProperty("Còn hàng");
        });

        tblStock.setItems(filteredStockItems);
    }

    private void setupHistoryTable() {
        colReceiptId.setCellValueFactory(c ->
                new SimpleStringProperty("GD#" + c.getValue().getInventoryId()));
        colHistoryProductName.setCellValueFactory(c -> {
            Integer specId = c.getValue().getSpecId();
            if (specId == null) return new SimpleStringProperty("—");
            ProductSpecDTO spec = specMap.get(specId);
            return new SimpleStringProperty(spec != null ? getProductNameBySpec(spec) : "Spec #" + specId);
        });
        colHistorySpec.setCellValueFactory(c -> {
            Integer specId = c.getValue().getSpecId();
            if (specId == null) return new SimpleStringProperty("—");
            ProductSpecDTO spec = specMap.get(specId);
            return new SimpleStringProperty(spec != null ? formatSpecDetail(spec) : ("#" + specId));
        });
        colHistoryType.setCellValueFactory(c ->
                new SimpleStringProperty(safeStr(c.getValue().getTransactionType())));
        colHistoryQty.setCellValueFactory(c -> {
            InventoryDTO log = c.getValue();
            int qty = log.getQuantityIn() > 0 ? log.getQuantityIn() : log.getQuantityOut();
            return new SimpleIntegerProperty(qty).asObject();
        });
        colHistoryNote.setCellValueFactory(c ->
                new SimpleStringProperty(safeStr(c.getValue().getNotes())));
        colCreatedDate.setCellValueFactory(c -> {
            if (c.getValue().getTransactionDate() == null) {
                return new SimpleStringProperty("");
            }
            return new SimpleStringProperty(c.getValue().getTransactionDate().format(DATE_FMT));
        });

        tblHistory.setItems(filteredHistoryItems);
    }

    @FXML
    private void handleRefresh() {
        loadAllData();
    }

    private void loadAllData() {
        loadProductNames();
        loadStockData();
        loadHistoryData();
    }

   private void loadProductNames() {
        productNameMap.clear();
        try {
            List<?> products = invokeZeroArgList(
                    productBUS,
                    "getAllProducts", "findAll", "getProducts", "getAll"
            );
            for (Object p : products) {
                if (p != null) {
                    int id = invokeInt(p, "getProductId", "getId");
                    String name = invokeString(p, "getProductName", "getName", "getProductTitle");
                    if (id > 0 && !name.isBlank()) {
                        productNameMap.put(id, name);
                    }
                }
            }
        } catch (Exception ignored) {
            // Nếu chưa có quyền xem sản phẩm hoặc lỗi nhẹ, vẫn fallback về Product #<id>
        }
    }
    private void loadStockData() {
        try {
            List<ProductSpecDTO> rows = inventoryBUS.getLowStockSpecs(Integer.MAX_VALUE);
            allStockItems.setAll(rows);

            specMap.clear();
            for (ProductSpecDTO spec : rows) {
                specMap.put(spec.getSpecId(), spec);
            }

            ProductSpecDTO selectedSpec = cmbTargetSpec.getValue();
            cmbTargetSpec.setItems(FXCollections.observableArrayList(rows));
            if (selectedSpec != null && rows.contains(selectedSpec)) {
                cmbTargetSpec.getSelectionModel().select(selectedSpec);
            } else if (!rows.isEmpty()) {
                cmbTargetSpec.getSelectionModel().selectFirst();
            }

            updateLowStockBadge();
            applyStockFilter();
        } catch (BusinessException ex) {
            showError("Thông báo tồn kho", ex.getMessage());
        } catch (Exception ex) {
            showError("Lỗi tải dữ liệu tồn kho", ex.getMessage());
        }
    }

    private void loadHistoryData() {
        try {
            List<InventoryDTO> logs = inventoryBUS.getAllInventoryLogs();
            allHistoryItems.setAll(logs);
            applyHistoryFilter();
        } catch (BusinessException ex) {
            showError("Thông báo lịch sử kho", ex.getMessage());
        } catch (Exception ex) {
            showError("Lỗi tải lịch sử kho", ex.getMessage());
        }
    }

    private void updateLowStockBadge() {
        int threshold = getThreshold();
        long lowCount = allStockItems.stream()
                .filter(spec -> spec.getStockQuantity() <= threshold)
                .count();
        lblLowStockAlert.setText("Cảnh báo tồn thấp (≤ " + threshold + "): " + lowCount);
    }

    @FXML
    private void handleFilterStock() {
        applyStockFilter();
    }

    @FXML
    private void handleResetStockFilter() {
        txtSearchStock.clear();
        cmbStockFilter.getSelectionModel().selectFirst();
        applyStockFilter();
    }

    private void applyStockFilter() {
        String keyword = txtSearchStock.getText() == null
                ? ""
                : txtSearchStock.getText().trim().toLowerCase(Locale.ROOT);
        String filterMode = cmbStockFilter.getValue();
        int threshold = getThreshold();

        List<ProductSpecDTO> result = new ArrayList<>();
        for (ProductSpecDTO spec : allStockItems) {
            int qty = spec.getStockQuantity();
            if ("Sắp hết hàng".equals(filterMode) && qty > threshold) continue;
            if ("Hết hàng".equals(filterMode) && qty > 0) continue;

            if (!keyword.isEmpty()) {
                String haystack = (
                        spec.getSpecId() + " " +
                        getProductNameBySpec(spec) + " " +
                        safeStr(spec.getRam()) + " " +
                        safeStr(spec.getStorage()) + " " +
                        safeStr(spec.getColor())
                ).toLowerCase(Locale.ROOT);
                if (!haystack.contains(keyword)) continue;
            }
            result.add(spec);
        }
        filteredStockItems.setAll(result);
    }

    @FXML
    private void handleSubmitMovement() {
        ProductSpecDTO selectedSpec = cmbTargetSpec.getValue();
        if (selectedSpec == null) {
            showError("Thiếu thông tin", "Vui lòng chọn sản phẩm / cấu hình.");
            return;
        }

        int qty;
        try {
            qty = Integer.parseInt(txtQuantity.getText().trim());
        } catch (NumberFormatException e) {
            showError("Số lượng không hợp lệ", "Vui lòng nhập số nguyên hợp lệ.");
            return;
        }

        String mode = cmbMovementType.getValue();
        String refCode = txtReferenceCode.getText() == null ? "" : txtReferenceCode.getText().trim();
        String note = txtNote.getText() == null ? "" : txtNote.getText().trim();
        String fullNote = refCode.isBlank() ? note : ("[" + refCode + "] " + note).trim();

        try {
            if (InventoryBUS.TYPE_EXPORT.equals(mode)) {
                inventoryBUS.exportStock(selectedSpec.getSpecId(), qty, fullNote);
                showInfo("Thành công", "Đã lập phiếu xuất kho thành công.");
            } else {
                inventoryBUS.importStock(selectedSpec.getSpecId(), qty, fullNote);
                showInfo("Thành công", "Đã lập phiếu nhập kho thành công.");
            }
            handleClearMovementForm();
            loadAllData();
        } catch (BusinessException ex) {
            showError("Không thể thực hiện phiếu kho", ex.getMessage());
        } catch (Exception ex) {
            showError("Lỗi hệ thống", ex.getMessage());
        }
    }

    @FXML
    private void handleClearMovementForm() {
        txtQuantity.clear();
        txtReferenceCode.clear();
        txtNote.clear();
    }

    @FXML
    private void handleFilterHistory() {
        applyHistoryFilter();
    }

    @FXML
    private void handleResetHistoryFilter() {
        txtSearchHistory.clear();
        cmbHistoryTypeFilter.getSelectionModel().selectFirst();
        applyHistoryFilter();
    }

    private void applyHistoryFilter() {
        String keyword = txtSearchHistory.getText() == null
                ? ""
                : txtSearchHistory.getText().trim().toLowerCase(Locale.ROOT);
        String typeFilter = cmbHistoryTypeFilter.getValue();

        List<InventoryDTO> result = new ArrayList<>();
        for (InventoryDTO log : allHistoryItems) {
            String rowType = safeStr(log.getTransactionType());
            if (typeFilter != null && !"Tất cả".equals(typeFilter) && !typeFilter.equalsIgnoreCase(rowType)) {
                continue;
            }

            if (!keyword.isEmpty()) {
                Integer specId = log.getSpecId();
                ProductSpecDTO spec = specId != null ? specMap.get(specId) : null;
                String prodName = spec != null ? getProductNameBySpec(spec) : "";
                String haystack = (
                        "gd#" + log.getInventoryId() + " " +
                        log.getInventoryId() + " " +
                        prodName + " " +
                        safeStr(log.getNotes())
                ).toLowerCase(Locale.ROOT);
                if (!haystack.contains(keyword)) continue;
            }
            result.add(log);
        }
        filteredHistoryItems.setAll(result);
    }

    private String getProductNameBySpec(ProductSpecDTO spec) {
        if (spec == null) return "";
        Integer pid = spec.getProductId();
        if (pid != null && productNameMap.containsKey(pid)) {
            return productNameMap.get(pid);
        }
        return pid != null ? "Sản phẩm #" + pid : "Cấu hình #" + spec.getSpecId();
    }

    private String formatSpecDetail(ProductSpecDTO spec) {
        List<String> parts = new ArrayList<>();
        if (spec.getRam() != null && !spec.getRam().isBlank()) parts.add(spec.getRam().trim());
        if (spec.getStorage() != null && !spec.getStorage().isBlank()) parts.add(spec.getStorage().trim());
        if (spec.getColor() != null && !spec.getColor().isBlank()) parts.add(spec.getColor().trim());
        return String.join(" / ", parts);
    }

    private int getThreshold() {
        Integer v = spnLowStockThreshold.getValue();
        return v == null || v < 0 ? InventoryBUS.DEFAULT_LOW_STOCK_THRESHOLD : v;
    }

    private String safeStr(String s) {
        return s == null ? "" : s;
    }

    private List<?> invokeZeroArgList(Object target, String... names) {
        for (String name : names) {
            try {
                Method m = target.getClass().getMethod(name);
                Object val = m.invoke(target);
                if (val instanceof List<?> list) return list;
            } catch (Exception ignored) {
            }
        }
        return List.of();
    }

    private int invokeInt(Object obj, String... names) {
        for (String name : names) {
            try {
                Method m = obj.getClass().getMethod(name);
                Object val = m.invoke(obj);
                if (val instanceof Number n) return n.intValue();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private String invokeString(Object obj, String... names) {
        for (String name : names) {
            try {
                Method m = obj.getClass().getMethod(name);
                Object val = m.invoke(obj);
                if (val != null) return String.valueOf(val);
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message == null || message.isBlank() ? "Đã xảy ra lỗi không xác định." : message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
package com.phonestore.gui;

import java.util.List;

import com.phonestore.bus.InventoryBUS;
import com.phonestore.dto.InventoryDTO;
import com.phonestore.dto.ProductSpecDTO;
import com.phonestore.util.AlertUtil;
import com.phonestore.util.FormatUtil;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.StackPane;

public class InventoryController {

    @FXML private StackPane rootView;
    @FXML private TextField txtSearchInventory;
    @FXML private ComboBox<String> cbStockFilter;
    
    // Bảng Tab 1: Tồn kho sản phẩm (ProductSpecDTO)
    @FXML private TableView<ProductSpecDTO> tblInventory;
    @FXML private TableColumn<ProductSpecDTO, Integer> colSkuId;
    @FXML private TableColumn<ProductSpecDTO, String> colSpecDetail;
    @FXML private TableColumn<ProductSpecDTO, String> colPrice;
    @FXML private TableColumn<ProductSpecDTO, Integer> colStockQty;
    @FXML private TableColumn<ProductSpecDTO, String> colStatusAlert;

    // Bảng Tab 2: Lịch sử nhập xuất kho (InventoryDTO)
    @FXML private TableView<InventoryDTO> tblReceiptHistory;
    @FXML private TableColumn<InventoryDTO, String> colReceiptType;
    @FXML private TableColumn<InventoryDTO, Integer> colSpecIdLog;
    @FXML private TableColumn<InventoryDTO, String> colQuantityChanged;
    @FXML private TableColumn<InventoryDTO, String> colNotes;

    private final InventoryBUS inventoryBUS = new InventoryBUS();

    private final ObservableList<ProductSpecDTO> inventoryData = FXCollections.observableArrayList();
    private final ObservableList<InventoryDTO> historyData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        initInventoryColumns();
        initHistoryColumns();
        loadFilterOptions();
        loadData();

        txtSearchInventory.textProperty().addListener((obs, o, n) -> filterInventory());
        cbStockFilter.setOnAction(e -> filterInventory());
    }

    private void initInventoryColumns() {
        colSkuId.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getSpecId()));
        colSkuId.setStyle("-fx-alignment: CENTER;");
        
        colSpecDetail.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper("RAM: " + cellData.getValue().getRam() + " - ROM: " + cellData.getValue().getStorage()));
        
        colPrice.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(FormatUtil.formatCurrency(cellData.getValue().getPrice())));
        colPrice.setStyle("-fx-alignment: CENTER-RIGHT;");
        
        colStockQty.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getStockQuantity()));
        colStockQty.setStyle("-fx-alignment: CENTER;");

        colStatusAlert.setCellValueFactory(cellData -> {
            int stock = cellData.getValue().getStockQuantity();
            String status = stock <= InventoryBUS.DEFAULT_LOW_STOCK_THRESHOLD ? "⚠️ Sắp hết hàng" : "Ổn định";
            return new ReadOnlyObjectWrapper<>(status);
        });

        colStatusAlert.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER);
                    if (item.contains("Sắp hết")) {
                        setStyle("-fx-text-fill: #d9534f; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #5cb85c;");
                    }
                }
            }
        });
    }

    private void initHistoryColumns() {
        colReceiptType.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTransactionType()));
        
        colSpecIdLog.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getSpecId()));
        colSpecIdLog.setStyle("-fx-alignment: CENTER;");
        
        colQuantityChanged.setCellValueFactory(cellData -> {
            InventoryDTO log = cellData.getValue();
            String text = log.getQuantityIn() > 0 ? "+" + log.getQuantityIn() : "-" + log.getQuantityOut();
            return new ReadOnlyObjectWrapper<>(text);
        });
        colQuantityChanged.setStyle("-fx-alignment: CENTER;");
        
        colNotes.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getNotes()));
    }

    private void loadFilterOptions() {
        cbStockFilter.setItems(FXCollections.observableArrayList("Tất cả trạng thái", "Sắp hết hàng", "Còn hàng"));
        cbStockFilter.getSelectionModel().selectFirst();
    }

    private void loadData() {
        try {
            // Lấy toàn bộ danh sách thông qua ngưỡng cực đại
            List<ProductSpecDTO> specs = inventoryBUS.getLowStockSpecs(Integer.MAX_VALUE);
            inventoryData.setAll(specs);
            tblInventory.setItems(inventoryData);

            // Lấy lịch sử biến động kho
            List<InventoryDTO> logs = inventoryBUS.getAllInventoryLogs();
            historyData.setAll(logs);
            tblReceiptHistory.setItems(historyData);
        } catch (Exception e) {
            AlertUtil.showError("Lỗi hệ thống", e.getMessage());
        }
    }

    private void filterInventory() {
        String keyword = txtSearchInventory.getText();
        String filterChoice = cbStockFilter.getValue();

        try {
            List<ProductSpecDTO> all = inventoryBUS.getLowStockSpecs(Integer.MAX_VALUE);
            ObservableList<ProductSpecDTO> filtered = FXCollections.observableArrayList();

            for (ProductSpecDTO p : all) {
                boolean matchesKeyword = (keyword == null || keyword.isBlank()) ||
                        String.valueOf(p.getSpecId()).contains(keyword.trim()) ||
                        p.getRam().toLowerCase().contains(keyword.toLowerCase().trim()) ||
                        p.getStorage().toLowerCase().contains(keyword.toLowerCase().trim());

                boolean matchesStock = true;
                if ("Sắp hết hàng".equals(filterChoice)) {
                    matchesStock = p.getStockQuantity() <= InventoryBUS.DEFAULT_LOW_STOCK_THRESHOLD;
                } else if ("Còn hàng".equals(filterChoice)) {
                    matchesStock = p.getStockQuantity() > InventoryBUS.DEFAULT_LOW_STOCK_THRESHOLD;
                }

                if (matchesKeyword && matchesStock) {
                    filtered.add(p);
                }
            }
            tblInventory.setItems(filtered);
        } catch (Exception e) {
            AlertUtil.showError("Lỗi", e.getMessage());
        }
    }

    @FXML
    private void onRefreshStock() {
        txtSearchInventory.clear();
        cbStockFilter.getSelectionModel().selectFirst();
        loadData();
        AlertUtil.showInfo("Thành công", "Đã làm mới dữ liệu kho hàng.");
    }

    @FXML
    private void onOpenCreateReceiptModal() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Lập phiếu nhập kho nhanh");
        dialog.setHeaderText("Nhập thông tin giao dịch nhập kho");
        dialog.setContentText("Cú pháp: [Mã SKU SpecID],[Số lượng],[Ghi chú]\nVí dụ: 1,5,Nhập thêm hàng từ nhà cung cấp");

        dialog.showAndWait().ifPresent(result -> {
            try {
                String[] parts = result.split(",");
                int specId = Integer.parseInt(parts[0].trim());
                int qty = Integer.parseInt(parts[1].trim());
                String note = parts.length > 2 ? parts[2].trim() : "Nhập kho thủ công";

                inventoryBUS.importStock(specId, qty, note);
                AlertUtil.showInfo("Thành công", "Lập phiếu nhập kho và cập nhật tồn kho thành công!");
                loadData();
            } catch (Exception e) {
                AlertUtil.showError("Lỗi nghiệp vụ", "Không thể nhập kho: " + e.getMessage());
            }
        });
    }
}
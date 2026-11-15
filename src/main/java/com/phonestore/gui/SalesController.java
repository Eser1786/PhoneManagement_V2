package com.phonestore.gui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.phonestore.bus.CategoryBUS;
import com.phonestore.bus.OrderBUS;
import com.phonestore.bus.ProductBUS;
import com.phonestore.dto.CategoryDTO;
import com.phonestore.dto.CustomerDTO;
import com.phonestore.dto.OrderDetailDTO;
import com.phonestore.dto.ProductListItemDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.AlertUtil;
import com.phonestore.util.FormatUtil;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

public class SalesController {

    @FXML private StackPane rootView;
    @FXML private TextField txtSearchProduct;
    @FXML private ComboBox<CategoryDTO> cbCategoryFilter;
    @FXML private TableView<ProductListItemDTO> tblCatalog;
    @FXML private TableColumn<ProductListItemDTO, String> colCatName;
    @FXML private TableColumn<ProductListItemDTO, String> colCatPrice;
    @FXML private TableColumn<ProductListItemDTO, Integer> colCatStock;
    @FXML private TableColumn<ProductListItemDTO, Void> colCatAction;

    @FXML private TableView<CartItem> tblCart;
    @FXML private TableColumn<CartItem, String> colCartName;
    @FXML private TableColumn<CartItem, Integer> colCartQty;
    @FXML private TableColumn<CartItem, String> colCartPrice;
    @FXML private TableColumn<CartItem, Void> colCartAction;

    @FXML private ComboBox<CustomerDTO> cbCustomer;
    @FXML private Label lblSubTotal;
    @FXML private TextField txtCashPaid;
    @FXML private Label lblChangeReturn;

    private final ProductBUS productBUS = new ProductBUS();
    private final CategoryBUS categoryBUS = new CategoryBUS();
    private final OrderBUS orderBUS = new OrderBUS();

    private final ObservableList<ProductListItemDTO> catalogData = FXCollections.observableArrayList();
    private final ObservableList<CartItem> cartData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        initCatalogColumns();
        initCartColumns();
        loadCategories();
        loadCatalogData();

        txtSearchProduct.textProperty().addListener((obs, o, n) -> loadCatalogData());
        cbCategoryFilter.setOnAction(e -> loadCatalogData());

        txtCashPaid.textProperty().addListener((obs, o, n) -> calculateChange());
    }

    private void initCatalogColumns() {
        colCatName.setCellValueFactory(cellData -> {
            ProductListItemDTO item = cellData.getValue();
            return new ReadOnlyObjectWrapper<>(item.getProductName() + " - " + item.getRam() + "/" + item.getStorage());
        });
        colCatPrice.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(FormatUtil.formatCurrency(cellData.getValue().getPrice())));
        colCatPrice.setStyle("-fx-alignment: CENTER-RIGHT;");
        colCatStock.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getStockQuantity()));
        colCatStock.setStyle("-fx-alignment: CENTER;");

        colCatAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnAdd = new Button("+ Chọn");
            {
                btnAdd.getStyleClass().add("app-btn-action");
                btnAdd.setOnAction(e -> addToCart(getTableView().getItems().get(getIndex())));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnAdd);
                setAlignment(Pos.CENTER);
            }
        });
    }

    private void initCartColumns() {
        colCartName.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getItemName()));
        colCartQty.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getQuantity()));
        colCartQty.setStyle("-fx-alignment: CENTER;");
        colCartPrice.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(FormatUtil.formatCurrency(cellData.getValue().getSubTotal())));
        colCartPrice.setStyle("-fx-alignment: CENTER-RIGHT;");

        colCartAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnRemove = new Button("X");
            {
                btnRemove.getStyleClass().add("app-btn-danger-outline");
                btnRemove.setOnAction(e -> {
                    cartData.remove(getTableView().getItems().get(getIndex()));
                    updateCartTotal();
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnRemove);
                setAlignment(Pos.CENTER);
            }
        });
        tblCart.setItems(cartData);
    }

    private void loadCategories() {
        List<CategoryDTO> list = categoryBUS.getAllCategories();
        CategoryDTO all = new CategoryDTO(0, "Tất cả danh mục", "");
        ObservableList<CategoryDTO> cats = FXCollections.observableArrayList();
        cats.add(all);
        cats.addAll(list);
        cbCategoryFilter.setItems(cats);
        cbCategoryFilter.getSelectionModel().selectFirst();
        cbCategoryFilter.setConverter(new StringConverter<>() {
            @Override public String toString(CategoryDTO c) { return c == null ? "" : c.getCategoryName(); }
            @Override public CategoryDTO fromString(String string) { return null; }
        });
    }

    private void loadCatalogData() {
        com.phonestore.dto.ProductFilter filter = new com.phonestore.dto.ProductFilter();
        String kw = txtSearchProduct.getText();
        if (kw != null && !kw.isBlank()) filter.setKeyword(kw.trim());
        CategoryDTO cat = cbCategoryFilter.getValue();
        if (cat != null && cat.getCategoryId() > 0) filter.setCategoryId(cat.getCategoryId());

        try {
            catalogData.setAll(productBUS.searchProducts(filter));
            tblCatalog.setItems(catalogData);
        } catch (Exception e) {
            AlertUtil.showError("Lỗi", e.getMessage());
        }
    }

    private void addToCart(ProductListItemDTO product) {
        for (CartItem item : cartData) {
            if (item.getSpecId() == product.getSpecId()) {
                if (item.getQuantity() + 1 > product.getStockQuantity()) {
                    AlertUtil.showWarning("Vượt quá tồn kho", "Số lượng sản phẩm trong kho không đủ.");
                    return;
                }
                item.setQuantity(item.getQuantity() + 1);
                tblCart.refresh();
                updateCartTotal();
                return;
            }
        }
        cartData.add(new CartItem(product.getSpecId(), product.getProductName() + " (" + product.getRam() + "/" + product.getStorage() + ")", product.getPrice(), 1));
        updateCartTotal();
    }

    private void updateCartTotal() {
        BigDecimal total = cartData.stream().map(CartItem::getSubTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        lblSubTotal.setText(FormatUtil.formatCurrency(total));
        calculateChange();
    }

    private void calculateChange() {
        try {
            BigDecimal total = cartData.stream().map(CartItem::getSubTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            String paidStr = txtCashPaid.getText();
            if (paidStr == null || paidStr.isBlank()) {
                lblChangeReturn.setText("0₫");
                return;
            }
            BigDecimal paid = new BigDecimal(paidStr.trim());
            BigDecimal change = paid.subtract(total);
            lblChangeReturn.setText(FormatUtil.formatCurrency(change.compareTo(BigDecimal.ZERO) >= 0 ? change : BigDecimal.ZERO));
        } catch (NumberFormatException e) {
            lblChangeReturn.setText("0₫");
        }
    }
@FXML
    private void onCheckout() {
        if (cartData.isEmpty()) {
            AlertUtil.showWarning("Giỏ hàng trống", "Vui lòng chọn ít nhất một sản phẩm để thanh toán.");
            return;
        }

        try {
            // Lấy ID khách hàng từ ComboBox (nếu có chọn, ngược lại để null hoặc 0 tùy DB)
            CustomerDTO selectedCustomer = cbCustomer.getValue();
            Integer customerId = (selectedCustomer != null) ? selectedCustomer.getCustomerId() : null;

            int userId = 1; // ID nhân viên hiện tại (hoặc lấy từ SessionManager)
            String paymentMethod = "Tiền mặt"; // Phương thức thanh toán mặc định
            String note = "Bán hàng trực tiếp tại quầy";

            List<OrderDetailDTO> details = new ArrayList<>();
            for (CartItem item : cartData) {
                OrderDetailDTO d = new OrderDetailDTO();
                d.setSpecId(item.getSpecId());
                d.setQuantity(item.getQuantity());
                d.setUnitPrice(item.getUnitPrice());
                details.add(d);
            }

            // Gọi đúng chữ ký phương thức createOrder(Integer, Integer, String, String, List) của OrderBUS hiện tại
            orderBUS.createOrder(userId, customerId, paymentMethod, note, details);

            AlertUtil.showInfo("Thành công", "Thanh toán và tạo hóa đơn thành công!");
            cartData.clear();
            txtCashPaid.clear();
            updateCartTotal();
            loadCatalogData();
        } catch (BusinessException be) {
            AlertUtil.showWarning("Lỗi thanh toán", be.getMessage());
        } catch (Exception ex) {
            AlertUtil.showError("Lỗi hệ thống", ex.getMessage());
        }
    }
    public static class CartItem {
        private final int specId;
        private final String itemName;
        private final BigDecimal unitPrice;
        private int quantity;

        public CartItem(int specId, String itemName, BigDecimal unitPrice, int quantity) {
            this.specId = specId;
            this.itemName = itemName;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
        }

        public int getSpecId() { return specId; }
        public String getItemName() { return itemName; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public BigDecimal getSubTotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
    }
}
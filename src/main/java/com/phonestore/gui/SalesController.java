package com.phonestore.gui;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.phonestore.bus.CategoryBUS;
import com.phonestore.bus.CustomerBUS;
import com.phonestore.bus.OrderBUS;
import com.phonestore.bus.ProductBUS;
import com.phonestore.dto.CategoryDTO;
import com.phonestore.dto.CustomerDTO;
import com.phonestore.dto.OrderDetailDTO;
import com.phonestore.dto.ProductFilter;
import com.phonestore.dto.ProductListItemDTO;
import com.phonestore.dto.UserDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.AlertUtil;
import com.phonestore.util.FormatUtil;
import com.phonestore.util.SessionManager;

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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;

public class SalesController {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String SPEC_STATUS_ON_SALE = "Đang bán";

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
    @FXML private TableColumn<CartItem, CartItem> colCartQty;
    @FXML private TableColumn<CartItem, String> colCartPrice;
    @FXML private TableColumn<CartItem, Void> colCartAction;

    @FXML private ComboBox<CustomerDTO> cbCustomer;
    @FXML private ComboBox<String> cbPaymentMethod;
    @FXML private TextField txtDiscountPercent;
    @FXML private TextField txtOrderNote;
    @FXML private Label lblGrossTotal;
    @FXML private Label lblSubTotal;
    @FXML private TextField txtCashPaid;
    @FXML private Label lblChangeReturn;

    private final ProductBUS productBUS = new ProductBUS();
    private final CategoryBUS categoryBUS = new CategoryBUS();
    private final CustomerBUS customerBUS = new CustomerBUS();
    private final OrderBUS orderBUS = new OrderBUS();

    private final ObservableList<ProductListItemDTO> catalogData = FXCollections.observableArrayList();
    private final ObservableList<CartItem> cartData = FXCollections.observableArrayList();
    private final Map<String, Image> productImageCache = new HashMap<>();

    @FXML
    public void initialize() {
        initCatalogColumns();
        initCartColumns();
        setupPaymentAndDiscountControls();
        loadCategories();
        loadCustomers();
        loadCatalogData();

        txtSearchProduct.textProperty().addListener((obs, oldVal, newVal) -> loadCatalogData());
        cbCategoryFilter.setOnAction(e -> loadCatalogData());
        txtDiscountPercent.textProperty().addListener((obs, oldVal, newVal) -> updateCartTotal());
        txtCashPaid.textProperty().addListener((obs, oldVal, newVal) -> calculateChange());
        cbPaymentMethod.setOnAction(e -> calculateChange());
    }

    private void setupPaymentAndDiscountControls() {
        if (cbPaymentMethod != null) {
            cbPaymentMethod.setItems(FXCollections.observableArrayList(OrderBUS.PAYMENT_METHODS));
            if (!OrderBUS.PAYMENT_METHODS.isEmpty()) {
                cbPaymentMethod.getSelectionModel().selectFirst();
            }
        }
        if (txtDiscountPercent != null && (txtDiscountPercent.getText() == null || txtDiscountPercent.getText().isBlank())) {
            txtDiscountPercent.setText("0");
        }
        updateCartTotal();
    }

    private void initCatalogColumns() {
        tblCatalog.setFixedCellSize(52.0);

        colCatName.setCellValueFactory(cellData -> {
            ProductListItemDTO item = cellData.getValue();
            return new ReadOnlyObjectWrapper<>(
                    item.getProductName() + " - " + safeStr(item.getRam()) + "/" + safeStr(item.getStorage())
            );
        });

        colCatName.setCellFactory(col -> new TableCell<>() {
            private final ImageView imageView = new ImageView();
            private final Label lblName = new Label();
            private final HBox container = new HBox(10.0, imageView, lblName);

            {
                imageView.setFitWidth(38.0);
                imageView.setFitHeight(38.0);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);

                Rectangle clip = new Rectangle(38.0, 38.0);
                clip.setArcWidth(8.0);
                clip.setArcHeight(8.0);
                imageView.setClip(clip);

                lblName.setStyle("-fx-font-weight: 600; -fx-text-fill: #1f2937;");
                container.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                if (empty || text == null || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    setText(null);
                } else {
                    ProductListItemDTO row = getTableView().getItems().get(getIndex());
                    imageView.setImage(getProductThumbnail(row != null ? row.getProductName() : text));
                    lblName.setText(text);
                    setGraphic(container);
                    setText(null);
                }
            }
        });

        colCatPrice.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(FormatUtil.formatCurrency(cellData.getValue().getPrice())));
        colCatPrice.setStyle("-fx-alignment: CENTER-RIGHT;");

        colCatStock.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(cellData.getValue().getStockQuantity()));
        colCatStock.setStyle("-fx-alignment: CENTER;");

        colCatAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnAdd = new Button("+ Chọn");

            {
                btnAdd.getStyleClass().add("app-btn-action");
                btnAdd.setOnAction(e -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        ProductListItemDTO row = getTableView().getItems().get(getIndex());
                        addToCart(row);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    ProductListItemDTO row = getTableView().getItems().get(getIndex());
                    boolean canSell = isSellable(row);
                    btnAdd.setDisable(!canSell);
                    btnAdd.setText(canSell ? "+ Chọn" : "Hết hàng");
                    setGraphic(btnAdd);
                    setAlignment(Pos.CENTER);
                }
            }
        });
    }

    private void initCartColumns() {
        colCartName.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(cellData.getValue().getItemName()));

        colCartQty.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(cellData.getValue()));
        colCartQty.setStyle("-fx-alignment: CENTER;");
        colCartQty.setCellFactory(col -> new TableCell<>() {
            private final Button btnMinus = new Button("-");
            private final Label lblQty = new Label();
            private final Button btnPlus = new Button("+");
            private final HBox box = new HBox(4, btnMinus, lblQty, btnPlus);

            {
                box.setAlignment(Pos.CENTER);
                btnMinus.setOnAction(e -> {
                    CartItem item = getItem();
                    if (item == null) return;
                    if (item.getQuantity() <= 1) {
                        cartData.remove(item);
                    } else {
                        item.setQuantity(item.getQuantity() - 1);
                    }
                    tblCart.refresh();
                    updateCartTotal();
                });
                btnPlus.setOnAction(e -> {
                    CartItem item = getItem();
                    if (item == null) return;
                    if (item.getQuantity() + 1 > item.getMaxStock()) {
                        AlertUtil.showWarning("Vượt quá tồn kho",
                                "Tồn kho tối đa của sản phẩm này là " + item.getMaxStock() + ".");
                        return;
                    }
                    item.setQuantity(item.getQuantity() + 1);
                    tblCart.refresh();
                    updateCartTotal();
                });
            }

            @Override
            protected void updateItem(CartItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    lblQty.setText(String.valueOf(item.getQuantity()));
                    setGraphic(box);
                }
            }
        });

        colCartPrice.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(FormatUtil.formatCurrency(
                        OrderBUS.calculateSubtotal(
                                cellData.getValue().getUnitPrice(),
                                cellData.getValue().getQuantity(),
                                parseDiscountSafe()
                        )
                )));
        colCartPrice.setStyle("-fx-alignment: CENTER-RIGHT;");

        colCartAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnRemove = new Button("X");

            {
                btnRemove.getStyleClass().add("app-btn-danger-outline");
                btnRemove.setOnAction(e -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        cartData.remove(getTableView().getItems().get(getIndex()));
                        updateCartTotal();
                    }
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
        try {
            List<CategoryDTO> list = categoryBUS.getAllCategories();
            CategoryDTO all = new CategoryDTO(0, "Tất cả danh mục", "");
            ObservableList<CategoryDTO> cats = FXCollections.observableArrayList();
            cats.add(all);
            cats.addAll(list);
            cbCategoryFilter.setItems(cats);
            cbCategoryFilter.getSelectionModel().selectFirst();
            cbCategoryFilter.setConverter(new StringConverter<>() {
                @Override
                public String toString(CategoryDTO c) {
                    return c == null ? "" : c.getCategoryName();
                }

                @Override
                public CategoryDTO fromString(String string) {
                    return null;
                }
            });
        } catch (Exception e) {
            AlertUtil.showError("Lỗi tải danh mục", e.getMessage());
        }
    }

    private void loadCustomers() {
        if (cbCustomer == null) return;
        try {
            List<CustomerDTO> customers = customerBUS.getAllCustomers();
            ObservableList<CustomerDTO> items = FXCollections.observableArrayList();
            items.add(null);
            items.addAll(customers);
            cbCustomer.setItems(items);
            cbCustomer.getSelectionModel().selectFirst();
            cbCustomer.setConverter(new StringConverter<>() {
                @Override
                public String toString(CustomerDTO c) {
                    if (c == null) return "Khách vãng lai";
                    return formatCustomerLabel(c);
                }

                @Override
                public CustomerDTO fromString(String string) {
                    return null;
                }
            });
        } catch (BusinessException ignored) {
            cbCustomer.setItems(FXCollections.observableArrayList((CustomerDTO) null));
            cbCustomer.getSelectionModel().selectFirst();
        } catch (Exception e) {
            AlertUtil.showError("Lỗi tải khách hàng", e.getMessage());
        }
    }

    private String formatCustomerLabel(CustomerDTO c) {
        try {
            UserDTO u = customerBUS.getUserOfCustomer(c);
            if (u != null && u.getFullName() != null && !u.getFullName().isBlank()) {
                String phone = (u.getPhone() != null && !u.getPhone().isBlank()) ? " - " + u.getPhone() : "";
                return "#" + c.getCustomerId() + " - " + u.getFullName() + phone;
            }
        } catch (Exception ignored) {
        }
        String city = (c.getCity() != null && !c.getCity().isBlank()) ? " (" + c.getCity() + ")" : "";
        return "Khách #" + c.getCustomerId() + city;
    }

    private void loadCatalogData() {
        ProductFilter filter = new ProductFilter();
        String kw = txtSearchProduct.getText();
        if (kw != null && !kw.isBlank()) {
            filter.setKeyword(kw.trim());
        }
        CategoryDTO cat = cbCategoryFilter.getValue();
        if (cat != null && cat.getCategoryId() > 0) {
            filter.setCategoryId(cat.getCategoryId());
        }

        try {
            catalogData.setAll(productBUS.searchProducts(filter));
            tblCatalog.setItems(catalogData);
        } catch (Exception e) {
            AlertUtil.showError("Lỗi tải sản phẩm", e.getMessage());
        }
    }

    private boolean isSellable(ProductListItemDTO product) {
        if (product == null) return false;
        if (product.getStockQuantity() <= 0) return false;
        String status = readStatusSafe(product);
        return status == null || status.isBlank() || SPEC_STATUS_ON_SALE.equalsIgnoreCase(status.trim());
    }

    private String readStatusSafe(ProductListItemDTO product) {
        for (String method : new String[]{"getStatus", "getSpecStatus"}) {
            try {
                Method m = product.getClass().getMethod(method);
                Object val = m.invoke(product);
                if (val != null) return String.valueOf(val);
            } catch (Exception ignored) {
            }
        }
        return SPEC_STATUS_ON_SALE;
    }

    private void addToCart(ProductListItemDTO product) {
        if (product == null) return;
        if (!isSellable(product)) {
            AlertUtil.showWarning("Không thể thêm sản phẩm",
                    "Sản phẩm hiện hết hàng hoặc ngừng bán.");
            return;
        }

        for (CartItem item : cartData) {
            if (item.getSpecId() == product.getSpecId()) {
                if (item.getQuantity() + 1 > product.getStockQuantity()) {
                    AlertUtil.showWarning("Vượt quá tồn kho",
                            "Số lượng sản phẩm trong kho không đủ (tồn: " + product.getStockQuantity() + ").");
                    return;
                }
                item.setQuantity(item.getQuantity() + 1);
                tblCart.refresh();
                updateCartTotal();
                return;
            }
        }

        String label = product.getProductName() + " (" + safeStr(product.getRam()) + "/" + safeStr(product.getStorage()) + ")";
        cartData.add(new CartItem(product.getSpecId(), label, product.getPrice(), 1, product.getStockQuantity()));
        updateCartTotal();
    }

    private BigDecimal getGrossTotal() {
        return cartData.stream()
                .map(CartItem::getGrossSubTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getNetTotal(BigDecimal discountPercent) {
        return cartData.stream()
                .map(item -> OrderBUS.calculateSubtotal(item.getUnitPrice(), item.getQuantity(), discountPercent))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal parseDiscountSafe() {
        if (txtDiscountPercent == null) return BigDecimal.ZERO;
        String raw = txtDiscountPercent.getText();
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
        try {
            BigDecimal val = new BigDecimal(raw.trim());
            if (val.compareTo(BigDecimal.ZERO) < 0 || val.compareTo(HUNDRED) > 0) {
                return BigDecimal.ZERO;
            }
            return val;
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private void updateCartTotal() {
        BigDecimal gross = getGrossTotal();
        BigDecimal net = getNetTotal(parseDiscountSafe());
        if (lblGrossTotal != null) {
            lblGrossTotal.setText(FormatUtil.formatCurrency(gross));
        }
        if (lblSubTotal != null) {
            lblSubTotal.setText(FormatUtil.formatCurrency(net));
        }
        tblCart.refresh();
        calculateChange();
    }

    private void calculateChange() {
        if (lblChangeReturn == null || txtCashPaid == null) return;
        BigDecimal total = getNetTotal(parseDiscountSafe());
        String paidStr = txtCashPaid.getText();
        if (paidStr == null || paidStr.isBlank()) {
            lblChangeReturn.setText("0₫");
            return;
        }
        try {
            BigDecimal paid = new BigDecimal(paidStr.trim());
            BigDecimal change = paid.subtract(total);
            lblChangeReturn.setText(
                    FormatUtil.formatCurrency(change.compareTo(BigDecimal.ZERO) >= 0 ? change : BigDecimal.ZERO)
            );
        } catch (NumberFormatException e) {
            lblChangeReturn.setText("0₫");
        }
    }

    private Integer resolveCurrentStaffId() {
        UserDTO currentUser = SessionManager.getCurrentUser();
        if (currentUser == null) {
            return null;
        }
        for (String methodName : new String[]{"getStaffId", "getUserId", "getId"}) {
            try {
                Method m = currentUser.getClass().getMethod(methodName);
                Object val = m.invoke(currentUser);
                if (val instanceof Number n && n.intValue() > 0) {
                    return n.intValue();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @FXML
    private void onCheckout() {
        if (cartData.isEmpty()) {
            AlertUtil.showWarning("Giỏ hàng trống", "Vui lòng chọn ít nhất một sản phẩm để thanh toán.");
            return;
        }

        BigDecimal discountPercent;
        try {
            String rawDiscount = (txtDiscountPercent != null && !txtDiscountPercent.getText().isBlank())
                    ? txtDiscountPercent.getText().trim()
                    : "0";
            discountPercent = new BigDecimal(rawDiscount);
            if (discountPercent.compareTo(BigDecimal.ZERO) < 0 || discountPercent.compareTo(HUNDRED) > 0) {
                AlertUtil.showWarning("Chiết khấu không hợp lệ", "Chiết khấu phải nằm trong khoảng từ 0 đến 100%.");
                return;
            }
        } catch (NumberFormatException e) {
            AlertUtil.showWarning("Chiết khấu không hợp lệ", "Vui lòng nhập số hợp lệ cho chiết khấu (%).");
            return;
        }

        BigDecimal totalAmount = getNetTotal(discountPercent);
        String paymentMethod = (cbPaymentMethod != null && cbPaymentMethod.getValue() != null)
                ? cbPaymentMethod.getValue()
                : OrderBUS.PAYMENT_CASH;

        String paidStr = (txtCashPaid != null && txtCashPaid.getText() != null) ? txtCashPaid.getText().trim() : "";
        if (OrderBUS.PAYMENT_CASH.equals(paymentMethod) || !paidStr.isEmpty()) {
            if (paidStr.isEmpty()) {
                AlertUtil.showWarning("Thiếu tiền thanh toán", "Vui lòng nhập số tiền khách đưa.");
                return;
            }
            try {
                BigDecimal paid = new BigDecimal(paidStr);
                if (paid.compareTo(totalAmount) < 0) {
                    AlertUtil.showWarning(
                            "Tiền khách đưa chưa đủ",
                            "Tổng thanh toán là " + FormatUtil.formatCurrency(totalAmount)
                                    + ", khách mới đưa " + FormatUtil.formatCurrency(paid) + "."
                    );
                    return;
                }
            } catch (NumberFormatException e) {
                AlertUtil.showWarning("Số tiền không hợp lệ", "Vui lòng nhập số tiền khách đưa hợp lệ.");
                return;
            }
        }

        try {
            CustomerDTO selectedCustomer = (cbCustomer != null) ? cbCustomer.getValue() : null;
            Integer customerId = (selectedCustomer != null) ? selectedCustomer.getCustomerId() : null;
            Integer staffId = resolveCurrentStaffId();

            String rawNote = (txtOrderNote != null && !txtOrderNote.getText().isBlank())
                    ? txtOrderNote.getText().trim()
                    : "Bán hàng trực tiếp tại quầy";

            List<OrderDetailDTO> details = new ArrayList<>();
            for (CartItem item : cartData) {
                OrderDetailDTO d = new OrderDetailDTO();
                d.setSpecId(item.getSpecId());
                d.setQuantity(item.getQuantity());
                d.setDiscountPercent(discountPercent);
                details.add(d);
            }

            orderBUS.createOrder(customerId, staffId, paymentMethod, rawNote, details);

            AlertUtil.showInfo("Thành công", "Thanh toán và tạo hóa đơn thành công!");
            cartData.clear();
            if (txtCashPaid != null) txtCashPaid.clear();
            if (txtDiscountPercent != null) txtDiscountPercent.setText("0");
            if (txtOrderNote != null) txtOrderNote.clear();
            if (cbCustomer != null) cbCustomer.getSelectionModel().selectFirst();
            updateCartTotal();
            loadCatalogData();
        } catch (BusinessException be) {
            AlertUtil.showWarning("Lỗi thanh toán", be.getMessage());
        } catch (Exception ex) {
            AlertUtil.showError("Lỗi hệ thống", ex.getMessage());
        }
    }

    private Image getProductThumbnail(String productName) {
        String slug = toSlug(productName);
        if (productImageCache.containsKey(slug)) {
            return productImageCache.get(slug);
        }

        String[] extensions = {".png", ".jpg", ".jpeg"};
        for (String ext : extensions) {
            String path = "/images/products/" + slug + ext;
            try (InputStream is = getClass().getResourceAsStream(path)) {
                if (is != null) {
                    Image img = new Image(is, 76, 76, true, true);
                    productImageCache.put(slug, img);
                    return img;
                }
            } catch (Exception ignored) {
            }
        }

        Image fallback = createFallbackThumbnail();
        productImageCache.put(slug, fallback);
        return fallback;
    }

    private Image createFallbackThumbnail() {
        int size = 76;
        WritableImage img = new WritableImage(size, size);
        PixelWriter pw = img.getPixelWriter();

        Color bg = Color.web("#f3e8ff");
        Color border = Color.web("#d8b4fe");
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean isBorder = x < 2 || x >= size - 2 || y < 2 || y >= size - 2;
                pw.setColor(x, y, isBorder ? border : bg);
            }
        }
        return img;
    }

    private String toSlug(String input) {
        if (input == null || input.isBlank()) return "default-phone";
        String normalized = Normalizer.normalize(input.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private String safeStr(String s) {
        return s == null ? "" : s;
    }

    public static class CartItem {
        private final int specId;
        private final String itemName;
        private final BigDecimal unitPrice;
        private final int maxStock;
        private int quantity;

        public CartItem(int specId, String itemName, BigDecimal unitPrice, int quantity, int maxStock) {
            this.specId = specId;
            this.itemName = itemName;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
            this.maxStock = maxStock;
        }

        public int getSpecId() { return specId; }
        public String getItemName() { return itemName; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public int getMaxStock() { return maxStock; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }

        public BigDecimal getGrossSubTotal() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(0, RoundingMode.HALF_UP);
        }
    }
}
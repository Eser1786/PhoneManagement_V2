package com.phonestore.gui;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.phonestore.bus.ProductBUS;
import com.phonestore.dto.LookupItem;
import com.phonestore.dto.ProductDTO;
import com.phonestore.dto.ProductFilter;
import com.phonestore.dto.ProductListItemDTO;
import com.phonestore.dto.ProductSpecDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.AlertUtil;
import com.phonestore.util.FormatUtil;
import com.phonestore.util.ProductStatuses;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;

public class ProductController {

    // Bộ lọc trên cùng
    @FXML private TextField txtSearch;
    @FXML private ComboBox<LookupItem> cbBrandFilter;
    @FXML private ComboBox<LookupItem> cbCategoryFilter;
    @FXML private ComboBox<String> cbStatusFilter;
    @FXML private TextField txtMinPrice;
    @FXML private TextField txtMaxPrice;

    // Bảng danh sách
    @FXML private TableView<ProductListItemDTO> tblProducts;
    @FXML private TableColumn<ProductListItemDTO, Integer> colSpecId;
    @FXML private TableColumn<ProductListItemDTO, Void> colImage;
    @FXML private TableColumn<ProductListItemDTO, String> colProductName;
    @FXML private TableColumn<ProductListItemDTO, String> colBrand;
    @FXML private TableColumn<ProductListItemDTO, String> colCategory;
    @FXML private TableColumn<ProductListItemDTO, String> colConfig;
    @FXML private TableColumn<ProductListItemDTO, String> colPrice;
    @FXML private TableColumn<ProductListItemDTO, Integer> colStock;
    @FXML private TableColumn<ProductListItemDTO, String> colStatus;

    // Form phải
    @FXML private Label lblFormTitle;
    @FXML private ImageView imgProductPreview;
    @FXML private Label lblImageSlugHint;
    @FXML private TextField txtProductName;
    @FXML private ComboBox<LookupItem> cbFormBrand;
    @FXML private ComboBox<LookupItem> cbFormCategory;
    @FXML private TextArea txtDescription;

    @FXML private TextField txtRam;
    @FXML private TextField txtStorage;
    @FXML private TextField txtColor;
    @FXML private ComboBox<String> cbSpecStatus;
    @FXML private TextField txtPrice;
    @FXML private TextField txtCostPrice;
    @FXML private TextField txtStock;
    @FXML private TextField txtScreenSize;
    @FXML private TextField txtProcessor;
    @FXML private TextField txtResolution;
    @FXML private TextField txtBattery;
    @FXML private TextField txtOs;
    @FXML private TextField txtCameraMain;
    @FXML private TextField txtCameraFront;
    @FXML private TextField txtWeight;
    @FXML private TextField txtWarranty;

    private final ProductBUS productBUS = new ProductBUS();
    private final ObservableList<ProductListItemDTO> productRows = FXCollections.observableArrayList();
    private final Map<String, Image> productImageCache = new HashMap<>();

    private Integer selectedProductId = null;
    private Integer selectedSpecId = null;

    @FXML
    public void initialize() {
        setupTable();
        setupFilterAndFormControls();
        loadLookupOptions();
        loadProducts();
        updatePreviewImage(null);

        tblProducts.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> {
            if (newRow != null) {
                populateFormFromRow(newRow);
            }
        });

        txtProductName.textProperty().addListener((obs, oldVal, newVal) -> updatePreviewImage(newVal));
    }

    private void setupTable() {
        tblProducts.setFixedCellSize(50.0);
        colSpecId.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getSpecId()));

        colImage.setCellFactory(col -> new TableCell<>() {
            private final ImageView imageView = new ImageView();
            private final StackPane box = new StackPane(imageView);

            {
                double size = 42.0;
                imageView.setFitWidth(size);
                imageView.setFitHeight(size);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);

                Rectangle clip = new Rectangle(size, size);
                clip.setArcWidth(8.0);
                clip.setArcHeight(8.0);
                imageView.setClip(clip);

                box.setMinSize(size, size);
                box.setPrefSize(size, size);
                box.setMaxSize(size, size);
                box.setAlignment(Pos.CENTER);
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    setText(null);
                } else {
                    ProductListItemDTO row = getTableView().getItems().get(getIndex());
                    imageView.setImage(getProductThumbnail(row != null ? row.getProductName() : null));
                    setGraphic(box);
                    setText(null);
                }
            }
        });

        colProductName.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(safeStr(c.getValue().getProductName())));
        colBrand.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(safeStr(c.getValue().getBrandName())));
        colCategory.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(safeStr(c.getValue().getCategoryName())));
        colConfig.setCellValueFactory(c -> {
            ProductListItemDTO r = c.getValue();
            String cfg = String.format("%s / %s / %s",
                    safeStr(r.getRam()), safeStr(r.getStorage()), safeStr(r.getColor()));
            return new ReadOnlyObjectWrapper<>(cfg);
        });
        colPrice.setCellValueFactory(c ->
                new ReadOnlyObjectWrapper<>(c.getValue().getPrice() != null
                        ? FormatUtil.formatCurrency(c.getValue().getPrice())
                        : "0₫"));
        colPrice.setStyle("-fx-alignment: CENTER-RIGHT;");
        colStock.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getStockQuantity()));
        colStock.setStyle("-fx-alignment: CENTER;");
        colStatus.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(safeStr(c.getValue().getStatus())));

        tblProducts.setItems(productRows);
    }

    private void setupFilterAndFormControls() {
        ObservableList<String> statusFilters = FXCollections.observableArrayList("Tất cả trạng thái");
        statusFilters.addAll(ProductStatuses.ALL);
        cbStatusFilter.setItems(statusFilters);
        cbStatusFilter.getSelectionModel().selectFirst();

        cbSpecStatus.setItems(FXCollections.observableArrayList(ProductStatuses.ALL));
        cbSpecStatus.getSelectionModel().selectFirst();

        if (imgProductPreview != null) {
            Rectangle clip = new Rectangle(64.0, 64.0);
            clip.setArcWidth(10.0);
            clip.setArcHeight(10.0);
            imgProductPreview.setClip(clip);
        }

        StringConverter<LookupItem> lookupConverter = new StringConverter<>() {
            @Override
            public String toString(LookupItem item) {
                if (item == null) return "";
                String label = invokeString(item, "getName", "getLabel", "getTitle", "getValue");
                return label.isBlank() ? ("#" + getLookupId(item)) : label;
            }

            @Override
            public LookupItem fromString(String string) {
                return null;
            }
        };

        cbBrandFilter.setConverter(lookupConverter);
        cbCategoryFilter.setConverter(lookupConverter);
        cbFormBrand.setConverter(lookupConverter);
        cbFormCategory.setConverter(lookupConverter);
    }

    private void loadLookupOptions() {
        try {
            List<LookupItem> brands = productBUS.getBrandOptions();
            List<LookupItem> categories = productBUS.getCategoryOptions();

            LookupItem allBrand = new LookupItem(0, "Tất cả hãng");
            ObservableList<LookupItem> brandFilterList = FXCollections.observableArrayList();
            brandFilterList.add(allBrand);
            brandFilterList.addAll(brands);
            cbBrandFilter.setItems(brandFilterList);
            cbBrandFilter.getSelectionModel().selectFirst();

            cbFormBrand.setItems(FXCollections.observableArrayList(brands));
            if (!brands.isEmpty()) cbFormBrand.getSelectionModel().selectFirst();

            LookupItem allCat = new LookupItem(0, "Tất cả danh mục");
            ObservableList<LookupItem> catFilterList = FXCollections.observableArrayList();
            catFilterList.add(allCat);
            catFilterList.addAll(categories);
            cbCategoryFilter.setItems(catFilterList);
            cbCategoryFilter.getSelectionModel().selectFirst();

            cbFormCategory.setItems(FXCollections.observableArrayList(categories));
            if (!categories.isEmpty()) cbFormCategory.getSelectionModel().selectFirst();
        } catch (BusinessException ex) {
            AlertUtil.showWarning("Thông báo", ex.getMessage());
        } catch (Exception ex) {
            AlertUtil.showError("Lỗi tải danh mục/hãng", ex.getMessage());
        }
    }

    private int getLookupId(LookupItem item) {
        if (item == null) return 0;
        return invokeInt(item, "getId", "getBrandId", "getCategoryId", "getValueId");
    }

    @FXML
    private void onRefresh() {
        productImageCache.clear();
        loadLookupOptions();
        loadProducts();
        updatePreviewImage(txtProductName.getText());
    }

    @FXML
    private void onFilter() {
        loadProducts();
    }

    @FXML
    private void onResetFilter() {
        txtSearch.clear();
        cbBrandFilter.getSelectionModel().selectFirst();
        cbCategoryFilter.getSelectionModel().selectFirst();
        cbStatusFilter.getSelectionModel().selectFirst();
        txtMinPrice.clear();
        txtMaxPrice.clear();
        loadProducts();
    }

    private void loadProducts() {
        try {
            ProductFilter filter = new ProductFilter();
            String kw = txtSearch.getText();
            if (kw != null && !kw.isBlank()) {
                filter.setKeyword(kw.trim());
            }

            int brandId = getLookupId(cbBrandFilter.getValue());
            if (brandId > 0) filter.setBrandId(brandId);

            int categoryId = getLookupId(cbCategoryFilter.getValue());
            if (categoryId > 0) filter.setCategoryId(categoryId);

            String status = cbStatusFilter.getValue();
            if (status != null && !"Tất cả trạng thái".equals(status) && !status.isBlank()) {
                filter.setStatus(status);
            }

            BigDecimal minPrice = parseOptionalDecimal(txtMinPrice.getText(), "Giá từ");
            BigDecimal maxPrice = parseOptionalDecimal(txtMaxPrice.getText(), "Giá đến");
            filter.setMinPrice(minPrice);
            filter.setMaxPrice(maxPrice);

            productRows.setAll(productBUS.searchProducts(filter));
        } catch (BusinessException ex) {
            AlertUtil.showWarning("Cảnh báo lọc sản phẩm", ex.getMessage());
        } catch (Exception ex) {
            AlertUtil.showError("Lỗi tải sản phẩm", ex.getMessage());
        }
    }

    private void populateFormFromRow(ProductListItemDTO row) {
        selectedProductId = row.getProductId();
        selectedSpecId = row.getSpecId();
        lblFormTitle.setText("Chỉnh sửa — SP #" + selectedProductId + " / PB #" + selectedSpecId);

        try {
            ProductDTO product = productBUS.getProduct(selectedProductId);
            txtProductName.setText(safeStr(product.getName()));
            txtDescription.setText(safeStr(product.getDescription()));
            selectLookupById(cbFormBrand, product.getBrandId());
            selectLookupById(cbFormCategory, product.getCategoryId());
        } catch (Exception ignored) {
            txtProductName.setText(safeStr(row.getProductName()));
        }

        updatePreviewImage(txtProductName.getText());

        try {
            ProductSpecDTO spec = productBUS.getSpec(selectedSpecId);
            txtRam.setText(safeStr(spec.getRam()));
            txtStorage.setText(safeStr(spec.getStorage()));
            txtColor.setText(safeStr(spec.getColor()));
            cbSpecStatus.setValue(spec.getStatus() != null ? spec.getStatus() : ProductStatuses.ON_SALE);
            txtPrice.setText(spec.getPrice() != null ? spec.getPrice().toPlainString() : "");
            txtCostPrice.setText(spec.getCostPrice() != null ? spec.getCostPrice().toPlainString() : "0");
            txtStock.setText(String.valueOf(spec.getStockQuantity()));
            txtScreenSize.setText(defaultIfBlank(spec.getScreenSize(), "6.3 inch"));
            txtProcessor.setText(defaultIfBlank(spec.getProcessor(), "Snapdragon / Apple"));
            txtResolution.setText(defaultIfBlank(spec.getResolution(), "2400 x 1080"));
            txtBattery.setText(defaultIfBlank(spec.getBattery(), "5000 mAh"));
            txtOs.setText(defaultIfBlank(spec.getOs(), "Android / iOS"));
            txtCameraMain.setText(defaultIfBlank(spec.getCameraMain(), "50MP"));
            txtCameraFront.setText(defaultIfBlank(spec.getCameraFront(), "12MP"));
            txtWeight.setText(defaultIfBlank(spec.getWeight(), "195g"));
            txtWarranty.setText(defaultIfBlank(spec.getWarrantyPeriod(), "12 tháng"));
        } catch (Exception ignored) {
            txtRam.setText(safeStr(row.getRam()));
            txtStorage.setText(safeStr(row.getStorage()));
            txtColor.setText(safeStr(row.getColor()));
            txtPrice.setText(row.getPrice() != null ? row.getPrice().toPlainString() : "");
            txtStock.setText(String.valueOf(row.getStockQuantity()));
            cbSpecStatus.setValue(safeStr(row.getStatus()));
        }
    }

    private void selectLookupById(ComboBox<LookupItem> combo, Integer targetId) {
        if (combo == null || targetId == null) return;
        for (LookupItem item : combo.getItems()) {
            if (getLookupId(item) == targetId) {
                combo.getSelectionModel().select(item);
                return;
            }
        }
    }

    @FXML
    private void onNewForm() {
        selectedProductId = null;
        selectedSpecId = null;
        tblProducts.getSelectionModel().clearSelection();
        lblFormTitle.setText("Thêm sản phẩm mới");

        txtProductName.clear();
        txtDescription.clear();
        txtRam.clear();
        txtStorage.clear();
        txtColor.clear();
        txtPrice.clear();
        txtCostPrice.setText("0");
        txtStock.setText("0");
        cbSpecStatus.getSelectionModel().selectFirst();
        updatePreviewImage(null);

        if (!cbFormBrand.getItems().isEmpty()) cbFormBrand.getSelectionModel().selectFirst();
        if (!cbFormCategory.getItems().isEmpty()) cbFormCategory.getSelectionModel().selectFirst();
    }

    @FXML
    private void onCreateProduct() {
        try {
            ProductDTO product = buildProductFromForm(null);
            ProductSpecDTO spec = buildSpecFromForm(null, null, true);

            int newProductId = productBUS.createProduct(product, List.of(spec));
            AlertUtil.showInfo("Thành công", "Đã tạo sản phẩm mới (Mã SP #" + newProductId + ").");
            loadProducts();
            onNewForm();
        } catch (BusinessException ex) {
            AlertUtil.showWarning("Không thể tạo sản phẩm", ex.getMessage());
        } catch (Exception ex) {
            AlertUtil.showError("Lỗi hệ thống", ex.getMessage());
        }
    }

    @FXML
    private void onAddSpecToSelectedProduct() {
        if (selectedProductId == null || selectedProductId <= 0) {
            AlertUtil.showWarning("Chưa chọn sản phẩm", "Vui lòng chọn một sản phẩm ở bảng bên trái để thêm phiên bản.");
            return;
        }
        try {
            ProductSpecDTO spec = buildSpecFromForm(null, selectedProductId, true);
            int newSpecId = productBUS.addSpec(spec);
            AlertUtil.showInfo("Thành công", "Đã thêm phiên bản cấu hình mới (Mã PB #" + newSpecId + ").");
            loadProducts();
        } catch (BusinessException ex) {
            AlertUtil.showWarning("Không thể thêm phiên bản", ex.getMessage());
        } catch (Exception ex) {
            AlertUtil.showError("Lỗi hệ thống", ex.getMessage());
        }
    }

    @FXML
    private void onUpdateSelected() {
        if (selectedProductId == null || selectedSpecId == null) {
            AlertUtil.showWarning("Chưa chọn dòng", "Vui lòng chọn một sản phẩm / phiên bản trong bảng để cập nhật.");
            return;
        }
        try {
            ProductDTO product = buildProductFromForm(selectedProductId);
            ProductSpecDTO spec = buildSpecFromForm(selectedSpecId, selectedProductId, false);

            productBUS.updateProduct(product);
            productBUS.updateSpec(spec);
            AlertUtil.showInfo("Thành công", "Đã cập nhật sản phẩm và phiên bản cấu hình (tồn kho giữ nguyên).");
            loadProducts();
        } catch (BusinessException ex) {
            AlertUtil.showWarning("Không thể cập nhật", ex.getMessage());
        } catch (Exception ex) {
            AlertUtil.showError("Lỗi hệ thống", ex.getMessage());
        }
    }

    @FXML
    private void onDeleteSpec() {
        if (selectedSpecId == null || selectedSpecId <= 0) {
            AlertUtil.showWarning("Chưa chọn phiên bản", "Vui lòng chọn một phiên bản cấu hình để xóa.");
            return;
        }
        boolean confirmed = AlertUtil.showConfirm(
                "Xác nhận xóa phiên bản",
                "Bạn có chắc muốn xóa phiên bản #" + selectedSpecId + "? (Nếu đã có lịch sử bán/kho sẽ chuyển Ngừng kinh doanh)."
        );
        if (!confirmed) return;

        try {
            boolean hardDeleted = productBUS.deleteSpec(selectedSpecId);
            AlertUtil.showInfo("Hoàn tất", hardDeleted
                    ? "Đã xóa vĩnh viễn phiên bản #" + selectedSpecId + "."
                    : "Phiên bản đã có lịch sử giao dịch nên được chuyển sang 'Ngừng kinh doanh'.");
            loadProducts();
            onNewForm();
        } catch (BusinessException ex) {
            AlertUtil.showWarning("Không thể xóa phiên bản", ex.getMessage());
        } catch (Exception ex) {
            AlertUtil.showError("Lỗi hệ thống", ex.getMessage());
        }
    }

    @FXML
    private void onDeleteProduct() {
        if (selectedProductId == null || selectedProductId <= 0) {
            AlertUtil.showWarning("Chưa chọn sản phẩm", "Vui lòng chọn một sản phẩm để xóa.");
            return;
        }
        boolean confirmed = AlertUtil.showConfirm(
                "Xác nhận xóa sản phẩm",
                "Bạn có chắc muốn xóa sản phẩm #" + selectedProductId + "? (Nếu đã có lịch sử bán/kho sẽ chuyển Ngừng kinh doanh)."
        );
        if (!confirmed) return;

        try {
            boolean hardDeleted = productBUS.deleteProduct(selectedProductId);
            AlertUtil.showInfo("Hoàn tất", hardDeleted
                    ? "Đã xóa vĩnh viễn sản phẩm #" + selectedProductId + "."
                    : "Sản phẩm đã có lịch sử giao dịch nên được chuyển sang 'Ngừng kinh doanh'.");
            loadProducts();
            onNewForm();
        } catch (BusinessException ex) {
            AlertUtil.showWarning("Không thể xóa sản phẩm", ex.getMessage());
        } catch (Exception ex) {
            AlertUtil.showError("Lỗi hệ thống", ex.getMessage());
        }
    }

    private void updatePreviewImage(String productName) {
        if (imgProductPreview != null) {
            imgProductPreview.setImage(getProductThumbnail(productName));
        }
        if (lblImageSlugHint != null) {
            String slug = toSlug(productName);
            lblImageSlugHint.setText(slug.isBlank()
                    ? "Đặt file <tên-sp>.png/.jpg vào images/products/"
                    : "File khớp: images/products/" + slug + ".png");
        }
    }

    private Image getProductThumbnail(String productName) {
        String slug = toSlug(productName);
        if (productImageCache.containsKey(slug)) {
            return productImageCache.get(slug);
        }

        if (!slug.isBlank()) {
            for (String ext : new String[]{".png", ".jpg", ".jpeg"}) {
                String path = "/images/products/" + slug + ext;
                try (InputStream is = getClass().getResourceAsStream(path)) {
                    if (is != null) {
                        Image img = new Image(is, 80, 80, true, true);
                        productImageCache.put(slug, img);
                        return img;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        Image fallback = createFallbackThumbnail();
        productImageCache.put(slug, fallback);
        return fallback;
    }

    private Image createFallbackThumbnail() {
        int size = 80;
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
        if (input == null || input.isBlank()) return "";
        String normalized = Normalizer.normalize(input.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private ProductDTO buildProductFromForm(Integer productId) {
        ProductDTO dto = new ProductDTO();
        if (productId != null) dto.setProductId(productId);
        dto.setName(txtProductName.getText());
        dto.setDescription(txtDescription.getText());

        int brandId = getLookupId(cbFormBrand.getValue());
        int categoryId = getLookupId(cbFormCategory.getValue());
        dto.setBrandId(brandId > 0 ? brandId : null);
        dto.setCategoryId(categoryId > 0 ? categoryId : null);
        return dto;
    }

    private ProductSpecDTO buildSpecFromForm(Integer specId, Integer productId, boolean requireStock) {
        ProductSpecDTO spec = new ProductSpecDTO();
        if (specId != null) spec.setSpecId(specId);
        if (productId != null) spec.setProductId(productId);

        spec.setPrice(parseDecimalField(txtPrice.getText(), "Giá bán"));
        spec.setCostPrice(parseDecimalField(txtCostPrice.getText(), "Giá vốn"));

        if (requireStock) {
            spec.setStockQuantity(parseIntField(txtStock.getText(), "Tồn kho ban đầu"));
        }

        spec.setRam(txtRam.getText());
        spec.setStorage(txtStorage.getText());
        spec.setColor(txtColor.getText());
        spec.setStatus(cbSpecStatus.getValue());

        spec.setScreenSize(txtScreenSize.getText());
        spec.setProcessor(txtProcessor.getText());
        spec.setResolution(txtResolution.getText());
        spec.setBattery(txtBattery.getText());
        spec.setOs(txtOs.getText());
        spec.setCameraMain(txtCameraMain.getText());
        spec.setCameraFront(txtCameraFront.getText());
        spec.setWeight(txtWeight.getText());
        spec.setWarrantyPeriod(txtWarranty.getText());
        return spec;
    }

    private BigDecimal parseOptionalDecimal(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) return null;
        return parseDecimalField(raw, fieldName);
    }

    private BigDecimal parseDecimalField(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("Vui lòng nhập " + fieldName);
        }
        try {
            return new BigDecimal(raw.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            throw new BusinessException(fieldName + " phải là số hợp lệ");
        }
    }

    private int parseIntField(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("Vui lòng nhập " + fieldName);
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(fieldName + " phải là số nguyên hợp lệ");
        }
    }

    private String defaultIfBlank(String val, String fallback) {
        return (val == null || val.isBlank()) ? fallback : val;
    }

    private String safeStr(String s) {
        return s == null ? "" : s;
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
}
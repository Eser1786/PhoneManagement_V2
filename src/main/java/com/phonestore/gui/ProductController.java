package com.phonestore.gui;

import com.phonestore.bus.BrandBUS;
import com.phonestore.bus.CategoryBUS;
import com.phonestore.bus.ProductBUS;
import com.phonestore.dto.BrandDTO;
import com.phonestore.dto.CategoryDTO;
import com.phonestore.dto.ProductDTO;
import com.phonestore.dto.ProductFilter;
import com.phonestore.dto.ProductListItemDTO;
import com.phonestore.dto.ProductSpecDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.AlertUtil;
import com.phonestore.util.FormatUtil;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.List;

public class ProductController {

    @FXML private StackPane rootView;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<BrandDTO> cbBrandFilter;
    @FXML private ComboBox<CategoryDTO> cbCategoryFilter;
    @FXML private ComboBox<String> cbPriceFilter;
    @FXML private ComboBox<String> cbStatusFilter;
    @FXML private Label lblTotalItems;

    @FXML private TableView<ProductListItemDTO> tblProducts;
    @FXML private TableColumn<ProductListItemDTO, Integer> colIndex;
    @FXML private TableColumn<ProductListItemDTO, String> colCode;
    @FXML private TableColumn<ProductListItemDTO, String> colName;
    @FXML private TableColumn<ProductListItemDTO, String> colBrand;
    @FXML private TableColumn<ProductListItemDTO, String> colCategory;
    @FXML private TableColumn<ProductListItemDTO, String> colPrice;
    @FXML private TableColumn<ProductListItemDTO, String> colCostPrice;
    @FXML private TableColumn<ProductListItemDTO, Integer> colStock;
    @FXML private TableColumn<ProductListItemDTO, String> colStatus;
    @FXML private TableColumn<ProductListItemDTO, Void> colActions;

    @FXML private Pagination pagination;

    // Modal elements
    @FXML private StackPane modalOverlay;
    @FXML private Label lblModalTitle;
    @FXML private TextField txtFormName;
    @FXML private ComboBox<BrandDTO> cbFormBrand;
    @FXML private ComboBox<CategoryDTO> cbFormCategory;
    @FXML private TextField txtFormRam;
    @FXML private TextField txtFormStorage;
    @FXML private TextField txtFormColor;
    @FXML private TextField txtFormPrice;
    @FXML private TextField txtFormCostPrice;
    @FXML private TextField txtFormStock;
    @FXML private ComboBox<String> cbFormStatus;
    @FXML private TextArea txtFormDescription;

    private final ProductBUS productBUS = new ProductBUS();
    private final BrandBUS brandBUS = new BrandBUS();
    private final CategoryBUS categoryBUS = new CategoryBUS();

    private final ObservableList<ProductListItemDTO> masterData = FXCollections.observableArrayList();
    private static final int ROWS_PER_PAGE = 10;
    private ProductListItemDTO editingItem = null;

    @FXML
    public void initialize() {
        initFilters();
        initTableColumns();
        loadBrandsAndCategories();
        loadProductData();

        // Lắng nghe sự kiện tìm kiếm và lọc
        txtSearch.textProperty().addListener((obs, o, n) -> loadProductData());
        cbBrandFilter.setOnAction(e -> loadProductData());
        cbCategoryFilter.setOnAction(e -> loadProductData());
        cbPriceFilter.setOnAction(e -> loadProductData());
        cbStatusFilter.setOnAction(e -> loadProductData());

        pagination.currentPageIndexProperty().addListener((obs, oldIdx, newIdx) -> updateTablePage(newIdx.intValue()));
    }

    private void initFilters() {
        cbPriceFilter.setItems(FXCollections.observableArrayList(
                "Tất cả giá", "Dưới 5 triệu", "5 - 15 triệu", "15 - 25 triệu", "Trên 25 triệu"
        ));
        cbPriceFilter.getSelectionModel().selectFirst();

        cbStatusFilter.setItems(FXCollections.observableArrayList(
                "Tất cả trạng thái", "Đang bán", "Hết hàng", "Sắp hết", "Ngừng bán"
        ));
        cbStatusFilter.getSelectionModel().selectFirst();

        cbFormStatus.setItems(FXCollections.observableArrayList("Đang bán", "Hết hàng", "Ngừng bán"));
    }

    private void initTableColumns() {
        colIndex.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(tblProducts.getItems().indexOf(cellData.getValue()) + 1));
        colIndex.setStyle("-fx-alignment: CENTER;");

        colCode.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(String.format("SP-%04d", cellData.getValue().getSpecId())));

        colName.setCellValueFactory(cellData -> {
            ProductListItemDTO item = cellData.getValue();
            String details = item.getProductName();
            if (item.getRam() != null && !item.getRam().isBlank()) details += " • " + item.getRam();
            if (item.getStorage() != null && !item.getStorage().isBlank()) details += " / " + item.getStorage();
            if (item.getColor() != null && !item.getColor().isBlank()) details += " • " + item.getColor();
            return new ReadOnlyObjectWrapper<>(details);
        });

        colBrand.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getBrandName()));
        colCategory.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getCategoryName()));

        colPrice.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(FormatUtil.formatCurrency(cellData.getValue().getPrice())));
        colPrice.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");

        colCostPrice.setCellValueFactory(cellData -> {
            try {
                ProductSpecDTO spec = productBUS.getSpecById(cellData.getValue().getSpecId());
                return new ReadOnlyObjectWrapper<>(FormatUtil.formatCurrency(spec != null ? spec.getCostPrice() : BigDecimal.ZERO));
            } catch (Exception ex) {
                return new ReadOnlyObjectWrapper<>("0₫");
            }
        });
        colCostPrice.setStyle("-fx-alignment: CENTER-RIGHT;");

        colStock.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getStockQuantity()));
        colStock.setStyle("-fx-alignment: CENTER;");

        colStatus.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getStatus()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    return;
                }
                Label badge = new Label(status);
                badge.getStyleClass().add("app-badge");
                if ("Đang bán".equalsIgnoreCase(status)) badge.getStyleClass().add("app-badge-green");
                else if ("Hết hàng".equalsIgnoreCase(status) || "Ngừng bán".equalsIgnoreCase(status)) badge.getStyleClass().add("app-badge-red");
                else badge.getStyleClass().add("app-badge-yellow");
                setGraphic(badge);
                setAlignment(Pos.CENTER);
            }
        });

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnView = new Button("Xem");
            private final Button btnEdit = new Button("Sửa");
            private final Button btnDelete = new Button("Xóa");
            private final HBox pane = new HBox(6, btnView, btnEdit, btnDelete);

            {
                btnView.getStyleClass().add("app-btn-action");
                btnEdit.getStyleClass().add("app-btn-action");
                btnDelete.getStyleClass().add("app-btn-danger-outline");
                pane.setAlignment(Pos.CENTER);

                btnView.setOnAction(e -> handleView(getTableView().getItems().get(getIndex())));
                btnEdit.setOnAction(e -> handleEdit(getTableView().getItems().get(getIndex())));
                btnDelete.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });
    }

    private void loadBrandsAndCategories() {
        List<BrandDTO> brands = brandBUS.getAllBrands();
        BrandDTO allBrand = new BrandDTO(0, "Tất cả hãng", "", "", "");
        ObservableList<BrandDTO> brandList = FXCollections.observableArrayList();
        brandList.add(allBrand);
        brandList.addAll(brands);
        cbBrandFilter.setItems(brandList);
        cbBrandFilter.getSelectionModel().selectFirst();

        cbFormBrand.setItems(FXCollections.observableArrayList(brands));

        List<CategoryDTO> categories = categoryBUS.getAllCategories();
        CategoryDTO allCat = new CategoryDTO(0, "Tất cả danh mục", "");
        ObservableList<CategoryDTO> catList = FXCollections.observableArrayList();
        catList.add(allCat);
        catList.addAll(categories);
        cbCategoryFilter.setItems(catList);
        cbCategoryFilter.getSelectionModel().selectFirst();

        cbFormCategory.setItems(FXCollections.observableArrayList(categories));

        StringConverter<BrandDTO> brandConverter = new StringConverter<>() {
            @Override public String toString(BrandDTO b) { return b == null ? "" : b.getBrandName(); }
            @Override public BrandDTO fromString(String string) { return null; }
        };
        cbBrandFilter.setConverter(brandConverter);
        cbFormBrand.setConverter(brandConverter);

        StringConverter<CategoryDTO> catConverter = new StringConverter<>() {
            @Override public String toString(CategoryDTO c) { return c == null ? "" : c.getCategoryName(); }
            @Override public CategoryDTO fromString(String string) { return null; }
        };
        cbCategoryFilter.setConverter(catConverter);
        cbFormCategory.setConverter(catConverter);
    }

    private void loadProductData() {
        ProductFilter filter = new ProductFilter();
        String kw = txtSearch.getText();
        if (kw != null && !kw.isBlank()) filter.setKeyword(kw.trim());

        BrandDTO selBrand = cbBrandFilter.getSelectionModel().getSelectedItem();
        if (selBrand != null && selBrand.getBrandId() > 0) filter.setBrandId(selBrand.getBrandId());

        CategoryDTO selCat = cbCategoryFilter.getSelectionModel().getSelectedItem();
        if (selCat != null && selCat.getCategoryId() > 0) filter.setCategoryId(selCat.getCategoryId());

        String status = cbStatusFilter.getSelectionModel().getSelectedItem();
        if (status != null && !status.startsWith("Tất cả")) filter.setStatus(status);

        String priceRange = cbPriceFilter.getSelectionModel().getSelectedItem();
        if (priceRange != null) {
            switch (priceRange) {
                case "Dưới 5 triệu" -> { filter.setMinPrice(BigDecimal.ZERO); filter.setMaxPrice(new BigDecimal("5000000")); }
                case "5 - 15 triệu" -> { filter.setMinPrice(new BigDecimal("5000000")); filter.setMaxPrice(new BigDecimal("15000000")); }
                case "15 - 25 triệu" -> { filter.setMinPrice(new BigDecimal("15000000")); filter.setMaxPrice(new BigDecimal("25000000")); }
                case "Trên 25 triệu" -> filter.setMinPrice(new BigDecimal("25000000"));
            }
        }

        try {
            List<ProductListItemDTO> items = productBUS.searchProducts(filter);
            masterData.setAll(items);
            lblTotalItems.setText(FormatUtil.formatNumber(items.size()) + " sản phẩm");

            int pageCount = (int) Math.ceil((double) items.size() / ROWS_PER_PAGE);
            pagination.setPageCount(Math.max(pageCount, 1));
            updateTablePage(0);
        } catch (Exception ex) {
            AlertUtil.showError("Lỗi tải dữ liệu", ex.getMessage());
        }
    }

    private void updateTablePage(int pageIndex) {
        int from = pageIndex * ROWS_PER_PAGE;
        int to = Math.min(from + ROWS_PER_PAGE, masterData.size());
        if (from <= to && from < masterData.size()) {
            tblProducts.setItems(FXCollections.observableArrayList(masterData.subList(from, to)));
        } else {
            tblProducts.setItems(FXCollections.observableArrayList());
        }
    }

    @FXML
    private void onResetFilter() {
        txtSearch.clear();
        cbBrandFilter.getSelectionModel().selectFirst();
        cbCategoryFilter.getSelectionModel().selectFirst();
        cbPriceFilter.getSelectionModel().selectFirst();
        cbStatusFilter.getSelectionModel().selectFirst();
        loadProductData();
    }

    @FXML
    private void onOpenAddModal() {
        editingItem = null;
        lblModalTitle.setText("Thêm sản phẩm mới");
        clearModalForm();
        modalOverlay.setVisible(true);
    }

    @FXML
    private void onCloseModal() {
        modalOverlay.setVisible(false);
    }

    private void clearModalForm() {
        txtFormName.clear();
        cbFormBrand.getSelectionModel().clearSelection();
        cbFormCategory.getSelectionModel().clearSelection();
        txtFormRam.clear();
        txtFormStorage.clear();
        txtFormColor.clear();
        txtFormPrice.clear();
        txtFormCostPrice.clear();
        txtFormStock.clear();
        cbFormStatus.getSelectionModel().select("Đang bán");
        txtFormDescription.clear();
    }

    private void handleView(ProductListItemDTO item) {
        try {
            ProductDTO p = productBUS.getProductById(item.getProductId());
            ProductSpecDTO s = productBUS.getSpecById(item.getSpecId());
            String info = "Tên: " + item.getProductName() + "\n"
                    + "Cấu hình: " + s.getRam() + " / " + s.getStorage() + " - " + s.getColor() + "\n"
                    + "Giá bán: " + FormatUtil.formatCurrency(s.getPrice()) + "\n"
                    + "Giá vốn: " + FormatUtil.formatCurrency(s.getCostPrice()) + "\n"
                    + "Tồn kho: " + s.getStockQuantity() + " máy\n"
                    + "Trạng thái: " + s.getStatus() + "\n"
                    + "Mô tả: " + (p.getDescription() != null ? p.getDescription() : "Không có");
            AlertUtil.showInfo("Chi tiết sản phẩm #" + item.getSpecId(), info);
        } catch (Exception ex) {
            AlertUtil.showError("Lỗi", ex.getMessage());
        }
    }

    private void handleEdit(ProductListItemDTO item) {
        try {
            editingItem = item;
            ProductDTO p = productBUS.getProductById(item.getProductId());
            ProductSpecDTO s = productBUS.getSpecById(item.getSpecId());

            lblModalTitle.setText("Cập nhật sản phẩm: " + item.getProductName());
            txtFormName.setText(p.getName());
            cbFormBrand.getItems().stream().filter(b -> b.getBrandId() == p.getBrandId()).findFirst().ifPresent(cbFormBrand::setValue);
            cbFormCategory.getItems().stream().filter(c -> c.getCategoryId() == p.getCategoryId()).findFirst().ifPresent(cbFormCategory::setValue);

            txtFormRam.setText(s.getRam());
            txtFormStorage.setText(s.getStorage());
            txtFormColor.setText(s.getColor());
            txtFormPrice.setText(s.getPrice() != null ? s.getPrice().toPlainString() : "");
            txtFormCostPrice.setText(s.getCostPrice() != null ? s.getCostPrice().toPlainString() : "");
            txtFormStock.setText(String.valueOf(s.getStockQuantity()));
            cbFormStatus.setValue(s.getStatus());
            txtFormDescription.setText(p.getDescription());

            modalOverlay.setVisible(true);
        } catch (Exception ex) {
            AlertUtil.showError("Lỗi đọc dữ liệu", ex.getMessage());
        }
    }

    private void handleDelete(ProductListItemDTO item) {
        if (AlertUtil.showConfirm("Xác nhận xóa", "Bạn có chắc muốn xóa sản phẩm \"" + item.getProductName() + "\"?")) {
            try {
                productBUS.deleteProduct(item.getProductId());
                AlertUtil.showInfo("Thành công", "Đã xóa sản phẩm thành công.");
                loadProductData();
            } catch (BusinessException be) {
                AlertUtil.showWarning("Không thể xóa", be.getMessage());
            } catch (Exception ex) {
                AlertUtil.showError("Lỗi hệ thống", ex.getMessage());
            }
        }
    }

    @FXML
    private void onSaveProduct() {
        try {
            String name = txtFormName.getText() != null ? txtFormName.getText().trim() : "";
            BrandDTO brand = cbFormBrand.getValue();
            CategoryDTO category = cbFormCategory.getValue();

            BigDecimal price = new BigDecimal(txtFormPrice.getText().trim());
            BigDecimal costPrice = new BigDecimal(txtFormCostPrice.getText().trim());
            int stock = Integer.parseInt(txtFormStock.getText().trim());

            if (editingItem == null) {
                ProductDTO p = new ProductDTO(0, name, brand != null ? brand.getBrandId() : null, category != null ? category.getCategoryId() : null, txtFormDescription.getText());
                ProductSpecDTO s = new ProductSpecDTO();
                s.setPrice(price);
                s.setCostPrice(costPrice);
                s.setStockQuantity(stock);
                s.setRam(txtFormRam.getText());
                s.setStorage(txtFormStorage.getText());
                s.setColor(txtFormColor.getText());
                s.setStatus(cbFormStatus.getValue());

                productBUS.addProductWithSpec(p, s);
                AlertUtil.showInfo("Thành công", "Thêm sản phẩm mới thành công!");
            } else {
                ProductDTO p = productBUS.getProductById(editingItem.getProductId());
                p.setName(name);
                p.setBrandId(brand != null ? brand.getBrandId() : null);
                p.setCategoryId(category != null ? category.getCategoryId() : null);
                p.setDescription(txtFormDescription.getText());

                ProductSpecDTO s = productBUS.getSpecById(editingItem.getSpecId());
                s.setPrice(price);
                s.setCostPrice(costPrice);
                s.setStockQuantity(stock);
                s.setRam(txtFormRam.getText());
                s.setStorage(txtFormStorage.getText());
                s.setColor(txtFormColor.getText());
                s.setStatus(cbFormStatus.getValue());

                productBUS.updateProductAndSpec(p, s);
                AlertUtil.showInfo("Thành công", "Cập nhật sản phẩm thành công!");
            }

            modalOverlay.setVisible(false);
            loadProductData();
        } catch (NumberFormatException nfe) {
            AlertUtil.showWarning("Dữ liệu không hợp lệ", "Giá bán, giá vốn và tồn kho phải là số hợp lệ.");
        } catch (BusinessException be) {
            AlertUtil.showWarning("Lỗi dữ liệu", be.getMessage());
        } catch (Exception ex) {
            AlertUtil.showError("Lỗi hệ thống", ex.getMessage());
        }
    }
}
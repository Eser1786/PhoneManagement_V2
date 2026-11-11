package com.phonestore.gui;

import com.phonestore.bus.BrandBUS;
import com.phonestore.bus.CategoryBUS;
import com.phonestore.dto.BrandDTO;
import com.phonestore.dto.CategoryDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.gui.component.CrudPanel;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;

public class BrandCategoryController {

    private static final String DB_ERROR = "Không kết nối được cơ sở dữ liệu. Vui lòng thử lại.";

    private final BrandBUS brandBUS = new BrandBUS();
    private final CategoryBUS categoryBUS = new CategoryBUS();

    @FXML private HBox panels;

    private CrudPanel<BrandDTO> brandPanel;
    private CrudPanel<CategoryDTO> categoryPanel;

    @FXML
    private void initialize() {
        buildBrandPanel();
        buildCategoryPanel();

        panels.getChildren().addAll(brandPanel, categoryPanel);
        for (Node n : panels.getChildren()) {
            ((Region) n).setPrefWidth(0);          // hai khung chia đều chiều rộng
            HBox.setHgrow(n, Priority.ALWAYS);
        }

        reloadBrands();
        reloadCategories();
    }

    // =====================================================
    // THƯƠNG HIỆU
    // =====================================================

    private void buildBrandPanel() {
        brandPanel = new CrudPanel<BrandDTO>("THƯƠNG HIỆU", "Thêm thương hiệu",
                "Tìm kiếm thương hiệu...", "thương hiệu", 6,
                b -> b.getBrandName() + " " + nz(b.getCountry()) + " " + nz(b.getDescription()));

        brandPanel.addIndexColumn(50);
        brandPanel.addNodeColumn("LOGO", 60, this::buildLogo);
        brandPanel.addTextColumn("TÊN THƯƠNG HIỆU", 130, BrandDTO::getBrandName);
        brandPanel.addTextColumn("MÔ TẢ", 200, b -> nz(b.getDescription()));
        brandPanel.addActionColumn(120);

        brandPanel.setOnAdd(() -> showBrandForm(null));
        brandPanel.setOnEdit(this::showBrandForm);
        brandPanel.setOnDelete(this::deleteBrand);
    }

    private void reloadBrands() {
        try {
            brandPanel.setItems(brandBUS.getAllBrands());
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (RuntimeException e) {
            showError(DB_ERROR);
        }
    }

    /** existing == null nghĩa là Thêm mới, khác null nghĩa là Sửa. */
    private void showBrandForm(BrandDTO existing) {
        boolean isEdit = existing != null;

        TextField name = new TextField(isEdit ? nz(existing.getBrandName()) : "");
        TextField country = new TextField(isEdit ? nz(existing.getCountry()) : "");
        TextField logo = new TextField(isEdit ? nz(existing.getLogoUrl()) : "");
        TextArea desc = new TextArea(isEdit ? nz(existing.getDescription()) : "");
        name.setPromptText("Ví dụ: Apple");
        country.setPromptText("Ví dụ: Mỹ");
        logo.setPromptText("Không bắt buộc, ví dụ: images/brands/apple.png");
        desc.setPromptText("Mô tả ngắn về thương hiệu");
        desc.setPrefRowCount(3);
        desc.setWrapText(true);

        GridPane form = newForm();
        addRow(form, 0, "Tên thương hiệu *", name);
        addRow(form, 1, "Quốc gia *", country);
        addRow(form, 2, "Logo", logo);
        addRow(form, 3, "Mô tả", desc);

        Label error = newErrorLabel();

        boolean saved = showForm(
                isEdit ? "Sửa thương hiệu" : "Thêm thương hiệu",
                isEdit ? "Lưu thay đổi" : "Thêm thương hiệu",
                new VBox(12, form, error), error,
                () -> {
                    BrandDTO dto = new BrandDTO();
                    if (isEdit) dto.setBrandId(existing.getBrandId());
                    dto.setBrandName(name.getText());
                    dto.setCountry(country.getText());
                    dto.setLogoUrl(logo.getText());
                    dto.setDescription(desc.getText());
                    try {
                        if (isEdit) brandBUS.updateBrand(dto);
                        else brandBUS.addBrand(dto);
                        return null;                       // null = thành công
                    } catch (BusinessException e) {
                        return e.getMessage();
                    } catch (RuntimeException e) {
                        return DB_ERROR;
                    }
                });

        if (saved) reloadBrands();
    }

    private void deleteBrand(BrandDTO brand) {
        if (!confirmDelete("thương hiệu \"" + brand.getBrandName() + "\"")) return;
        try {
            brandBUS.deleteBrand(brand.getBrandId());
            reloadBrands();
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (RuntimeException e) {
            showError(DB_ERROR);
        }
    }

    // =====================================================
    // DANH MỤC
    // =====================================================

    private void buildCategoryPanel() {
        categoryPanel = new CrudPanel<CategoryDTO>("DANH MỤC", "Thêm danh mục",
                "Tìm kiếm danh mục...", "danh mục", 5,
                c -> c.getCategoryName() + " " + nz(c.getDescription()));

        categoryPanel.addIndexColumn(50);
        categoryPanel.addTextColumn("TÊN DANH MỤC", 150, CategoryDTO::getCategoryName);
        categoryPanel.addTextColumn("MÔ TẢ", 200, c -> nz(c.getDescription()));
        categoryPanel.addActionColumn(120);

        categoryPanel.setOnAdd(() -> showCategoryForm(null));
        categoryPanel.setOnEdit(this::showCategoryForm);
        categoryPanel.setOnDelete(this::deleteCategory);
    }

    private void reloadCategories() {
        try {
            categoryPanel.setItems(categoryBUS.getAllCategories());
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (RuntimeException e) {
            showError(DB_ERROR);
        }
    }

    private void showCategoryForm(CategoryDTO existing) {
        boolean isEdit = existing != null;

        TextField name = new TextField(isEdit ? nz(existing.getCategoryName()) : "");
        TextArea desc = new TextArea(isEdit ? nz(existing.getDescription()) : "");
        name.setPromptText("Ví dụ: Flagship");
        desc.setPromptText("Mô tả ngắn về danh mục");
        desc.setPrefRowCount(3);
        desc.setWrapText(true);

        GridPane form = newForm();
        addRow(form, 0, "Tên danh mục *", name);
        addRow(form, 1, "Mô tả", desc);

        Label error = newErrorLabel();

        boolean saved = showForm(
                isEdit ? "Sửa danh mục" : "Thêm danh mục",
                isEdit ? "Lưu thay đổi" : "Thêm danh mục",
                new VBox(12, form, error), error,
                () -> {
                    CategoryDTO dto = new CategoryDTO();
                    if (isEdit) dto.setCategoryId(existing.getCategoryId());
                    dto.setCategoryName(name.getText());
                    dto.setDescription(desc.getText());
                    try {
                        if (isEdit) categoryBUS.updateCategory(dto);
                        else categoryBUS.addCategory(dto);
                        return null;
                    } catch (BusinessException e) {
                        return e.getMessage();
                    } catch (RuntimeException e) {
                        return DB_ERROR;
                    }
                });

        if (saved) reloadCategories();
    }

    private void deleteCategory(CategoryDTO category) {
        if (!confirmDelete("danh mục \"" + category.getCategoryName() + "\"")) return;
        try {
            categoryBUS.deleteCategory(category.getCategoryId());
            reloadCategories();
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (RuntimeException e) {
            showError(DB_ERROR);
        }
    }

    // =====================================================
    // LOGO TRÒN
    // =====================================================

    private Node buildLogo(BrandDTO brand) {
        double size = 40;
        StackPane box = new StackPane();
        box.setMinSize(size, size);
        box.setMaxSize(size, size);

        Image img = loadLogo(brand.getLogoUrl());
        if (img != null) {
            ImageView view = new ImageView(img);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(true);
            view.setClip(new Circle(size / 2, size / 2, size / 2));
            box.getChildren().add(view);
        } else {
            // Chưa có ảnh: hiện chữ cái đầu của tên thương hiệu
            String text = nz(brand.getBrandName());
            Label letter = new Label(text.isEmpty() ? "?" : text.substring(0, 1).toUpperCase());
            letter.getStyleClass().add("crud-logo-letter");
            box.getStyleClass().add("crud-logo-fallback");
            box.getChildren().add(letter);
        }
        return box;
    }

    // logo_url là đường dẫn trong resources, ví dụ "images/brands/apple.png"
    private Image loadLogo(String path) {
        if (path == null || path.isBlank()) return null;
        String p = path.trim();
        if (p.startsWith("/")) p = p.substring(1);
        try (InputStream in = getClass().getResourceAsStream("/" + p)) {
            if (in == null) return null;
            Image img = new Image(in, 80, 80, true, true);
            return img.isError() ? null : img;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // =====================================================
    // HỘP THOẠI VÀ HÀM PHỤ
    // =====================================================

    /**
     * Mở hộp thoại. onSave trả về null nếu lưu thành công, hoặc nội dung lỗi để hiện trong hộp thoại.
     * Trả về true nếu người dùng đã lưu thành công.
     */
    private boolean showForm(String title, String saveText, Region content, Label error,
                             Supplier<String> onSave) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle(title);
        dlg.initOwner(panels.getScene().getWindow());

        DialogPane pane = dlg.getDialogPane();
        pane.getStylesheets().add(CrudPanel.STYLESHEET);
        pane.getStyleClass().add("crud-dialog");
        content.setPrefWidth(440);
        pane.setContent(content);

        ButtonType saveType = new ButtonType(saveText, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(saveType, cancelType);

        // Bấm Lưu: gọi onSave, nếu có lỗi thì giữ hộp thoại mở và hiện lỗi
        Button saveBtn = (Button) pane.lookupButton(saveType);
        saveBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            String msg = onSave.get();
            if (msg != null) {
                error.setText(msg);
                error.setVisible(true);
                ev.consume();
            }
        });

        return dlg.showAndWait().filter(b -> b == saveType).isPresent();
    }

    private boolean confirmDelete(String what) {
        ButtonType yes = new ButtonType("Xóa", ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Bạn có chắc muốn xóa " + what + "?", yes, no);
        alert.setTitle("Xác nhận xóa");
        alert.setHeaderText(null);
        return alert.showAndWait().filter(b -> b == yes).isPresent();
    }

    // Bọc trong runLater để không bị lỗi khi gọi lúc màn hình đang được nạp
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
            alert.setTitle("Thông báo");
            alert.setHeaderText(null);
            alert.showAndWait();
        });
    }

    private GridPane newForm() {
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        return form;
    }

    private void addRow(GridPane form, int row, String labelText, Node field) {
        Label label = new Label(labelText);
        label.getStyleClass().add("crud-form-label");
        label.setMinWidth(120);
        GridPane.setHgrow(field, Priority.ALWAYS);
        form.addRow(row, label, field);
    }

    private Label newErrorLabel() {
        Label error = new Label();
        error.getStyleClass().add("crud-error");
        error.setWrapText(true);
        error.setMaxWidth(Double.MAX_VALUE);
        error.managedProperty().bind(error.visibleProperty());
        error.setVisible(false);
        return error;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
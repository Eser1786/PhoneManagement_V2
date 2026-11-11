package com.phonestore.gui.component;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Khung "tiêu đề + nút Thêm + tìm kiếm + bảng + phân trang" dùng chung cho các màn CRUD. */
public class CrudPanel<T> extends VBox {

    public static final String STYLESHEET =
            CrudPanel.class.getResource("/css/crud.css").toExternalForm();

    private final int pageSize;
    private final String itemName;
    private final Function<T, String> searchText;

    private final TableView<T> table = new TableView<>();
    private final TextField searchField = new TextField();
    private final Button addButton = new Button();
    private final Label pageInfo = new Label();
    private final Button prevBtn = new Button("‹");
    private final Button nextBtn = new Button("›");

    private List<T> allItems = new ArrayList<>();
    private int currentPage = 1;

    private Consumer<T> onEdit;
    private Consumer<T> onDelete;

    /**
     * @param itemName   tên gọi ở dòng "Hiển thị ... trong tổng số ..." (ví dụ "thương hiệu")
     * @param searchText hàm trả về đoạn chữ dùng để tìm kiếm của một dòng
     */
    public CrudPanel(String title, String addText, String searchPrompt,
                     String itemName, int pageSize, Function<T, String> searchText) {
        this.itemName = itemName;
        this.pageSize = pageSize;
        this.searchText = searchText;

        getStylesheets().add(STYLESHEET);
        getStyleClass().add("crud-panel");
        setSpacing(14);

        // Hàng tiêu đề + nút Thêm
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("crud-title");
        addButton.setText("+  " + addText);
        addButton.getStyleClass().add("crud-primary-btn");
        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        HBox header = new HBox(titleLabel, spacer1, addButton);
        header.setAlignment(Pos.CENTER_LEFT);

        // Ô tìm kiếm
        searchField.setPromptText(searchPrompt);
        searchField.getStyleClass().add("crud-search");
        searchField.textProperty().addListener((o, a, b) -> {
            currentPage = 1;
            refresh();
        });

        // Bảng
        table.getStyleClass().add("crud-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setFixedCellSize(60);
        table.setPlaceholder(new Label("Chưa có dữ liệu"));
        VBox.setVgrow(table, Priority.ALWAYS);

        // Phân trang
        prevBtn.getStyleClass().add("crud-page-btn");
        nextBtn.getStyleClass().add("crud-page-btn");
        prevBtn.setOnAction(e -> {
            currentPage--;
            refresh();
        });
        nextBtn.setOnAction(e -> {
            currentPage++;
            refresh();
        });
        pageInfo.getStyleClass().add("crud-muted");
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        HBox footer = new HBox(8, pageInfo, spacer2, prevBtn, nextBtn);
        footer.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(header, searchField, table, footer);
    }

    // ---------- cách dùng từ bên ngoài ----------

    public void setOnAdd(Runnable action) {
        addButton.setOnAction(e -> action.run());
    }

    public void setOnEdit(Consumer<T> action) {
        this.onEdit = action;
    }

    public void setOnDelete(Consumer<T> action) {
        this.onDelete = action;
    }

    /** Nạp (hoặc nạp lại) toàn bộ dữ liệu. Trang hiện tại được giữ nếu còn hợp lệ. */
    public void setItems(List<T> items) {
        allItems = new ArrayList<>(items);
        refresh();
    }

    // ---------- các loại cột ----------

    /** Cột số thứ tự 01, 02, 03... tính liên tục qua các trang. */
    public void addIndexColumn(double width) {
        TableColumn<T, T> col = new TableColumn<>("STT");
        col.setPrefWidth(width);
        col.setSortable(false);
        col.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        col.setCellFactory(c -> new TableCell<T, T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : String.format("%02d", (currentPage - 1) * pageSize + getIndex() + 1));
            }
        });
        table.getColumns().add(col);
    }

    /** Cột chữ, tự xuống dòng khi dài. */
    public void addTextColumn(String header, double width, Function<T, String> getter) {
        TableColumn<T, String> col = new TableColumn<>(header);
        col.setPrefWidth(width);
        col.setSortable(false);
        col.setCellValueFactory(cd -> new SimpleStringProperty(getter.apply(cd.getValue())));
        col.setCellFactory(c -> new TableCell<T, String>() {
            private final Label label = new Label();

            {
                label.setWrapText(true);
                label.setMaxHeight(48);
                label.maxWidthProperty().bind(col.widthProperty().subtract(16));
            }

            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                if (empty || text == null) {
                    setGraphic(null);
                } else {
                    label.setText(text);
                    setGraphic(label);
                }
            }
        });
        table.getColumns().add(col);
    }

    /** Cột chứa một thành phần giao diện bất kỳ (ví dụ logo, huy hiệu trạng thái). */
    public void addNodeColumn(String header, double width, Function<T, Node> factory) {
        TableColumn<T, T> col = new TableColumn<>(header);
        col.setPrefWidth(width);
        col.setSortable(false);
        col.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        col.setCellFactory(c -> new TableCell<T, T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : factory.apply(item));
            }
        });
        table.getColumns().add(col);
    }

    /** Cột THAO TÁC với hai nút Sửa, Xóa. */
    public void addActionColumn(double width) {
        addNodeColumn("THAO TÁC", width, item -> {
            Button edit = new Button("Sửa");
            Button delete = new Button("Xóa");
            edit.getStyleClass().add("crud-btn-outline");
            delete.getStyleClass().addAll("crud-btn-outline", "crud-btn-danger");
            edit.setOnAction(e -> {
                if (onEdit != null) onEdit.accept(item);
            });
            delete.setOnAction(e -> {
                if (onDelete != null) onDelete.accept(item);
            });
            HBox box = new HBox(6, edit, delete);
            box.setAlignment(Pos.CENTER_LEFT);
            return box;
        });
    }

    // ---------- lọc + phân trang ----------

    private void refresh() {
        String keyword = normalize(searchField.getText());
        List<T> filtered = new ArrayList<>();
        for (T item : allItems) {
            if (keyword.isEmpty() || normalize(searchText.apply(item)).contains(keyword)) {
                filtered.add(item);
            }
        }

        int total = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        currentPage = Math.max(1, Math.min(currentPage, totalPages));

        int from = (currentPage - 1) * pageSize;
        int to = Math.min(from + pageSize, total);
        table.setItems(FXCollections.observableArrayList(filtered.subList(from, to)));
        table.refresh();

        pageInfo.setText(total == 0
                ? "Không có " + itemName
                : "Hiển thị " + (from + 1) + "–" + to + " trong tổng số " + total + " " + itemName);
        prevBtn.setDisable(currentPage <= 1);
        nextBtn.setDisable(currentPage >= totalPages);
    }

    // Bỏ dấu và chữ hoa để gõ "ap" vẫn tìm ra "Áp"
    private static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase()
                .trim();
    }
}
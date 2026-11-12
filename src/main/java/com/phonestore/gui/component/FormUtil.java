package com.phonestore.gui.component;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Window;

import java.util.function.Supplier;

/** Hàm dựng hộp thoại Thêm/Sửa dùng chung. */
public final class FormUtil {

    private FormUtil() {}

    public static GridPane newForm() {
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        return form;
    }

    public static void addRow(GridPane form, int row, String labelText, Node field) {
        Label label = new Label(labelText);
        label.getStyleClass().add("crud-form-label");
        label.setMinWidth(120);
        GridPane.setHgrow(field, Priority.ALWAYS);
        form.addRow(row, label, field);
    }

    public static Label newErrorLabel() {
        Label error = new Label();
        error.getStyleClass().add("crud-error");
        error.setWrapText(true);
        error.setMaxWidth(Double.MAX_VALUE);
        error.managedProperty().bind(error.visibleProperty());
        error.setVisible(false);
        return error;
    }

    /**
     * Mở hộp thoại. onSave trả về null nếu lưu thành công, hoặc nội dung lỗi để hiện trong hộp thoại.
     * Trả về true nếu người dùng đã lưu thành công.
     */
    public static boolean showForm(Window owner, String title, String saveText,
                                   Region content, Label error, Supplier<String> onSave) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle(title);
        if (owner != null) dlg.initOwner(owner);

        DialogPane pane = dlg.getDialogPane();
        pane.getStylesheets().add(CrudPanel.STYLESHEET);
        pane.getStyleClass().add("crud-dialog");
        content.setPrefWidth(480);
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
}
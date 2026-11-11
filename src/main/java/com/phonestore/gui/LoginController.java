package com.phonestore.gui;

import com.phonestore.bus.AuthBUS;
import com.phonestore.exception.BusinessException;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.shape.SVGPath;

public class LoginController {

    private static final String EYE_OPEN =
            "M1 12 Q12 -1 23 12 Q12 25 1 12 Z M12 9 A3 3 0 1 0 12 15 A3 3 0 1 0 12 9 Z";
    private static final String EYE_OFF = EYE_OPEN + " M3 3 L21 21";

    private final AuthBUS authBUS = new AuthBUS();

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtPasswordVisible;
    @FXML private SVGPath eyeIcon;
    @FXML private HBox errorBox;
    @FXML private Label lblError;

    @FXML
    private void initialize() {
        txtPasswordVisible.textProperty().bindBidirectional(txtPassword.textProperty());
        eyeIcon.setContent(EYE_OFF);

        txtUsername.textProperty().addListener((obs, oldV, newV) -> hideError());
        txtPassword.textProperty().addListener((obs, oldV, newV) -> hideError());
    }

    @FXML
    private void handleLogin() {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();

        if (username.isEmpty()) {
            showError("Vui lòng nhập tên đăng nhập.");
            txtUsername.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            showError("Vui lòng nhập mật khẩu.");
            return;
        }

        try {
            // Đăng nhập đúng thì AuthBUS đã tự lưu phiên đăng nhập
            authBUS.login(username, password);
        } catch (BusinessException e) {
            showError(e.getMessage());
            return;
        }

        // Khi MainLayout.fxml của Sang sẵn sàng, đổi "Customer.fxml" thành "MainLayout.fxml"
        SceneNavigator.switchScene("MainLayout.fxml", "Hệ thống quản trị");
    }

    @FXML
    private void handleTogglePassword() {
        boolean show = !txtPasswordVisible.isVisible();

        txtPasswordVisible.setVisible(show);
        txtPasswordVisible.setManaged(show);
        txtPassword.setVisible(!show);
        txtPassword.setManaged(!show);
        eyeIcon.setContent(show ? EYE_OPEN : EYE_OFF);

        TextField active = show ? txtPasswordVisible : txtPassword;
        active.requestFocus();
        active.positionCaret(active.getText().length());
    }

    @FXML
    private void handleForgotPassword() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Quên mật khẩu");
        alert.setHeaderText(null);
        alert.setContentText("Vui lòng liên hệ quản trị viên để được cấp lại mật khẩu.");
        alert.showAndWait();
    }

    private void showError(String message) {
        lblError.setText(message);
        errorBox.setVisible(true);
        errorBox.setManaged(true);
    }

    private void hideError() {
        errorBox.setVisible(false);
        errorBox.setManaged(false);
    }
}
package com.phonestore.gui;

import java.util.List;

import com.phonestore.dto.UserDTO;
import com.phonestore.util.AlertUtil;
import com.phonestore.util.PermissionNames;
import com.phonestore.util.SessionManager;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class MainLayoutController {

    @FXML private Button btnDashboard;
    @FXML private Button btnSales;
    @FXML private Button btnProduct;
    @FXML private Button btnBrandCategory;
    @FXML private Button btnOrder;
    @FXML private Button btnInventory;
    @FXML private Button btnCustomer;
    @FXML private Button btnStaff;
    @FXML private Button btnUserRole;
    @FXML private Button btnReport;

    @FXML private Label lblPageTitle;
    @FXML private HBox userProfileBox;
    @FXML private Label lblUserInitials;
    @FXML private Label lblUserName;
    @FXML private Label lblUserRole;
    @FXML private StackPane contentArea;

    private List<Button> navButtons;
    private ContextMenu userContextMenu;

    @FXML
    public void initialize() {
        SceneNavigator.setContentArea(contentArea);

        navButtons = List.of(
                btnDashboard, btnSales, btnProduct, btnBrandCategory,
                btnOrder, btnInventory, btnCustomer, btnStaff, btnUserRole, btnReport
        );

        setupUserInfo();
        applyRolePermissions();
        setupUserContextMenu();

        // Mặc định mở màn hình Tổng quan khi vào MainLayout
        navigateTo(btnDashboard, "Tổng quan", "Dashboard.fxml");
    }

    private void setupUserInfo() {
        UserDTO user = SessionManager.getCurrentUser();
        if (user != null) {
            String fullName = (user.getFullName() != null && !user.getFullName().isBlank())
                    ? user.getFullName().trim()
                    : user.getUsername();
            lblUserName.setText(fullName);
            lblUserInitials.setText(extractInitials(fullName));
            lblUserRole.setText(resolveRoleName(user.getRoleId()));
        } else {
            lblUserName.setText("Quản trị viên");
            lblUserInitials.setText("AD");
            lblUserRole.setText("Chế độ phát triển");
        }
    }

    private void applyRolePermissions() {
        // Chỉ lọc quyền khi đã đăng nhập thực tế và có tập quyền trong SessionManager
        if (!SessionManager.isLoggedIn()) {
            return;
        }
        setButtonVisibility(btnSales, PermissionNames.PROCESS_ORDERS);
        setButtonVisibility(btnProduct, PermissionNames.VIEW_PRODUCTS);
        setButtonVisibility(btnBrandCategory, PermissionNames.VIEW_PRODUCTS);
        setButtonVisibility(btnOrder, PermissionNames.VIEW_ORDERS);
        setButtonVisibility(btnInventory, PermissionNames.MANAGE_INVENTORY);
        setButtonVisibility(btnCustomer, PermissionNames.MANAGE_CUSTOMERS);
        setButtonVisibility(btnStaff, PermissionNames.MANAGE_USERS);
        setButtonVisibility(btnUserRole, PermissionNames.MANAGE_USERS);
        setButtonVisibility(btnReport, PermissionNames.VIEW_REPORTS);
    }

    private void setButtonVisibility(Button button, String permission) {
        boolean allowed = SessionManager.hasPermission(permission);
        button.setVisible(allowed);
        button.setManaged(allowed);
    }

    private void setupUserContextMenu() {
        userContextMenu = new ContextMenu();
        MenuItem logoutItem = new MenuItem("Đăng xuất");
        logoutItem.setOnAction(e -> handleLogout());
        userContextMenu.getItems().add(logoutItem);
    }

    @FXML
    private void onNavDashboard() {
        navigateTo(btnDashboard, "Tổng quan", "Dashboard.fxml");
    }

    @FXML
    private void onNavSales() {
        navigateTo(btnSales, "Bán hàng", "Sales.fxml");
    }

    @FXML
    private void onNavProduct() {
        navigateTo(btnProduct, "Quản lý sản phẩm", "Product.fxml");
    }

    @FXML
    private void onNavBrandCategory() {
        navigateTo(btnBrandCategory, "Thương hiệu & danh mục", "BrandCategory.fxml");
    }

    @FXML
    private void onNavOrder() {
        navigateTo(btnOrder, "Quản lý đơn hàng", "Order.fxml");
    }

    @FXML
    private void onNavInventory() {
        navigateTo(btnInventory, "Quản lý kho", "Inventory.fxml");
    }

    @FXML
    private void onNavCustomer() {
        navigateTo(btnCustomer, "Quản lý khách hàng", "Customer.fxml");
    }

    @FXML
    private void onNavStaff() {
        navigateTo(btnStaff, "Quản lý nhân viên", "Staff.fxml");
    }

    @FXML
    private void onNavUserRole() {
        navigateTo(btnUserRole, "Quản lý tài khoản", "UserRole.fxml");
    }

    @FXML
    private void onNavReport() {
        navigateTo(btnReport, "Báo cáo & Thống kê", "Report.fxml");
    }

    @FXML
    private void onHelpClick() {
        AlertUtil.showInfo(
                "Hỗ trợ kỹ thuật",
                "Hệ thống Quản lý cửa hàng điện thoại - Admin Portal v2.4\n"
                        + "Vui lòng liên hệ quản trị viên nếu bạn cần cấp quyền hoặc hỗ trợ nghiệp vụ."
        );
    }

    @FXML
    private void onUserMenuClick(MouseEvent event) {
        if (userContextMenu != null) {
            userContextMenu.show(userProfileBox, event.getScreenX(), event.getScreenY());
        }
    }

    private void handleLogout() {
        if (AlertUtil.showConfirm("Đăng xuất", "Bạn có chắc chắn muốn đăng xuất khỏi hệ thống?")) {
            SessionManager.logout();
            try {
                SceneNavigator.switchScene("Login.fxml", "Đăng nhập - Quản lý bán điện thoại");
            } catch (Exception ex) {
                AlertUtil.showInfo("Đã đăng xuất", "Phiên đăng nhập đã kết thúc (Màn hình Login.fxml chưa được tích hợp).");
                setupUserInfo();
            }
        }
    }

    public void navigateTo(Button activeBtn, String pageTitle, String fxmlFile) {
        setActiveButton(activeBtn);
        lblPageTitle.setText(pageTitle);

        try {
            SceneNavigator.loadContent(fxmlFile);
        } catch (Exception ex) {
            showPlaceholderScreen(pageTitle, fxmlFile, ex.getMessage());
        }
    }

    private void setActiveButton(Button activeBtn) {
        for (Button btn : navButtons) {
            btn.getStyleClass().remove("nav-btn-active");
        }
        if (activeBtn != null && !activeBtn.getStyleClass().contains("nav-btn-active")) {
            activeBtn.getStyleClass().add("nav-btn-active");
        }
    }

    private void showPlaceholderScreen(String pageTitle, String fxmlFile, String detail) {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("page-container");

        Label title = new Label(pageTitle);
        title.getStyleClass().add("page-title");

        Label sub = new Label("Màn hình " + fxmlFile + " đang được phát triển hoặc chưa nạp được.");
        sub.getStyleClass().add("page-subtitle");

        Label err = new Label(detail != null ? detail : "");
        err.getStyleClass().add("user-role");

        box.getChildren().addAll(title, sub, err);
        contentArea.getChildren().setAll(box);
    }

    private String extractInitials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "U";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        String first = parts[0].substring(0, 1);
        String last = parts[parts.length - 1].substring(0, 1);
        return (first + last).toUpperCase();
    }

    private String resolveRoleName(Integer roleId) {
        if (roleId == null) {
            return "Nhân viên";
        }
        return switch (roleId) {
            case 1 -> "Quản trị viên";
            case 2 -> "Quản lý";
            case 3 -> "Nhân viên bán hàng";
            case 4 -> "Khách hàng";
            default -> "Vai trò #" + roleId;
        };
    }
}
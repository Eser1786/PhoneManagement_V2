package com.phonestore.gui;

import com.phonestore.bus.AuthBUS;
import com.phonestore.bus.UserBUS;
import com.phonestore.dto.PermissionDTO;
import com.phonestore.dto.RoleDTO;
import com.phonestore.dto.UserDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.gui.component.CrudPanel;
import com.phonestore.gui.component.FormUtil;
import com.phonestore.util.AlertUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UserRoleController {

    private static final String DB_ERROR = "Không kết nối được cơ sở dữ liệu. Vui lòng thử lại.";
    private static final String ALL_ROLES = "Tất cả vai trò";
    private static final String[] AVATAR_COLORS =
            {"#e8833a", "#d9527a", "#5b7fd6", "#5fa86a", "#9b6fd6", "#d9534f", "#4fb0ae"};

    /** Một dòng của bảng: tài khoản + vai trò của tài khoản đó. */
    private record UserRow(UserDTO user, RoleDTO role) {
        String fullName() {
            return nz(user.getFullName());
        }

        String roleName() {
            return role == null ? "Chưa có vai trò" : role.getRoleName();
        }
    }

    private final UserBUS userBUS = new UserBUS();
    private final AuthBUS authBUS = new AuthBUS();

    @FXML private VBox root;
    @FXML private HBox body;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cboRoleFilter;
    @FXML private Label lblCount;
    @FXML private ComboBox<RoleDTO> cboPermRole;
    @FXML private Label lblRoleDesc;
    @FXML private VBox permList;
    @FXML private Label lblPermCount;

    private CrudPanel<UserRow> panel;
    private List<UserRow> rows = new ArrayList<>();
    private List<RoleDTO> roles = new ArrayList<>();
    private List<PermissionDTO> allPermissions = new ArrayList<>();
    private final Map<Integer, RoleDTO> roleById = new HashMap<>();
    private int currentUserId = -1;
    private boolean updatingFilter = false;

    @FXML
    private void initialize() {
        UserDTO me = authBUS.getCurrentUser();
        currentUserId = me == null ? -1 : me.getUserId();

        panel = new CrudPanel<UserRow>("", "", "", "tài khoản", 7,
                r -> r.fullName() + " " + nz(r.user().getUsername()) + " " + nz(r.user().getEmail()));
        panel.setHeaderVisible(false);   // tiêu đề đã nằm trong FXML
        panel.setSearchVisible(false);   // ô tìm kiếm đã nằm trong FXML
        panel.setFlat(true);
        panel.setNumberedPager(true);
        panel.setRowHeight(68);
        panel.setOnCountChanged(n -> lblCount.setText(n + " tài khoản"));
        panel.setExtraFilter(r -> {
            String f = cboRoleFilter.getValue();
            return f == null || f.equals(ALL_ROLES) || f.equals(r.roleName());
        });

        panel.addIndexColumn(50);
        panel.addNodeColumn("HỌ TÊN", 240, this::buildNameCell);
        panel.addTextColumn("EMAIL", 190, r -> nz(r.user().getEmail()));
        panel.addNodeColumn("VAI TRÒ", 110, this::buildRoleBadge);
        panel.addNodeColumn("TRẠNG THÁI", 130, this::buildStatusBadge);
        panel.addNodeColumn("THAO TÁC", 200, this::buildActionCell);

        body.getChildren().add(0, panel);
        panel.setPrefWidth(0);
        panel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(panel, Priority.ALWAYS);

        cboPermRole.setConverter(roleConverter());
        cboPermRole.valueProperty().addListener((o, a, b) -> {
            if (!updatingFilter) showPermissions(b);
        });
        txtSearch.textProperty().addListener((o, a, b) -> panel.setKeyword(b));
        cboRoleFilter.valueProperty().addListener((o, a, b) -> {
            if (!updatingFilter) panel.applyFilter();
        });

        reload();
    }

    // =====================================================
    // NẠP DỮ LIỆU
    // =====================================================

    private void reload() {
        try {
            roles = new ArrayList<>(userBUS.getAllRoles());
            roleById.clear();
            for (RoleDTO r : roles) roleById.put(r.getRoleId(), r);
            allPermissions = new ArrayList<>(userBUS.getAllPermissions());

            List<UserDTO> users = new ArrayList<>(userBUS.getAllUsers());
            users.sort(Comparator.comparingInt(UserDTO::getUserId));   // cũ trước, mới sau
            List<UserRow> loaded = new ArrayList<>();
            for (UserDTO u : users) {
                loaded.add(new UserRow(u, u.getRoleId() == null ? null : roleById.get(u.getRoleId())));
            }
            rows = loaded;

            rebuildRoleChoices();
            panel.setItems(rows);
            showPermissions(cboPermRole.getValue());
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (RuntimeException e) {
            showError(DB_ERROR);
        }
    }

    // Làm mới hai ô chọn vai trò nhưng giữ lựa chọn hiện tại nếu còn
    private void rebuildRoleChoices() {
        String selectedFilter = cboRoleFilter.getValue();
        RoleDTO selectedPerm = cboPermRole.getValue();

        updatingFilter = true;
        cboRoleFilter.getItems().setAll(ALL_ROLES);
        for (RoleDTO r : roles) cboRoleFilter.getItems().add(r.getRoleName());
        cboRoleFilter.setValue(selectedFilter != null && cboRoleFilter.getItems().contains(selectedFilter)
                ? selectedFilter : ALL_ROLES);

        cboPermRole.getItems().setAll(roles);
        RoleDTO keep = null;
        if (selectedPerm != null) {
            for (RoleDTO r : roles) {
                if (r.getRoleId() == selectedPerm.getRoleId()) keep = r;
            }
        }
        cboPermRole.setValue(keep != null ? keep : (roles.isEmpty() ? null : roles.get(0)));
        updatingFilter = false;
    }

    // =====================================================
    // KHUNG XEM QUYỀN CỦA VAI TRÒ
    // =====================================================

    private void showPermissions(RoleDTO role) {
        permList.getChildren().clear();
        if (role == null) {
            lblRoleDesc.setText("");
            lblPermCount.setText("");
            return;
        }
        lblRoleDesc.setText(nz(role.getDescription()));

        Set<String> granted = new HashSet<>();
        try {
            for (PermissionDTO p : userBUS.getPermissionsByRole(role.getRoleId())) {
                granted.add(p.getPermissionName());
            }
        } catch (BusinessException e) {
            showError(e.getMessage());
            return;
        } catch (RuntimeException e) {
            showError(DB_ERROR);
            return;
        }

        int count = 0;
        for (PermissionDTO p : allPermissions) {
            boolean on = granted.contains(p.getPermissionName());
            if (on) count++;
            permList.getChildren().add(buildPermissionRow(p, on));
        }
        lblPermCount.setText(role.getRoleName() + " có " + count + "/" + allPermissions.size() + " quyền");
    }

    private Node buildPermissionRow(PermissionDTO p, boolean on) {
        Label mark = new Label(on ? "✓" : "–");
        mark.getStyleClass().addAll("perm-mark", on ? "perm-mark-on" : "perm-mark-off");

        String text = (p.getDescription() == null || p.getDescription().isBlank())
                ? p.getPermissionName() : p.getDescription();
        Label name = new Label(text);
        name.getStyleClass().add(on ? "perm-text" : "perm-text-off");
        name.setWrapText(true);
        Label code = new Label(p.getPermissionName());
        code.getStyleClass().add("perm-code");

        VBox texts = new VBox(1, name, code);
        HBox row = new HBox(10, mark, texts);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("perm-row");
        return row;
    }

    // =====================================================
    // Ô TRONG BẢNG
    // =====================================================

    private Node buildNameCell(UserRow r) {
        String name = r.fullName();
        Label letter = new Label(initial(name));
        letter.getStyleClass().add("crud-avatar-text");
        StackPane avatar = new StackPane(letter);
        avatar.getStyleClass().add("crud-avatar");
        avatar.setStyle("-fx-background-color: "
                + AVATAR_COLORS[Math.floorMod(name.hashCode(), AVATAR_COLORS.length)] + ";");

        Label nameLabel = new Label(name);
        nameLabel.setMaxWidth(140);
        HBox nameLine = new HBox(6, nameLabel);
        nameLine.setAlignment(Pos.CENTER_LEFT);
        if (r.user().getUserId() == currentUserId) {
            Label tag = new Label("Bạn");
            tag.getStyleClass().add("crud-self-tag");
            nameLine.getChildren().add(tag);
        }

        Label sub = new Label("@" + nz(r.user().getUsername()));
        sub.getStyleClass().add("crud-name-sub");
        VBox texts = new VBox(2, nameLine, sub);
        texts.setAlignment(Pos.CENTER_LEFT);

        HBox box = new HBox(10, avatar, texts);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private Node buildRoleBadge(UserRow r) {
        Label badge = new Label(r.roleName());
        badge.getStyleClass().addAll("crud-badge", roleStyle(r.roleName()));
        return badge;
    }

    private Node buildStatusBadge(UserRow r) {
        boolean active = r.user().isActive();
        Label badge = new Label(active ? "Hoạt động" : "Đã khóa");
        badge.getStyleClass().addAll("crud-badge", active ? "crud-badge-ok" : "crud-badge-off");
        return badge;
    }

    private Node buildActionCell(UserRow r) {
        boolean self = r.user().getUserId() == currentUserId;
        boolean active = r.user().isActive();

        Button changeRole = new Button("Đổi vai trò");
        Button toggle = new Button(active ? "Khóa" : "Mở khóa");
        changeRole.getStyleClass().add("crud-btn-outline");
        toggle.getStyleClass().add("crud-btn-outline");
        if (active) toggle.getStyleClass().add("crud-btn-danger");

        // Không cho tự đổi vai trò / tự khóa chính mình (BUS cũng chặn ở tầng dưới)
        changeRole.setDisable(self);
        toggle.setDisable(self);

        changeRole.setOnAction(e -> showChangeRole(r));
        toggle.setOnAction(e -> toggleActive(r));

        HBox box = new HBox(6, changeRole, toggle);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    // =====================================================
    // ĐỔI VAI TRÒ
    // =====================================================

    private void showChangeRole(UserRow r) {
        ComboBox<RoleDTO> cbo = new ComboBox<>();
        cbo.setMaxWidth(Double.MAX_VALUE);
        cbo.setConverter(roleConverter());
        cbo.getItems().setAll(roles);
        cbo.setValue(r.role());

        Label desc = new Label();
        desc.getStyleClass().add("crud-muted");
        desc.setWrapText(true);
        Runnable syncDesc = () -> {
            RoleDTO v = cbo.getValue();
            desc.setText(v == null ? "" : nz(v.getDescription()));
        };
        cbo.valueProperty().addListener((o, a, b) -> syncDesc.run());
        syncDesc.run();

        TextField who = new TextField(r.fullName() + " (" + nz(r.user().getUsername()) + ")");
        who.setDisable(true);

        GridPane form = FormUtil.newForm();
        FormUtil.addRow(form, 0, "Tài khoản", who);
        FormUtil.addRow(form, 1, "Vai trò mới *", cbo);

        Label note = new Label("Quyền theo vai trò mới có hiệu lực ở lần đăng nhập tiếp theo của người này.");
        note.getStyleClass().add("crud-muted");
        note.setWrapText(true);
        Label error = FormUtil.newErrorLabel();

        boolean saved = FormUtil.showForm(
                root.getScene().getWindow(),
                "Đổi vai trò", "Lưu thay đổi",
                new VBox(12, form, desc, note, error), error,
                () -> {
                    RoleDTO chosen = cbo.getValue();
                    if (chosen == null) return "Vui lòng chọn vai trò.";
                    try {
                        userBUS.changeRole(r.user().getUserId(), chosen.getRoleId());
                        return null;   // null = thành công
                    } catch (BusinessException e) {
                        return e.getMessage();
                    } catch (RuntimeException e) {
                        return DB_ERROR;
                    }
                });

        if (saved) reload();
    }

    // =====================================================
    // KHÓA / MỞ KHÓA
    // =====================================================

    private void toggleActive(UserRow r) {
        boolean lock = r.user().isActive();
        String who = "\"" + nz(r.user().getUsername()) + "\" (" + r.fullName() + ")";
        String message = lock
                ? "Khóa tài khoản " + who + "?\nNgười này sẽ không đăng nhập được cho đến khi bạn mở khóa."
                : "Mở khóa tài khoản " + who + "?\nNgười này sẽ đăng nhập được trở lại.";
        if (!AlertUtil.showConfirm(lock ? "Xác nhận khóa tài khoản" : "Xác nhận mở khóa", message)) return;

        try {
            // Tham số thứ hai của BUS là trạng thái MỚI (true = hoạt động)
            if (!userBUS.toggleUserActive(r.user().getUserId(), !lock)) {
                showError("Không thể cập nhật trạng thái tài khoản. Vui lòng thử lại.");
                return;
            }
            reload();
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (RuntimeException e) {
            showError(DB_ERROR);
        }
    }

    // =====================================================
    // HÀM PHỤ
    // =====================================================

    private StringConverter<RoleDTO> roleConverter() {
        return new StringConverter<RoleDTO>() {
            @Override
            public String toString(RoleDTO r) {
                return r == null ? "" : r.getRoleName();
            }

            @Override
            public RoleDTO fromString(String s) {
                return null;
            }
        };
    }

    private static String roleStyle(String roleName) {
        return switch (roleName.toLowerCase()) {
            case "admin" -> "crud-role-admin";
            case "manager" -> "crud-role-manager";
            case "staff" -> "crud-role-staff";
            case "customer" -> "crud-role-customer";
            default -> "crud-role-other";
        };
    }

    // Bọc trong runLater để không lỗi khi gọi lúc màn hình đang được nạp
    private void showError(String message) {
        Platform.runLater(() -> AlertUtil.showError("Thông báo", message));
    }

    // Chữ cái đầu của tên (từ cuối cùng trong họ tên)
    private static String initial(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        return parts[parts.length - 1].substring(0, 1).toUpperCase();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
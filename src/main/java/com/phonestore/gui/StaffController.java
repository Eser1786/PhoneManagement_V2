package com.phonestore.gui;

import com.phonestore.bus.StaffBUS;
import com.phonestore.bus.UserBUS;
import com.phonestore.dto.RoleDTO;
import com.phonestore.dto.StaffDTO;
import com.phonestore.dto.UserDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.gui.component.CrudPanel;
import com.phonestore.gui.component.FormUtil;
import com.phonestore.util.AlertUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class StaffController {

    private static final String DB_ERROR = "Không kết nối được cơ sở dữ liệu. Vui lòng thử lại.";
    private static final String ALL_POSITIONS = "Tất cả chức vụ";
    private static final List<String> DEFAULT_POSITIONS =
            List.of("Quản trị hệ thống", "Quản lý cửa hàng", "Nhân viên bán hàng");
    private static final String[] AVATAR_COLORS =
            {"#e8833a", "#d9527a", "#5b7fd6", "#5fa86a", "#9b6fd6", "#d9534f", "#4fb0ae"};

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    // Chuyển qua lại giữa LocalDate và chữ dd/MM/yyyy trong ô chọn ngày
    private static final StringConverter<LocalDate> DATE_CONVERTER = new StringConverter<LocalDate>() {
        @Override
        public String toString(LocalDate d) {
            return d == null ? "" : DATE_FMT.format(d);
        }

        @Override
        public LocalDate fromString(String s) {
            if (s == null || s.isBlank()) return null;
            try {
                return LocalDate.parse(s.trim(), DATE_FMT);
            } catch (RuntimeException e) {
                return null;
            }
        }
    };

    /** Một dòng của bảng: thông tin nhân viên + tài khoản gắn với nhân viên đó. */
    private record StaffRow(StaffDTO staff, UserDTO user) {
        String fullName() {
            return user == null ? "(Không rõ)" : nz(user.getFullName());
        }

        String email() {
            return user == null ? "" : nz(user.getEmail());
        }
    }

    private final StaffBUS staffBUS = new StaffBUS();
    private final UserBUS userBUS = new UserBUS();

    @FXML private VBox root;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cboPosition;
    @FXML private Label lblCount;

    private CrudPanel<StaffRow> panel;
    private List<StaffRow> rows = new ArrayList<>();
    private boolean updatingFilter = false;

    @FXML
    private void initialize() {
        panel = new CrudPanel<StaffRow>("", "", "", "nhân viên", 7,
                r -> r.fullName() + " " + r.email());
        panel.setHeaderVisible(false);   // tiêu đề + nút Thêm đã nằm trong FXML
        panel.setSearchVisible(false);   // ô tìm kiếm đã nằm trong FXML
        panel.setFlat(true);
        panel.setNumberedPager(true);
        panel.setRowHeight(68);
        panel.setOnCountChanged(n -> lblCount.setText(n + " nhân viên"));
        panel.setExtraFilter(r -> {
            String p = cboPosition.getValue();
            return p == null || p.equals(ALL_POSITIONS) || p.equals(r.staff().getPosition());
        });

        panel.addIndexColumn(50);
        panel.addNodeColumn("HỌ TÊN", 230, this::buildNameCell);
        panel.addTextColumn("EMAIL", 210, StaffRow::email);
        panel.addTextColumn("CHỨC VỤ", 150, r -> nz(r.staff().getPosition()));
        panel.addTextColumn("LƯƠNG", 120, r -> formatMoney(r.staff().getSalary()));
        panel.addTextColumn("NGÀY VÀO LÀM", 120, r -> formatDate(r.staff().getHireDate()));
        panel.addNodeColumn("TÀI KHOẢN", 150, this::buildStatusBadge);
        panel.addActionColumn(120);

        panel.setOnEdit(this::showForm);
        panel.setOnDelete(this::deleteStaff);

        root.getChildren().add(panel);
        VBox.setVgrow(panel, Priority.ALWAYS);

        txtSearch.textProperty().addListener((o, a, b) -> panel.setKeyword(b));
        cboPosition.valueProperty().addListener((o, a, b) -> {
            if (!updatingFilter) panel.applyFilter();
        });

        reload();
    }

    @FXML
    private void handleAdd() {
        showForm(null);
    }

    // =====================================================
    // NẠP DỮ LIỆU
    // =====================================================

    private void reload() {
        try {
            List<StaffRow> loaded = new ArrayList<>();
            for (StaffDTO s : staffBUS.getAllStaff()) {
                loaded.add(new StaffRow(s, staffBUS.getUserOfStaff(s)));
            }
            rows = loaded;
            rebuildPositionFilter();
            panel.setItems(rows);
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (RuntimeException e) {
            showError(DB_ERROR);
        }
    }

    // Danh sách chức vụ trong ô lọc lấy từ chính dữ liệu đang có
    private void rebuildPositionFilter() {
        String selected = cboPosition.getValue();
        Set<String> positions = new TreeSet<>();
        for (StaffRow r : rows) {
            if (r.staff().getPosition() != null) positions.add(r.staff().getPosition());
        }
        updatingFilter = true;
        cboPosition.getItems().setAll(ALL_POSITIONS);
        cboPosition.getItems().addAll(positions);
        cboPosition.setValue(selected != null && cboPosition.getItems().contains(selected)
                ? selected : ALL_POSITIONS);
        updatingFilter = false;
    }

    // =====================================================
    // Ô TRONG BẢNG
    // =====================================================

    private Node buildNameCell(StaffRow r) {
        String name = r.fullName();
        Label letter = new Label(initial(name));
        letter.getStyleClass().add("crud-avatar-text");
        StackPane avatar = new StackPane(letter);
        avatar.getStyleClass().add("crud-avatar");
        avatar.setStyle("-fx-background-color: "
                + AVATAR_COLORS[Math.floorMod(name.hashCode(), AVATAR_COLORS.length)] + ";");

        Label label = new Label(name);
        label.setMaxWidth(160);
        HBox box = new HBox(10, avatar, label);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private Node buildStatusBadge(StaffRow r) {
        boolean active = r.user() != null && r.user().isActive();
        Label badge = new Label(active ? "Đang hoạt động" : "Đã khóa");
        badge.getStyleClass().addAll("crud-badge", active ? "crud-badge-ok" : "crud-badge-off");
        return badge;
    }

    // =====================================================
    // THÊM / SỬA
    // =====================================================

    /** existing == null nghĩa là Thêm mới, khác null nghĩa là Sửa. */
    private void showForm(StaffRow existing) {
        boolean isEdit = existing != null;
        UserDTO oldUser = isEdit ? existing.user() : null;

        // Với "Thêm": cần danh sách vai trò và tài khoản còn trống
        List<RoleDTO> roles = new ArrayList<>();
        List<UserDTO> freeUsers = new ArrayList<>();
        if (!isEdit) {
            try {
                loadAccountChoices(roles, freeUsers);
            } catch (BusinessException e) {
                showError(e.getMessage());
                return;
            } catch (RuntimeException e) {
                showError(DB_ERROR);
                return;
            }
        }

        // ---------- thông tin cá nhân + công việc ----------
        TextField fullName = new TextField(oldUser == null ? "" : nz(oldUser.getFullName()));
        TextField email = new TextField(oldUser == null ? "" : nz(oldUser.getEmail()));
        TextField phone = new TextField(oldUser == null ? "" : nz(oldUser.getPhone()));
        fullName.setPromptText("Ví dụ: Nguyễn Văn An");
        email.setPromptText("ten@example.com");
        phone.setPromptText("10 chữ số, bắt đầu bằng 0");

        ComboBox<String> position = new ComboBox<>();
        position.setEditable(true);
        position.getItems().setAll(positionSuggestions());
        position.getEditor().setText(isEdit ? nz(existing.staff().getPosition()) : "");
        position.getEditor().setPromptText("Chọn hoặc nhập chức vụ");
        position.setMaxWidth(Double.MAX_VALUE);

        TextField salary = new TextField(isEdit && existing.staff().getSalary() != null
                ? existing.staff().getSalary().stripTrailingZeros().toPlainString() : "");
        salary.setPromptText("Số nguyên, ví dụ: 10000000");

        DatePicker hireDate = new DatePicker(isEdit ? existing.staff().getHireDate() : LocalDate.now());
        hireDate.setConverter(DATE_CONVERTER);
        hireDate.getEditor().setPromptText("dd/MM/yyyy");
        hireDate.setMaxWidth(Double.MAX_VALUE);

        GridPane person = FormUtil.newForm();
        FormUtil.addRow(person, 0, "Họ tên *", fullName);
        FormUtil.addRow(person, 1, "Email *", email);
        FormUtil.addRow(person, 2, "Số điện thoại *", phone);
        FormUtil.addRow(person, 3, "Chức vụ *", position);
        FormUtil.addRow(person, 4, "Lương (VNĐ) *", salary);
        FormUtil.addRow(person, 5, "Ngày vào làm *", hireDate);

        // ---------- phần tài khoản (chỉ dùng khi Thêm) ----------
        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton rbNew = new RadioButton("Tạo tài khoản mới");
        RadioButton rbExisting = new RadioButton("Gắn tài khoản có sẵn");
        rbNew.setToggleGroup(modeGroup);
        rbExisting.setToggleGroup(modeGroup);
        rbNew.setSelected(true);

        TextField username = new TextField();
        username.setPromptText("3-50 ký tự: chữ, số, . _ -");
        PasswordField password = new PasswordField();
        password.setPromptText("Tối thiểu 6 ký tự");
        ComboBox<RoleDTO> cboRole = new ComboBox<>();
        cboRole.setMaxWidth(Double.MAX_VALUE);
        cboRole.setConverter(new StringConverter<RoleDTO>() {
            @Override public String toString(RoleDTO r) { return r == null ? "" : r.getRoleName(); }
            @Override public RoleDTO fromString(String s) { return null; }
        });
        cboRole.getItems().setAll(roles);
        roles.stream().filter(r -> "Staff".equalsIgnoreCase(r.getRoleName()))
                .findFirst().ifPresent(cboRole::setValue);

        ComboBox<UserDTO> cboUser = new ComboBox<>();
        cboUser.setMaxWidth(Double.MAX_VALUE);
        cboUser.setPromptText(freeUsers.isEmpty() ? "Không có tài khoản trống" : "Chọn tài khoản");
        cboUser.setConverter(new StringConverter<UserDTO>() {
            @Override public String toString(UserDTO u) {
                return u == null ? "" : u.getUsername() + " – " + nz(u.getFullName());
            }
            @Override public UserDTO fromString(String s) { return null; }
        });
        cboUser.getItems().setAll(freeUsers);
        rbExisting.setDisable(freeUsers.isEmpty());

        GridPane gridNew = FormUtil.newForm();
        FormUtil.addRow(gridNew, 0, "Tên đăng nhập *", username);
        FormUtil.addRow(gridNew, 1, "Mật khẩu *", password);
        FormUtil.addRow(gridNew, 2, "Vai trò *", cboRole);

        GridPane gridOld = FormUtil.newForm();
        FormUtil.addRow(gridOld, 0, "Tài khoản *", cboUser);

        Label sectionTitle = new Label("TÀI KHOẢN ĐĂNG NHẬP");
        sectionTitle.getStyleClass().add("crud-section-title");
        Label error = FormUtil.newErrorLabel();
        VBox content = new VBox(12, person, sectionTitle);

        if (isEdit) {
            TextField uname = new TextField(oldUser == null ? "" : nz(oldUser.getUsername()));
            uname.setDisable(true);
            GridPane acc = FormUtil.newForm();
            FormUtil.addRow(acc, 0, "Tên đăng nhập", uname);
            Label note = new Label("Tên đăng nhập không đổi được. "
                    + "Khóa/mở khóa tài khoản và đổi vai trò ở màn Quản lý tài khoản.");
            note.getStyleClass().add("crud-muted");
            note.setWrapText(true);
            content.getChildren().addAll(acc, note);
        } else {
            // Chọn "gắn tài khoản có sẵn" thì họ tên/email/SĐT lấy theo tài khoản đó
            Runnable syncMode = () -> {
                boolean useExisting = rbExisting.isSelected();
                gridNew.setVisible(!useExisting);
                gridNew.setManaged(!useExisting);
                gridOld.setVisible(useExisting);
                gridOld.setManaged(useExisting);
                fullName.setDisable(useExisting);
                email.setDisable(useExisting);
                phone.setDisable(useExisting);
                UserDTO u = useExisting ? cboUser.getValue() : null;
                fullName.setText(u == null ? "" : nz(u.getFullName()));
                email.setText(u == null ? "" : nz(u.getEmail()));
                phone.setText(u == null ? "" : nz(u.getPhone()));
            };
            modeGroup.selectedToggleProperty().addListener((o, a, b) -> syncMode.run());
            cboUser.valueProperty().addListener((o, a, b) -> syncMode.run());
            syncMode.run();
            content.getChildren().addAll(new HBox(16, rbNew, rbExisting), gridNew, gridOld);
        }
        content.getChildren().add(error);

        // ---------- bấm Lưu ----------
        boolean saved = FormUtil.showForm(
                root.getScene().getWindow(),
                isEdit ? "Sửa nhân viên" : "Thêm nhân viên",
                isEdit ? "Lưu thay đổi" : "Thêm nhân viên",
                content, error,
                () -> {
                    String pos = position.getEditor().getText();
                    if (pos == null || pos.isBlank()) return "Vui lòng nhập chức vụ.";
                    if (pos.trim().length() > 100) return "Chức vụ tối đa 100 ký tự.";

                    BigDecimal sal = parseSalary(salary.getText());
                    if (sal == null) return "Lương phải là số nguyên không âm, tối đa 99.999.999.";

                    LocalDate hire = DATE_CONVERTER.fromString(hireDate.getEditor().getText());
                    if (hire == null) return "Ngày vào làm không hợp lệ, nhập theo dạng dd/MM/yyyy.";

                    try {
                        if (isEdit) {
                            staffBUS.updateStaffWithUserInfo(existing.staff().getStaffId(),
                                    fullName.getText(), email.getText(), phone.getText(), pos, sal, hire);
                        } else if (rbExisting.isSelected()) {
                            UserDTO u = cboUser.getValue();
                            if (u == null) return "Vui lòng chọn tài khoản cần gắn.";
                            StaffDTO s = new StaffDTO();
                            s.setUserId(u.getUserId());
                            s.setPosition(pos.trim());
                            s.setSalary(sal);
                            s.setHireDate(hire);
                            staffBUS.addStaff(s);
                        } else {
                            RoleDTO role = cboRole.getValue();
                            staffBUS.addStaffWithNewAccount(fullName.getText(), email.getText(),
                                    phone.getText(), username.getText(), password.getText(),
                                    role == null ? 0 : role.getRoleId(), pos, sal, hire);
                        }
                        return null;   // null = thành công
                    } catch (BusinessException e) {
                        return e.getMessage();
                    } catch (RuntimeException e) {
                        return DB_ERROR;
                    }
                });

        if (saved) reload();
    }

    // Vai trò (bỏ Customer) và tài khoản chưa gắn nhân viên nào (bỏ tài khoản khách hàng)
    private void loadAccountChoices(List<RoleDTO> roles, List<UserDTO> freeUsers) {
        Integer customerRoleId = null;
        for (RoleDTO r : userBUS.getAllRoles()) {
            if ("Customer".equalsIgnoreCase(r.getRoleName())) {
                customerRoleId = r.getRoleId();
            } else {
                roles.add(r);
            }
        }
        Set<Integer> linked = new HashSet<>();
        for (StaffRow row : rows) {
            linked.add(row.staff().getUserId());
        }
        for (UserDTO u : userBUS.getAllUsers()) {
            boolean isCustomer = customerRoleId != null && customerRoleId.equals(u.getRoleId());
            if (!linked.contains(u.getUserId()) && !isCustomer) {
                freeUsers.add(u);
            }
        }
    }

    private List<String> positionSuggestions() {
        Set<String> set = new LinkedHashSet<>(DEFAULT_POSITIONS);
        for (StaffRow r : rows) {
            if (r.staff().getPosition() != null) set.add(r.staff().getPosition());
        }
        return new ArrayList<>(set);
    }

    // =====================================================
    // XÓA
    // =====================================================

    private void deleteStaff(StaffRow r) {
        boolean ok = AlertUtil.showConfirm("Xác nhận xóa",
                "Xóa nhân viên \"" + r.fullName() + "\"?\n"
                        + "Tài khoản đăng nhập vẫn được giữ lại, hãy khóa tài khoản nếu không cho họ đăng nhập nữa.");
        if (!ok) return;
        try {
            staffBUS.removeStaff(r.staff().getStaffId());
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

    // Bọc trong runLater để không lỗi khi gọi lúc màn hình đang được nạp
    private void showError(String message) {
        Platform.runLater(() -> AlertUtil.showError("Thông báo", message));
    }

    // Chỉ nhận số nguyên, cho phép gõ kèm dấu chấm, phẩy, khoảng trắng, chữ đ. Tối đa 8 chữ số.
    private static BigDecimal parseSalary(String text) {
        String digits = text == null ? "" : text.replaceAll("[.,\\s]", "").replace("đ", "");
        return digits.matches("\\d{1,8}") ? new BigDecimal(digits) : null;
    }

    private static String formatMoney(BigDecimal v) {
        if (v == null) return "";
        DecimalFormatSymbols sym = new DecimalFormatSymbols();
        sym.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", sym).format(v) + "đ";
    }

    private static String formatDate(LocalDate d) {
        return d == null ? "" : DATE_FMT.format(d);
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
package com.phonestore.bus;

import com.phonestore.dao.PermissionDAO;
import com.phonestore.dao.RoleDAO;
import com.phonestore.dao.UserDAO;
import com.phonestore.dto.PermissionDTO;
import com.phonestore.dto.RoleDTO;
import com.phonestore.dto.UserDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.PasswordUtil;
import com.phonestore.util.PermissionNames;

import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

public class UserBUS {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^0\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    private final UserDAO userDAO;
    private final RoleDAO roleDAO;
    private final PermissionDAO permissionDAO;
    private final AuthBUS authBUS;

    public UserBUS() {
        this(new UserDAO(), new RoleDAO(), new PermissionDAO(), new AuthBUS());
    }

    public UserBUS(UserDAO userDAO, RoleDAO roleDAO, PermissionDAO permissionDAO, AuthBUS authBUS) {
        this.userDAO = userDAO;
        this.roleDAO = roleDAO;
        this.permissionDAO = permissionDAO;
        this.authBUS = authBUS;
    }

    public List<UserDTO> getAllUsers() {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        return userDAO.findAll();
    }

    public UserDTO getUserById(int userId) {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        if (userId <= 0) {
            throw new BusinessException("Mã người dùng không hợp lệ");
        }
        return userDAO.findById(userId);
    }

    public List<RoleDTO> getAllRoles() {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        return roleDAO.findAll();
    }

    public List<PermissionDTO> getAllPermissions() {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        return permissionDAO.findAll();
    }

    public List<PermissionDTO> getPermissionsByRole(int roleId) {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        if (roleId <= 0) {
            throw new BusinessException("Mã vai trò không hợp lệ");
        }
        return permissionDAO.findByRoleId(roleId);
    }

    public int createUser(UserDTO user) {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        validateUser(user, true);

        try {
            if (userDAO.findByUsername(user.getUsername()) != null) {
                throw new BusinessException("Tên đăng nhập đã tồn tại");
            }
        } catch (SQLException e) {
            throw new BusinessException("Lỗi kiểm tra tên đăng nhập trong cơ sở dữ liệu", e);
        }

        // Băm mật khẩu bằng BCrypt nếu mật khẩu truyền vào chưa phải chuỗi BCrypt
        if (!user.getPassword().startsWith("$2a$")) {
            user.setPassword(PasswordUtil.hash(user.getPassword()));
        }

        int generatedId = userDAO.insert(user);
        if (generatedId <= 0) {
            throw new BusinessException("Không thể tạo người dùng mới (Email có thể đã tồn tại)");
        }
        return generatedId;
    }

    public boolean updateUser(UserDTO user) {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        if (user == null || user.getUserId() <= 0) {
            throw new BusinessException("Mã người dùng không hợp lệ");
        }
        validateUser(user, false);

        if (!userDAO.update(user)) {
            throw new BusinessException("Cập nhật thông tin người dùng thất bại");
        }
        return true;
    }

    public boolean toggleUserActive(int userId, boolean isActive) {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        if (userId <= 0) {
            throw new BusinessException("Mã người dùng không hợp lệ");
        }
        UserDTO currentUser = authBUS.getCurrentUser();
        if (!isActive && currentUser != null && currentUser.getUserId() == userId) {
            throw new BusinessException("Bạn không thể tự khóa tài khoản đang đăng nhập của chính mình");
        }
        return userDAO.updateActiveStatus(userId, isActive);
    }

    public boolean changePassword(int userId, String newPlainPassword) {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        if (userId <= 0) {
            throw new BusinessException("Mã người dùng không hợp lệ");
        }
        if (newPlainPassword == null || newPlainPassword.length() < 6) {
            throw new BusinessException("Mật khẩu mới phải có ít nhất 6 ký tự");
        }
        String passwordHash = PasswordUtil.hash(newPlainPassword);
        return userDAO.updatePassword(userId, passwordHash);
    }

    public boolean assignPermissionToRole(int roleId, int permissionId) {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        if (roleId <= 0 || permissionId <= 0) {
            throw new BusinessException("Mã vai trò hoặc mã quyền không hợp lệ");
        }
        return permissionDAO.assignPermissionToRole(roleId, permissionId);
    }

    public boolean removePermissionFromRole(int roleId, int permissionId) {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        if (roleId <= 0 || permissionId <= 0) {
            throw new BusinessException("Mã vai trò hoặc mã quyền không hợp lệ");
        }
        return permissionDAO.removePermissionFromRole(roleId, permissionId);
    }

    private void validateUser(UserDTO u, boolean isNew) {
        if (u == null) {
            throw new BusinessException("Thông tin người dùng không được để trống");
        }
        if (u.getUsername() == null || u.getUsername().isBlank()) {
            throw new BusinessException("Vui lòng nhập tên đăng nhập");
        }
        if (isNew && (u.getPassword() == null || u.getPassword().isEmpty())) {
            throw new BusinessException("Vui lòng nhập mật khẩu");
        }
        if (u.getFullName() == null || u.getFullName().isBlank()) {
            throw new BusinessException("Vui lòng nhập họ tên");
        }
        if (u.getEmail() == null || !EMAIL_PATTERN.matcher(u.getEmail().trim()).matches()) {
            throw new BusinessException("Email không hợp lệ");
        }
        if (u.getPhone() == null || !PHONE_PATTERN.matcher(u.getPhone().trim()).matches()) {
            throw new BusinessException("Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0");
        }
        u.setUsername(u.getUsername().trim());
        u.setFullName(u.getFullName().trim());
        u.setEmail(u.getEmail().trim());
        u.setPhone(u.getPhone().trim());
    }
}
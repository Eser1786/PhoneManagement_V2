package com.phonestore.bus;

import com.phonestore.dao.PermissionDAO;
import com.phonestore.dao.UserDAO;
import com.phonestore.dto.UserDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.PasswordUtil;
import com.phonestore.util.SessionManager;

import java.sql.SQLException;
import java.util.List;

public class AuthBUS {

    static final String INVALID_CREDENTIALS = "Tên đăng nhập hoặc mật khẩu không đúng";

    private final UserDAO userDAO;
    private final PermissionDAO permissionDAO;

    public AuthBUS() {
        this(new UserDAO(), new PermissionDAO());
    }

    public AuthBUS(UserDAO userDAO, PermissionDAO permissionDAO) {
        this.userDAO = userDAO;
        this.permissionDAO = permissionDAO;
    }

    public UserDTO login(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new BusinessException("Vui lòng nhập tên đăng nhập");
        }
        if (password == null || password.isEmpty()) {
            throw new BusinessException("Vui lòng nhập mật khẩu");
        }

        try {
            UserDTO user = userDAO.findByUsername(username.trim());

            if (user == null || !PasswordUtil.verify(password, user.getPassword())) {
                throw new BusinessException(INVALID_CREDENTIALS);
            }
            if (!user.isActive()) {
                throw new BusinessException("Tài khoản chưa được kích hoạt hoặc đã bị khóa");
            }

            List<String> permissionNames = permissionDAO.findPermissionNamesByRoleId(user.getRoleId());

            user.setPassword(null);   // không giữ chuỗi băm trong bộ nhớ sau khi xác thực xong
            SessionManager.login(user, permissionNames);
            return user;
        } catch (SQLException e) {
            throw new BusinessException("Không thể đăng nhập do lỗi cơ sở dữ liệu", e);
        }
    }

    public void logout() {
        SessionManager.logout();
    }

    public UserDTO getCurrentUser() {
        return SessionManager.getCurrentUser();
    }

    public boolean hasPermission(String permissionName) {
        return SessionManager.hasPermission(permissionName);
    }

    public void requirePermission(String permissionName) {
        if (!SessionManager.isLoggedIn()) {
            throw new BusinessException("Vui lòng đăng nhập để tiếp tục");
        }
        if (!SessionManager.hasPermission(permissionName)) {
            throw new BusinessException("Bạn không có quyền thực hiện chức năng này");
        }
    }
}
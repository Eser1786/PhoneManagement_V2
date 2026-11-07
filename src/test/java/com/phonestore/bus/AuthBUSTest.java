package com.phonestore.bus;

import com.phonestore.dao.PermissionDAO;
import com.phonestore.dao.UserDAO;
import com.phonestore.dto.UserDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.PasswordUtil;
import com.phonestore.util.PermissionNames;
import com.phonestore.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthBUSTest {

    private static final int ROLE_MANAGER = 2;
    private static final int ROLE_CUSTOMER = 4;

    private Map<String, UserDTO> users;
    private boolean failDatabase;
    private AuthBUS authBUS;

    @BeforeEach
    void setUp() {
        users = new HashMap<>();
        failDatabase = false;

        users.put("manager", user("manager", "123456", ROLE_MANAGER, true));
        users.put("customer1", user("customer1", "123456", ROLE_CUSTOMER, true));
        users.put("locked", user("locked", "123456", ROLE_CUSTOMER, false));

        UserDAO fakeUserDAO = new UserDAO() {
            @Override
            public UserDTO findByUsername(String username) throws SQLException {
                if (failDatabase) {
                    throw new SQLException("mất kết nối");
                }
                UserDTO u = users.get(username);
                return u == null ? null : copy(u);   // DAO thật luôn trả về đối tượng mới
            }
        };
        PermissionDAO fakePermissionDAO = new PermissionDAO() {
            @Override
            public List<String> findPermissionNamesByRoleId(int roleId) {
                return roleId == ROLE_MANAGER
                        ? List.of(PermissionNames.VIEW_PRODUCTS, PermissionNames.EDIT_PRODUCT)
                        : List.of(PermissionNames.VIEW_PRODUCTS);
            }
        };
        authBUS = new AuthBUS(fakeUserDAO, fakePermissionDAO);
        SessionManager.logout();
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }


    @Test
    void loginSucceedsAndStartsSession() {
        UserDTO result = authBUS.login("manager", "123456");

        assertEquals("manager", result.getUsername());
        assertTrue(SessionManager.isLoggedIn());
        assertSame(result, authBUS.getCurrentUser());
        assertTrue(authBUS.hasPermission(PermissionNames.EDIT_PRODUCT));
        assertFalse(authBUS.hasPermission(PermissionNames.MANAGE_USERS));
    }

    @Test
    void loginDoesNotKeepPasswordHashInSession() {
        UserDTO result = authBUS.login("manager", "123456");
        assertNull(result.getPassword());
        assertNull(SessionManager.getCurrentUser().getPassword());
    }

    @Test
    void loginTrimsUsername() {
        assertEquals("manager", authBUS.login("  manager  ", "123456").getUsername());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveSameMessage() {
        BusinessException wrongPassword =
                assertThrows(BusinessException.class, () -> authBUS.login("manager", "sai-mat-khau"));
        BusinessException unknownUser =
                assertThrows(BusinessException.class, () -> authBUS.login("khong-ton-tai", "123456"));

        assertEquals(AuthBUS.INVALID_CREDENTIALS, wrongPassword.getMessage());
        assertEquals(wrongPassword.getMessage(), unknownUser.getMessage());
        assertFalse(SessionManager.isLoggedIn());
    }

    @Test
    void inactiveAccountCannotLogInEvenWithCorrectPassword() {
        BusinessException e = assertThrows(BusinessException.class, () -> authBUS.login("locked", "123456"));
        assertTrue(e.getMessage().contains("khóa"));
        assertFalse(SessionManager.isLoggedIn());
    }

    @Test
    void inactiveAccountWithWrongPasswordDoesNotRevealItIsLocked() {
        BusinessException e = assertThrows(BusinessException.class, () -> authBUS.login("locked", "sai"));
        assertEquals(AuthBUS.INVALID_CREDENTIALS, e.getMessage());
    }

    @Test
    void blankInputIsRejectedWithClearMessages() {
        assertEquals("Vui lòng nhập tên đăng nhập",
                assertThrows(BusinessException.class, () -> authBUS.login("  ", "123456")).getMessage());
        assertEquals("Vui lòng nhập tên đăng nhập",
                assertThrows(BusinessException.class, () -> authBUS.login(null, "123456")).getMessage());
        assertEquals("Vui lòng nhập mật khẩu",
                assertThrows(BusinessException.class, () -> authBUS.login("manager", "")).getMessage());
        assertEquals("Vui lòng nhập mật khẩu",
                assertThrows(BusinessException.class, () -> authBUS.login("manager", null)).getMessage());
    }

    @Test
    void databaseErrorBecomesBusinessException() {
        failDatabase = true;
        BusinessException e = assertThrows(BusinessException.class, () -> authBUS.login("manager", "123456"));
        assertTrue(e.getCause() instanceof SQLException);
        assertFalse(SessionManager.isLoggedIn());
    }

    @Test
    void failedLoginDoesNotReplaceExistingSession() {
        authBUS.login("manager", "123456");
        assertThrows(BusinessException.class, () -> authBUS.login("customer1", "sai"));
        assertEquals("manager", authBUS.getCurrentUser().getUsername());
    }


    @Test
    void requirePermissionPassesWhenUserHasIt() {
        authBUS.login("manager", "123456");
        authBUS.requirePermission(PermissionNames.EDIT_PRODUCT);  
    }

    @Test
    void requirePermissionRejectsMissingPermission() {
        authBUS.login("customer1", "123456");
        BusinessException e = assertThrows(BusinessException.class,
                () -> authBUS.requirePermission(PermissionNames.ADD_PRODUCT));
        assertEquals("Bạn không có quyền thực hiện chức năng này", e.getMessage());
    }

    @Test
    void requirePermissionRejectsWhenNotLoggedIn() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> authBUS.requirePermission(PermissionNames.VIEW_PRODUCTS));
        assertEquals("Vui lòng đăng nhập để tiếp tục", e.getMessage());
    }

    @Test
    void logoutRemovesUserAndPermissions() {
        authBUS.login("manager", "123456");
        authBUS.logout();

        assertNull(authBUS.getCurrentUser());
        assertFalse(authBUS.hasPermission(PermissionNames.VIEW_PRODUCTS));
        assertThrows(BusinessException.class, () -> authBUS.requirePermission(PermissionNames.VIEW_PRODUCTS));
    }


    private static UserDTO user(String username, String plainPassword, int roleId, boolean active) {
        UserDTO u = new UserDTO();
        u.setUserId(username.hashCode() & 0xffff);
        u.setUsername(username);
        u.setPassword(PasswordUtil.hash(plainPassword));
        u.setRoleId(roleId);
        u.setActive(active);
        return u;
    }

    private static UserDTO copy(UserDTO s) {
        UserDTO u = new UserDTO();
        u.setUserId(s.getUserId());
        u.setUsername(s.getUsername());
        u.setPassword(s.getPassword());
        u.setRoleId(s.getRoleId());
        u.setActive(s.isActive());
        return u;
    }
}
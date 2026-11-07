package com.phonestore.bus;

import com.phonestore.config.DBConnection;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.PermissionNames;
import com.phonestore.util.SessionManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AuthBUSDatabaseTest {

    private final AuthBUS authBUS = new AuthBUS();

    @BeforeAll
    static void requireDatabase() {
        boolean ok;
        try (Connection c = DBConnection.getConnection()) {
            ok = c.isValid(2);
        } catch (Exception e) {
            ok = false;
        }
        assumeTrue(ok, "Không kết nối được database, bỏ qua test");
    }

    @AfterAll
    static void closePool() {
        DBConnection.close();
    }

    @AfterEach
    void logout() {
        SessionManager.logout();
    }

    @Test
    void adminHasAllTenPermissions() {
        authBUS.login("admin", "123456");
        assertEquals(10, SessionManager.getPermissions().size());
        assertTrue(authBUS.hasPermission(PermissionNames.MANAGE_USERS));
    }

    @Test
    void managerCanEditProductsButNotManageUsers() {
        authBUS.login("manager", "123456");
        assertTrue(authBUS.hasPermission(PermissionNames.EDIT_PRODUCT));
        assertTrue(authBUS.hasPermission(PermissionNames.VIEW_REPORTS));
        assertFalse(authBUS.hasPermission(PermissionNames.MANAGE_USERS));
    }

    @Test
    void staffCanProcessOrdersButNotAddProducts() {
        authBUS.login("staff1", "123456");
        assertTrue(authBUS.hasPermission(PermissionNames.PROCESS_ORDERS));
        assertFalse(authBUS.hasPermission(PermissionNames.ADD_PRODUCT));
    }

    @Test
    void customerOnlyViewsProducts() {
        authBUS.login("customer1", "123456");
        assertEquals(1, SessionManager.getPermissions().size());
        assertTrue(authBUS.hasPermission(PermissionNames.VIEW_PRODUCTS));
    }

    @Test
    void wrongPasswordIsRejected() {
        assertThrows(BusinessException.class, () -> authBUS.login("admin", "sai-mat-khau"));
        assertFalse(SessionManager.isLoggedIn());
    }

    @Test
    void deactivatedSeedAccountIsRejected() {
        BusinessException e = assertThrows(BusinessException.class, () -> authBUS.login("customer5", "123456"));
        assertTrue(e.getMessage().contains("khóa"));
    }
}
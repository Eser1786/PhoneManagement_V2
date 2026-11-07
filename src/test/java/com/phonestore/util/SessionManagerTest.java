package com.phonestore.util;

import com.phonestore.dto.UserDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionManagerTest {

    @AfterEach
    void reset() {
        SessionManager.logout();
    }

    private UserDTO user(String username) {
        UserDTO u = new UserDTO();
        u.setUserId(1);
        u.setUsername(username);
        return u;
    }

    @Test
    void notLoggedInByDefault() {
        assertFalse(SessionManager.isLoggedIn());
        assertNull(SessionManager.getCurrentUser());
        assertTrue(SessionManager.getPermissions().isEmpty());
        assertFalse(SessionManager.hasPermission(PermissionNames.VIEW_PRODUCTS));
    }

    @Test
    void loginStoresUserAndPermissions() {
        UserDTO u = user("manager");
        SessionManager.login(u, List.of(PermissionNames.VIEW_PRODUCTS, PermissionNames.EDIT_PRODUCT));

        assertTrue(SessionManager.isLoggedIn());
        assertSame(u, SessionManager.getCurrentUser());
        assertTrue(SessionManager.hasPermission(PermissionNames.EDIT_PRODUCT));
        assertFalse(SessionManager.hasPermission(PermissionNames.MANAGE_USERS));
        assertEquals(Set.of("view_products", "edit_product"), SessionManager.getPermissions());
    }

    @Test
    void logoutClearsEverything() {
        SessionManager.login(user("manager"), List.of(PermissionNames.VIEW_PRODUCTS));
        SessionManager.logout();

        assertFalse(SessionManager.isLoggedIn());
        assertNull(SessionManager.getCurrentUser());
        assertFalse(SessionManager.hasPermission(PermissionNames.VIEW_PRODUCTS));
    }

    @Test
    void permissionsCannotBeModifiedFromOutside() {
        SessionManager.login(user("staff"), List.of(PermissionNames.VIEW_PRODUCTS));
        Set<String> perms = SessionManager.getPermissions();
        assertThrows(UnsupportedOperationException.class, () -> perms.add(PermissionNames.MANAGE_USERS));
    }

    @Test
    void loginRejectsNullUser() {
        assertThrows(IllegalArgumentException.class, () -> SessionManager.login(null, List.of()));
    }

    @Test
    void loginWithNullPermissionsMeansNoPermissions() {
        SessionManager.login(user("customer"), null);
        assertTrue(SessionManager.isLoggedIn());
        assertFalse(SessionManager.hasPermission(PermissionNames.VIEW_PRODUCTS));
    }
}
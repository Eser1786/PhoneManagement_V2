package com.phonestore.util;

import com.phonestore.dto.UserDTO;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class SessionManager {

    private static UserDTO currentUser;
    private static Set<String> permissions = Collections.emptySet();

    private SessionManager() {
    }

    public static synchronized void login(UserDTO user, Collection<String> permissionNames) {
        if (user == null) {
            throw new IllegalArgumentException("user không được null");
        }
        currentUser = user;
        permissions = permissionNames == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(permissionNames));
    }

    public static synchronized void logout() {
        currentUser = null;
        permissions = Collections.emptySet();
    }

    public static synchronized boolean isLoggedIn() {
        return currentUser != null;
    }

    public static synchronized UserDTO getCurrentUser() {
        return currentUser;
    }

    public static synchronized Set<String> getPermissions() {
        return permissions;
    }

    public static synchronized boolean hasPermission(String permissionName) {
        return currentUser != null && permissions.contains(permissionName);
    }
}
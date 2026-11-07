package com.phonestore.util;

import at.favre.lib.crypto.bcrypt.BCrypt;

public final class PasswordUtil {

    private static final int COST = 10;

    private PasswordUtil() {
    }

    public static String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Mật khẩu không được rỗng");
        }
        return BCrypt.withDefaults().hashToString(COST, plainPassword.toCharArray());
    }

    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || plainPassword.isEmpty()
                || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        return BCrypt.verifyer().verify(plainPassword.toCharArray(), storedHash).verified;
    }
}
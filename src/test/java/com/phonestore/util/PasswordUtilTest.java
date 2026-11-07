package com.phonestore.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordUtilTest {

    private static final String SEED_HASH = "$2a$10$mEYqSDNk2HwbnAox4YWOnOMR6N6aXKv1cvW92D6uXpbvhGuvQDzd.";

    @Test
    void hashIsNotPlaintextAndLooksLikeBcrypt() {
        String hash = PasswordUtil.hash("MatKhau@123");
        assertNotEquals("MatKhau@123", hash);
        assertTrue(hash.startsWith("$2"), "chuỗi băm BCrypt phải bắt đầu bằng $2");
        assertTrue(hash.length() <= 255, "phải vừa cột Users.password VARCHAR(255)");
    }

    @Test
    void verifyAcceptsCorrectAndRejectsWrongPassword() {
        String hash = PasswordUtil.hash("MatKhau@123");
        assertTrue(PasswordUtil.verify("MatKhau@123", hash));
        assertFalse(PasswordUtil.verify("matkhau@123", hash));
        assertFalse(PasswordUtil.verify("", hash));
    }

    @Test
    void samePasswordGivesDifferentHashesBecauseOfSalt() {
        String h1 = PasswordUtil.hash("123456");
        String h2 = PasswordUtil.hash("123456");
        assertNotEquals(h1, h2);
        assertTrue(PasswordUtil.verify("123456", h1));
        assertTrue(PasswordUtil.verify("123456", h2));
    }

    @Test
    void verifyReturnsFalseForNullEmptyOrMalformedHash() {
        assertFalse(PasswordUtil.verify(null, "$2a$10$abc"));
        assertFalse(PasswordUtil.verify("123456", null));
        assertFalse(PasswordUtil.verify("123456", ""));
        assertFalse(PasswordUtil.verify("123456", "khong-phai-bcrypt"));
    }

    @Test
    void hashRejectsEmptyPassword() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hash(""));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hash(null));
    }

    @Test
    void seedDataPasswordMatches123456() {
        assertTrue(PasswordUtil.verify("123456", SEED_HASH));
        assertFalse(PasswordUtil.verify("654321", SEED_HASH));
    }
}
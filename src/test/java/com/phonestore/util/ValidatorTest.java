package com.phonestore.util;

import com.phonestore.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidatorTest {

    private static String msg(Runnable r) {
        return assertThrows(BusinessException.class, r::run).getMessage();
    }

    @Test
    void requireTextTrimsAndChecksLength() {
        assertEquals("abc", Validator.requireText("  abc ", "Tên", 10));
        assertEquals("Tên không được để trống", msg(() -> Validator.requireText("   ", "Tên", 10)));
        assertEquals("Tên không được để trống", msg(() -> Validator.requireText(null, "Tên", 10)));
        assertEquals("Tên không được dài quá 3 ký tự", msg(() -> Validator.requireText("abcd", "Tên", 3)));
    }

    @Test
    void optionalTextReturnsNullWhenBlank() {
        assertNull(Validator.optionalText("  ", "Ghi chú", 5));
        assertEquals("ok", Validator.optionalText(" ok ", "Ghi chú", 5));
    }

    @Test
    void moneyRules() {
        Validator.requireMoney(new BigDecimal("1000.50"), "Giá", false);
        Validator.requireMoney(BigDecimal.ZERO, "Giá vốn", true);
        assertEquals("Giá phải lớn hơn 0", msg(() -> Validator.requireMoney(BigDecimal.ZERO, "Giá", false)));
        assertEquals("Giá không được âm", msg(() -> Validator.requireMoney(new BigDecimal("-1"), "Giá", true)));
        assertEquals("Giá không được để trống", msg(() -> Validator.requireMoney(null, "Giá", true)));
        msg(() -> Validator.requireMoney(new BigDecimal("1.001"), "Giá", true));
        msg(() -> Validator.requireMoney(new BigDecimal("10000000000000"), "Giá", true));
    }

    @Test
    void numbersAndSelection() {
        Validator.requireNonNegative(0, "Số lượng");
        Validator.requirePositive(1, "Số lượng");
        msg(() -> Validator.requireNonNegative(-1, "Số lượng"));
        msg(() -> Validator.requirePositive(0, "Số lượng"));
        assertEquals("Vui lòng chọn hãng", msg(() -> Validator.requireSelected(0, "hãng")));
        Validator.requireSelected(3, "hãng");
    }

    @Test
    void phoneEmailAndOneOf() {
        assertEquals("0901234567", Validator.requirePhone("0901234567", "Số điện thoại"));
        msg(() -> Validator.requirePhone("12345", "Số điện thoại"));
        assertEquals("a@b.vn", Validator.requireEmail("a@b.vn", "Email"));
        msg(() -> Validator.requireEmail("khong-hop-le", "Email"));
        Validator.requireOneOf("A", "Loại", List.of("A", "B"));
        msg(() -> Validator.requireOneOf("C", "Loại", List.of("A", "B")));
    }
}

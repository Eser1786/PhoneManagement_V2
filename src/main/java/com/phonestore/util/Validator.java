package com.phonestore.util;

import com.phonestore.exception.BusinessException;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Các hàm kiểm tra dữ liệu đầu vào dùng chung cho lớp BUS. Hàm nào thấy dữ liệu sai thì ném
 * BusinessException với thông điệp tiếng Việt, GUI hiển thị trực tiếp. Tham số "label" là tên
 * trường hiển thị cho người dùng, ví dụ "Tên sản phẩm".
 *
 *   String name = Validator.requireText(dto.getName(), "Tên sản phẩm", 100);
 *   Validator.requireMoney(dto.getPrice(), "Giá bán", false);
 */
public final class Validator {

    /** Giới hạn của cột DECIMAL(15,2): 13 chữ số phần nguyên. */
    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999999.99");

    private static final Pattern PHONE = Pattern.compile("^(0|\\+84)\\d{9,10}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private Validator() {
    }

    /** Bắt buộc có chữ, bỏ khoảng trắng hai đầu, không dài quá maxLength. Trả về chuỗi đã cắt khoảng trắng. */
    public static String requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(label + " không được để trống");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessException(label + " không được dài quá " + maxLength + " ký tự");
        }
        return trimmed;
    }

    /** Không bắt buộc: trả về null nếu để trống, ngược lại kiểm tra độ dài như requireText. */
    public static String optionalText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, label, maxLength);
    }

    /** Số tiền: không null, không âm (hoặc > 0 nếu allowZero = false), tối đa 2 chữ số thập phân, vừa cột DECIMAL(15,2). */
    public static void requireMoney(BigDecimal value, String label, boolean allowZero) {
        if (value == null) {
            throw new BusinessException(label + " không được để trống");
        }
        int sign = value.signum();
        if (sign < 0 || (sign == 0 && !allowZero)) {
            throw new BusinessException(label + (allowZero ? " không được âm" : " phải lớn hơn 0"));
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new BusinessException(label + " chỉ được có tối đa 2 chữ số thập phân");
        }
        if (value.compareTo(MAX_MONEY) > 0) {
            throw new BusinessException(label + " quá lớn");
        }
    }

    public static void requireNonNegative(int value, String label) {
        if (value < 0) {
            throw new BusinessException(label + " không được âm");
        }
    }

    public static void requirePositive(int value, String label) {
        if (value <= 0) {
            throw new BusinessException(label + " phải lớn hơn 0");
        }
    }

    /** Dùng cho khóa ngoại chọn từ ComboBox: id phải > 0, nếu không là chưa chọn. */
    public static void requireSelected(int id, String label) {
        if (id <= 0) {
            throw new BusinessException("Vui lòng chọn " + label);
        }
    }

    /** Số điện thoại Việt Nam: 0xxxxxxxxx hoặc +84xxxxxxxxx. Cho phép dấu cách, chấm, gạch ngang. Trả về chuỗi đã chuẩn hóa. */
    public static String requirePhone(String value, String label) {
        String text = requireText(value, label, 20);
        String normalized = text.replaceAll("[\\s.\\-]", "");
        if (!PHONE.matcher(normalized).matches()) {
            throw new BusinessException(label + " không hợp lệ");
        }
        return normalized;
    }

    public static String requireEmail(String value, String label) {
        String text = requireText(value, label, 100);
        if (!EMAIL.matcher(text).matches()) {
            throw new BusinessException(label + " không hợp lệ");
        }
        return text;
    }

    public static void requireOneOf(String value, String label, Collection<String> allowed) {
        if (value == null || !allowed.contains(value)) {
            throw new BusinessException(label + " không hợp lệ");
        }
    }
}

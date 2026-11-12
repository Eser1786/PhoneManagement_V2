package com.phonestore.util;

import com.phonestore.exception.BusinessException;

import java.sql.SQLException;

/**
 * Đổi SQLException thành BusinessException có thông điệp tiếng Việt dễ hiểu, dựa trên mã lỗi
 * của MySQL. Dùng ở lớp BUS thay cho việc tự viết catch riêng:
 *
 *   try {
 *       dao.insert(x);
 *   } catch (SQLException e) {
 *       throw SqlErrorMapper.toBusinessException(e, "Không thể thêm sản phẩm");
 *   }
 *
 * Lỗi không nhận ra được thì dùng thông điệp fallback; lỗi gốc luôn được giữ làm "cause" để dễ gỡ lỗi.
 */
public final class SqlErrorMapper {

    private static final int DUPLICATE_ENTRY = 1062;
    private static final int COLUMN_NULL = 1048;
    private static final int DATA_TOO_LONG = 1406;
    private static final int OUT_OF_RANGE = 1264;
    private static final int PARENT_ROW_REFERENCED = 1451;   // xóa/sửa dòng đang được bảng khác tham chiếu
    private static final int CHILD_ROW_MISSING_PARENT = 1452; // thêm dòng trỏ tới khóa ngoại không tồn tại

    private SqlErrorMapper() {
    }

    public static BusinessException toBusinessException(SQLException e, String fallbackMessage) {
        return new BusinessException(messageFor(e, fallbackMessage), e);
    }

    static String messageFor(SQLException e, String fallbackMessage) {
        switch (e.getErrorCode()) {
            case DUPLICATE_ENTRY:
                return "Dữ liệu bị trùng với bản ghi đã có";
            case PARENT_ROW_REFERENCED:
                return "Không thể xóa hoặc thay đổi vì dữ liệu đang được sử dụng ở nơi khác";
            case CHILD_ROW_MISSING_PARENT:
                return "Dữ liệu liên quan (hãng, loại, khách hàng, nhân viên...) không tồn tại";
            case COLUMN_NULL:
                return "Thiếu thông tin bắt buộc";
            case DATA_TOO_LONG:
                return "Dữ liệu nhập vào quá dài";
            case OUT_OF_RANGE:
                return "Giá trị số vượt quá giới hạn cho phép";
            default:
                return fallbackMessage;
        }
    }
}

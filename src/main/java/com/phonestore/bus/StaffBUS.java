package com.phonestore.bus;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import com.phonestore.dao.StaffDAO;
import com.phonestore.dao.UserDAO;
import com.phonestore.dto.StaffDTO;
import com.phonestore.dto.UserDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.PasswordUtil;
import com.phonestore.util.PermissionNames;

public class StaffBUS {

    private final StaffDAO staffDAO;
    private final UserDAO userDAO;
    private final AuthBUS authBUS;

    public StaffBUS() {
        this(new StaffDAO(), new UserDAO(), new AuthBUS());
    }

    public StaffBUS(StaffDAO staffDAO, UserDAO userDAO, AuthBUS authBUS) {
        this.staffDAO = staffDAO;
        this.userDAO = userDAO;
        this.authBUS = authBUS;
    }

    public List<StaffDTO> getAllStaff() {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        return staffDAO.findAll();
    }

    public StaffDTO getStaffById(int staffId) {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        if (staffId <= 0) {
            throw new BusinessException("Mã nhân viên không hợp lệ");
        }
        return staffDAO.findById(staffId);
    }

    public StaffDTO getStaffByUserId(int userId) {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        if (userId <= 0) {
            throw new BusinessException("Mã tài khoản không hợp lệ");
        }
        return staffDAO.findByUserId(userId);
    }

    public UserDTO getUserOfStaff(StaffDTO staff) {
        if (staff == null || staff.getUserId() <= 0) {
            return null;
        }
        return userDAO.findById(staff.getUserId());
    }

    public List<StaffDTO> searchStaff(String keyword) {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        if (keyword == null || keyword.isBlank()) {
            return staffDAO.findAll();
        }
        return staffDAO.search(keyword.trim());
    }

    public boolean addStaff(StaffDTO staff) {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        validateStaff(staff);

        if (userDAO.findById(staff.getUserId()) == null) {
            throw new BusinessException("Tài khoản người dùng (user_id) không tồn tại");
        }
        if (staffDAO.findByUserId(staff.getUserId()) != null) {
            throw new BusinessException("Tài khoản này đã được gắn cho một nhân viên khác");
        }
        if (!staffDAO.insert(staff)) {
            throw new BusinessException("Không thể thêm nhân viên vào cơ sở dữ liệu");
        }
        return true;
    }

    public boolean updateStaff(StaffDTO staff) {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        if (staff == null || staff.getStaffId() <= 0) {
            throw new BusinessException("Mã nhân viên không hợp lệ");
        }
        validateStaff(staff);

        StaffDTO existingByUser = staffDAO.findByUserId(staff.getUserId());
        if (existingByUser != null && existingByUser.getStaffId() != staff.getStaffId()) {
            throw new BusinessException("Tài khoản này đã được gắn cho một nhân viên khác");
        }
        if (!staffDAO.update(staff)) {
            throw new BusinessException("Cập nhật thông tin nhân viên thất bại");
        }
        return true;
    }

    public boolean deleteStaff(int staffId) {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        if (staffId <= 0) {
            throw new BusinessException("Mã nhân viên không hợp lệ");
        }
        return staffDAO.delete(staffId);
    }

    private void validateStaff(StaffDTO s) {
        if (s == null) {
            throw new BusinessException("Thông tin nhân viên không được để trống");
        }
        if (s.getUserId() <= 0) {
            throw new BusinessException("Nhân viên bắt buộc phải gắn với một tài khoản (user_id)");
        }
        if (s.getPosition() == null || s.getPosition().isBlank()) {
            throw new BusinessException("Vui lòng nhập chức vụ nhân viên");
        }
        if (s.getSalary() == null || s.getSalary().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Lương nhân viên không được âm");
        }
        s.setPosition(s.getPosition().trim());
        if (s.getHireDate() == null) {
            s.setHireDate(LocalDate.now());
        }
    }

        // =====================================================================
    // STT 25: thêm nhân viên kèm tài khoản, sửa kèm thông tin tài khoản, xóa an toàn
    // =====================================================================

    private static final Pattern PHONE_PATTERN = Pattern.compile("^0\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");

    /** Tạo tài khoản mới và nhân viên cùng lúc. Nếu lưu nhân viên lỗi thì xóa tài khoản vừa tạo. */
    public StaffDTO addStaffWithNewAccount(String fullName, String email, String phone,
                                           String username, String password, int roleId,
                                           String position, BigDecimal salary, LocalDate hireDate) {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        validatePersonalInfo(fullName, email, phone);
        validateJobInfo(position, salary);
        if (username == null || !USERNAME_PATTERN.matcher(username.trim()).matches()) {
            throw new BusinessException("Tên đăng nhập gồm 3-50 ký tự: chữ, số, dấu chấm, gạch dưới hoặc gạch ngang");
        }
        if (password == null || password.length() < 6) {
            throw new BusinessException("Mật khẩu tối thiểu 6 ký tự");
        }
        if (roleId <= 0) {
            throw new BusinessException("Vui lòng chọn vai trò cho tài khoản");
        }

        try {
            if (userDAO.findByUsername(username.trim()) != null) {
                throw new BusinessException("Tên đăng nhập đã tồn tại");
            }
        } catch (SQLException e) {
            throw new BusinessException("Lỗi kiểm tra tên đăng nhập trong cơ sở dữ liệu", e);
        }

        UserDTO user = new UserDTO();
        user.setUsername(username.trim());
        user.setEmail(email.trim());
        user.setPassword(PasswordUtil.hash(password));
        user.setFullName(fullName.trim());
        user.setPhone(phone.trim());
        user.setRoleId(roleId);
        user.setActive(true);

        int userId = userDAO.insert(user);
        if (userId <= 0) {
            throw new BusinessException("Không thể tạo tài khoản (email có thể đã tồn tại)");
        }

        StaffDTO staff = new StaffDTO();
        staff.setUserId(userId);
        staff.setPosition(position.trim());
        staff.setSalary(salary);
        staff.setHireDate(hireDate != null ? hireDate : LocalDate.now());
        if (!staffDAO.insert(staff)) {
            userDAO.delete(userId);
            throw new BusinessException("Không thể lưu thông tin nhân viên");
        }
        return staff;
    }

    /** Sửa cả thông tin nhân viên lẫn họ tên, email, SĐT trong tài khoản gắn với nhân viên đó. */
    public StaffDTO updateStaffWithUserInfo(int staffId, String fullName, String email, String phone,
                                            String position, BigDecimal salary, LocalDate hireDate) {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        StaffDTO existing = staffDAO.findById(staffId);
        if (existing == null) {
            throw new BusinessException("Không tìm thấy nhân viên cần sửa");
        }
        validatePersonalInfo(fullName, email, phone);
        validateJobInfo(position, salary);

        UserDTO user = userDAO.findById(existing.getUserId());
        if (user == null) {
            throw new BusinessException("Không tìm thấy tài khoản của nhân viên");
        }
        user.setFullName(fullName.trim());
        user.setEmail(email.trim());
        user.setPhone(phone.trim());
        if (!userDAO.update(user)) {
            throw new BusinessException("Không thể cập nhật họ tên/email/SĐT (email có thể đã tồn tại)");
        }

        existing.setPosition(position.trim());
        existing.setSalary(salary);
        existing.setHireDate(hireDate != null ? hireDate : existing.getHireDate());
        if (!staffDAO.update(existing)) {
            throw new BusinessException("Không thể cập nhật thông tin nhân viên");
        }
        return existing;
    }

    /** Xóa nhân viên (giữ lại tài khoản). Báo lỗi rõ ràng thay vì chỉ trả false. */
    public void removeStaff(int staffId) {
        authBUS.requirePermission(PermissionNames.MANAGE_USERS);
        StaffDTO staff = staffDAO.findById(staffId);
        if (staff == null) {
            throw new BusinessException("Không tìm thấy nhân viên cần xóa");
        }
        UserDTO me = authBUS.getCurrentUser();
        if (me != null && me.getUserId() == staff.getUserId()) {
            throw new BusinessException("Bạn không thể xóa nhân viên gắn với tài khoản đang đăng nhập");
        }
        if (!staffDAO.delete(staffId)) {
            throw new BusinessException("Không thể xóa nhân viên. Nhân viên này có thể đã lập đơn hàng.");
        }
    }

    private void validatePersonalInfo(String fullName, String email, String phone) {
        if (fullName == null || fullName.isBlank()) {
            throw new BusinessException("Vui lòng nhập họ tên");
        }
        if (fullName.trim().length() > 100) {
            throw new BusinessException("Họ tên tối đa 100 ký tự");
        }
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches() || email.trim().length() > 100) {
            throw new BusinessException("Email chưa đúng định dạng, ví dụ ten@example.com");
        }
        if (phone == null || !PHONE_PATTERN.matcher(phone.trim()).matches()) {
            throw new BusinessException("Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0");
        }
    }

    private void validateJobInfo(String position, BigDecimal salary) {
        if (position == null || position.isBlank()) {
            throw new BusinessException("Vui lòng nhập chức vụ nhân viên");
        }
        if (position.trim().length() > 100) {
            throw new BusinessException("Chức vụ tối đa 100 ký tự");
        }
        if (salary == null || salary.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Lương nhân viên không được âm");
        }
        if (salary.compareTo(new BigDecimal("99999999")) > 0) {
            throw new BusinessException("Lương tối đa 99.999.999 đồng");
        }
    }
}
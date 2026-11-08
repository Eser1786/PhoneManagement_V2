package com.phonestore.bus;

import com.phonestore.dao.StaffDAO;
import com.phonestore.dao.UserDAO;
import com.phonestore.dto.StaffDTO;
import com.phonestore.dto.UserDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.PermissionNames;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
}
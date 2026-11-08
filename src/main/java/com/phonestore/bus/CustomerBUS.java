package com.phonestore.bus;

import com.phonestore.dao.CustomerDAO;
import com.phonestore.dao.RoleDAO;
import com.phonestore.dao.UserDAO;
import com.phonestore.dto.CustomerDTO;
import com.phonestore.dto.RoleDTO;
import com.phonestore.dto.UserDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.PasswordUtil;
import com.phonestore.util.PermissionNames;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

public class CustomerBUS {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^0\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final int DEFAULT_CUSTOMER_ROLE_ID = 4;

    private final CustomerDAO customerDAO;
    private final UserDAO userDAO;
    private final RoleDAO roleDAO;
    private final AuthBUS authBUS;

    public CustomerBUS() {
        this(new CustomerDAO(), new UserDAO(), new RoleDAO(), new AuthBUS());
    }

    public CustomerBUS(CustomerDAO customerDAO, UserDAO userDAO, RoleDAO roleDAO, AuthBUS authBUS) {
        this.customerDAO = customerDAO;
        this.userDAO = userDAO;
        this.roleDAO = roleDAO;
        this.authBUS = authBUS;
    }

    public List<CustomerDTO> getAllCustomers() {
        authBUS.requirePermission(PermissionNames.MANAGE_CUSTOMERS);
        return customerDAO.findAll();
    }

    public CustomerDTO getCustomerById(int customerId) {
        authBUS.requirePermission(PermissionNames.MANAGE_CUSTOMERS);
        if (customerId <= 0) {
            throw new BusinessException("Mã khách hàng không hợp lệ");
        }
        return customerDAO.findById(customerId);
    }

    public CustomerDTO getCustomerByUserId(int userId) {
        authBUS.requirePermission(PermissionNames.MANAGE_CUSTOMERS);
        if (userId <= 0) {
            throw new BusinessException("Mã tài khoản người dùng không hợp lệ");
        }
        return customerDAO.findByUserId(userId);
    }

    // Lấy thông tin Họ tên, SĐT, Email từ bảng Users gắn với Customer (phục vụ CustomerController)
    public UserDTO getUserOfCustomer(CustomerDTO customer) {
        if (customer == null || customer.getUserId() == null) {
            return null;
        }
        return userDAO.findById(customer.getUserId());
    }

    public List<CustomerDTO> searchCustomers(String keyword) {
        authBUS.requirePermission(PermissionNames.MANAGE_CUSTOMERS);
        if (keyword == null || keyword.isBlank()) {
            return customerDAO.findAll();
        }
        return customerDAO.search(keyword.trim());
    }

    public boolean addCustomer(CustomerDTO customer) {
        authBUS.requirePermission(PermissionNames.MANAGE_CUSTOMERS);
        validateCustomer(customer);

        if (customer.getTotalSpent() == null) {
            customer.setTotalSpent(BigDecimal.ZERO);
        }
        customer.setMembershipLevel(calculateMembershipLevel(customer.getTotalSpent()));

        if (!customerDAO.insert(customer)) {
            throw new BusinessException("Không thể thêm khách hàng vào cơ sở dữ liệu");
        }
        return true;
    }

    // Hỗ trợ CustomerController thêm mới khách hàng kèm tạo luôn bản ghi Users (họ tên, SĐT, email)
    public CustomerDTO addCustomerWithUserInfo(String fullName, String phone, String email,
                                               String address, String city) {
        authBUS.requirePermission(PermissionNames.MANAGE_CUSTOMERS);
        validateFullInput(null, fullName, phone, email, address, city);

        RoleDTO customerRole = roleDAO.findByName("Customer");
        int roleId = customerRole != null ? customerRole.getRoleId() : DEFAULT_CUSTOMER_ROLE_ID;

        UserDTO newUser = new UserDTO();
        newUser.setUsername(phone.trim());
        newUser.setEmail(email.trim());
        newUser.setPassword(PasswordUtil.hash("123456"));
        newUser.setFullName(fullName.trim());
        newUser.setPhone(phone.trim());
        newUser.setRoleId(roleId);
        newUser.setActive(true);

        int userId = userDAO.insert(newUser);
        if (userId <= 0) {
            throw new BusinessException("Không thể tạo tài khoản cho khách hàng (SĐT hoặc Email có thể đã tồn tại)");
        }

        CustomerDTO customer = new CustomerDTO();
        customer.setUserId(userId);
        customer.setAddress(address.trim());
        customer.setCity(city.trim());
        customer.setTotalSpent(BigDecimal.ZERO);
        customer.setMembershipLevel("Bronze");

        if (!customerDAO.insert(customer)) {
            userDAO.delete(userId);
            throw new BusinessException("Không thể lưu thông tin khách hàng");
        }
        return customer;
    }

    public boolean updateCustomer(CustomerDTO customer) {
        authBUS.requirePermission(PermissionNames.MANAGE_CUSTOMERS);
        if (customer == null || customer.getCustomerId() <= 0) {
            throw new BusinessException("Mã khách hàng không hợp lệ");
        }
        validateCustomer(customer);

        if (customer.getTotalSpent() == null) {
            customer.setTotalSpent(BigDecimal.ZERO);
        }
        customer.setMembershipLevel(calculateMembershipLevel(customer.getTotalSpent()));

        if (!customerDAO.update(customer)) {
            throw new BusinessException("Cập nhật thông tin khách hàng thất bại");
        }
        return true;
    }

    // Hỗ trợ CustomerController sửa đồng thời thông tin trên bảng Users và Customers
    public CustomerDTO updateCustomerWithUserInfo(int customerId, String fullName, String phone,
                                                  String email, String address, String city) {
        authBUS.requirePermission(PermissionNames.MANAGE_CUSTOMERS);
        CustomerDTO existing = customerDAO.findById(customerId);
        if (existing == null) {
            throw new BusinessException("Không tìm thấy khách hàng cần sửa");
        }

        validateFullInput(existing.getUserId(), fullName, phone, email, address, city);

        if (existing.getUserId() != null) {
            UserDTO user = userDAO.findById(existing.getUserId());
            if (user != null) {
                user.setFullName(fullName.trim());
                user.setPhone(phone.trim());
                user.setEmail(email.trim());
                if (!userDAO.update(user)) {
                    throw new BusinessException("Không thể cập nhật họ tên/SĐT/email của khách hàng");
                }
            }
        }

        existing.setAddress(address.trim());
        existing.setCity(city.trim());
        if (!customerDAO.update(existing)) {
            throw new BusinessException("Không thể cập nhật địa chỉ khách hàng");
        }
        return existing;
    }

    public boolean updateTotalSpent(int customerId, BigDecimal newTotalSpent) {
        authBUS.requirePermission(PermissionNames.MANAGE_CUSTOMERS);
        if (customerId <= 0) {
            throw new BusinessException("Mã khách hàng không hợp lệ");
        }
        if (newTotalSpent == null || newTotalSpent.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Tổng chi tiêu không được âm");
        }
        String newLevel = calculateMembershipLevel(newTotalSpent);
        return customerDAO.updateSpentAndMembership(customerId, newTotalSpent, newLevel);
    }

    public boolean deleteCustomer(int customerId) {
        authBUS.requirePermission(PermissionNames.MANAGE_CUSTOMERS);
        if (customerId <= 0) {
            throw new BusinessException("Mã khách hàng không hợp lệ");
        }
        return customerDAO.delete(customerId);
    }

    public static String calculateMembershipLevel(BigDecimal totalSpent) {
        if (totalSpent == null) return "Bronze";
        if (totalSpent.compareTo(new BigDecimal("50000000")) >= 0) {
            return "Platinum";
        } else if (totalSpent.compareTo(new BigDecimal("25000000")) >= 0) {
            return "Gold";
        } else if (totalSpent.compareTo(new BigDecimal("10000000")) >= 0) {
            return "Silver";
        }
        return "Bronze";
    }

    private void validateCustomer(CustomerDTO c) {
        if (c == null) {
            throw new BusinessException("Thông tin khách hàng không được để trống");
        }
        if (c.getAddress() == null || c.getAddress().isBlank()) {
            throw new BusinessException("Vui lòng nhập địa chỉ");
        }
        if (c.getCity() == null || c.getCity().isBlank()) {
            throw new BusinessException("Vui lòng nhập thành phố");
        }
        c.setAddress(c.getAddress().trim());
        c.setCity(c.getCity().trim());
    }

    private void validateFullInput(Integer currentUserId, String fullName, String phone,
                                   String email, String address, String city) {
        if (fullName == null || fullName.isBlank()) {
            throw new BusinessException("Vui lòng nhập họ tên.");
        }
        if (phone == null || !PHONE_PATTERN.matcher(phone.trim()).matches()) {
            throw new BusinessException("Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.");
        }
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new BusinessException("Email chưa đúng định dạng, ví dụ ten@example.com.");
        }
        if (address == null || address.isBlank()) {
            throw new BusinessException("Vui lòng nhập địa chỉ.");
        }
        if (city == null || city.isBlank()) {
            throw new BusinessException("Vui lòng nhập thành phố.");
        }

        String trimmedPhone = phone.trim();
        for (UserDTO u : userDAO.findAll()) {
            if (trimmedPhone.equals(u.getPhone())
                    && (currentUserId == null || u.getUserId() != currentUserId)) {
                throw new BusinessException("Số điện thoại này đã thuộc về một khách hàng khác.");
            }
        }
    }
}
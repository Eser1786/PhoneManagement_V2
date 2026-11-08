package com.phonestore.bus;

import com.phonestore.dao.CustomerDAO;
import com.phonestore.dao.PermissionDAO;
import com.phonestore.dao.RoleDAO;
import com.phonestore.dao.StaffDAO;
import com.phonestore.dao.UserDAO;
import com.phonestore.dto.CustomerDTO;
import com.phonestore.dto.StaffDTO;
import com.phonestore.dto.UserDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.PasswordUtil;
import com.phonestore.util.PermissionNames;
import com.phonestore.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Test CustomerBUS, StaffBUS và UserBUS bằng DAO giả, không cần database. */
class CustomerStaffUserBUSTest {

    private final Map<Integer, UserDTO> usersById = new HashMap<>();
    private final Map<Integer, CustomerDTO> customersById = new HashMap<>();
    private final Map<Integer, StaffDTO> staffById = new HashMap<>();

    private CustomerBUS customerBUS;
    private StaffBUS staffBUS;
    private UserBUS userBUS;

    @BeforeEach
    void setUp() {
        usersById.clear();
        customersById.clear();
        staffById.clear();

        UserDAO fakeUserDAO = new UserDAO() {
            @Override
            public List<UserDTO> findAll() {
                return new ArrayList<>(usersById.values());
            }

            @Override
            public UserDTO findById(int userId) {
                return usersById.get(userId);
            }

            @Override
            public UserDTO findByUsername(String username) {
                return usersById.values().stream()
                        .filter(u -> username.equals(u.getUsername()))
                        .findFirst().orElse(null);
            }

            @Override
            public int insert(UserDTO user) {
                int id = usersById.size() + 1;
                user.setUserId(id);
                usersById.put(id, user);
                return id;
            }

            @Override
            public boolean update(UserDTO user) {
                if (!usersById.containsKey(user.getUserId())) return false;
                usersById.put(user.getUserId(), user);
                return true;
            }

            @Override
            public boolean updateActiveStatus(int userId, boolean isActive) {
                UserDTO u = usersById.get(userId);
                if (u == null) return false;
                u.setActive(isActive);
                return true;
            }
        };

        CustomerDAO fakeCustomerDAO = new CustomerDAO() {
            @Override
            public boolean insert(CustomerDTO c) {
                int id = customersById.size() + 1;
                c.setCustomerId(id);
                customersById.put(id, c);
                return true;
            }

            @Override
            public CustomerDTO findById(int customerId) {
                return customersById.get(customerId);
            }

            @Override
            public boolean updateSpentAndMembership(int customerId, BigDecimal totalSpent, String membershipLevel) {
                CustomerDTO c = customersById.get(customerId);
                if (c == null) return false;
                c.setTotalSpent(totalSpent);
                c.setMembershipLevel(membershipLevel);
                return true;
            }
        };

        StaffDAO fakeStaffDAO = new StaffDAO() {
            @Override
            public StaffDTO findByUserId(int userId) {
                return staffById.values().stream()
                        .filter(s -> s.getUserId() == userId)
                        .findFirst().orElse(null);
            }

            @Override
            public boolean insert(StaffDTO s) {
                int id = staffById.size() + 1;
                s.setStaffId(id);
                staffById.put(id, s);
                return true;
            }
        };

        RoleDAO fakeRoleDAO = new RoleDAO();
        PermissionDAO fakePermissionDAO = new PermissionDAO();
        AuthBUS authBUS = new AuthBUS();

        customerBUS = new CustomerBUS(fakeCustomerDAO, fakeUserDAO, fakeRoleDAO, authBUS);
        staffBUS = new StaffBUS(fakeStaffDAO, fakeUserDAO, authBUS);
        userBUS = new UserBUS(fakeUserDAO, fakeRoleDAO, fakePermissionDAO, authBUS);

        UserDTO admin = new UserDTO();
        admin.setUserId(99);
        admin.setUsername("admin");
        SessionManager.login(admin, List.of(
                PermissionNames.MANAGE_CUSTOMERS,
                PermissionNames.MANAGE_USERS
        ));
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Test
    void membershipLevelFollowsTotalSpentThresholds() {
        assertEquals("Bronze", CustomerBUS.calculateMembershipLevel(BigDecimal.ZERO));
        assertEquals("Silver", CustomerBUS.calculateMembershipLevel(new BigDecimal("10000000")));
        assertEquals("Gold", CustomerBUS.calculateMembershipLevel(new BigDecimal("25000000")));
        assertEquals("Platinum", CustomerBUS.calculateMembershipLevel(new BigDecimal("50000000")));
    }

    @Test
    void addCustomerWithUserInfoCreatesBothUserAndCustomer() {
        CustomerDTO c = customerBUS.addCustomerWithUserInfo(
                "Nguyễn Văn A", "0901234567", "vana@example.com", "12 Lê Lợi", "TP. Hồ Chí Minh");

        assertEquals(1, c.getCustomerId());
        assertNotNull(c.getUserId());
        assertEquals("Bronze", c.getMembershipLevel());

        UserDTO linkedUser = customerBUS.getUserOfCustomer(c);
        assertNotNull(linkedUser);
        assertEquals("Nguyễn Văn A", linkedUser.getFullName());
        assertEquals("0901234567", linkedUser.getPhone());
    }

    @Test
    void createUserHashesPlainPasswordAndRejectsDuplicateUsername() {
        UserDTO u = new UserDTO();
        u.setUsername("staff_new");
        u.setPassword("123456");
        u.setFullName("Trần Văn B");
        u.setEmail("tranb@phonestore.vn");
        u.setPhone("0912345678");
        u.setRoleId(3);
        u.setActive(true);

        int id = userBUS.createUser(u);
        assertTrue(id > 0);
        assertTrue(PasswordUtil.verify("123456", usersById.get(id).getPassword()));

        assertThrows(BusinessException.class, () -> userBUS.createUser(u));
    }

    @Test
    void cannotDeactivateCurrentlyLoggedInUser() {
        assertThrows(BusinessException.class, () -> userBUS.toggleUserActive(99, false));
    }

    @Test
    void addStaffRequiresExistingUserAndPreventsDuplicateUserAssignment() {
        UserDTO u = new UserDTO();
        u.setUserId(1);
        u.setUsername("staff1");
        usersById.put(1, u);

        StaffDTO s = new StaffDTO();
        s.setUserId(1);
        s.setPosition("Nhân viên bán hàng");
        s.setSalary(new BigDecimal("10000000"));

        assertTrue(staffBUS.addStaff(s));
        assertThrows(BusinessException.class, () -> staffBUS.addStaff(s));
    }
}
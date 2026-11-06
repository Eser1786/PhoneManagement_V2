package com.phonestore.dao;

import com.phonestore.config.DBConnection;
import com.phonestore.dto.CustomerDTO;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CustomerDAO {

    private CustomerDTO mapRow(ResultSet rs) throws SQLException {
        int userId = rs.getInt("user_id");
        Integer userIdObj = rs.wasNull() ? null : userId;
        Timestamp createdTs = rs.getTimestamp("created_at");

        return new CustomerDTO(
                rs.getInt("customer_id"),
                userIdObj,
                rs.getString("address"),
                rs.getString("city"),
                rs.getString("membership_level"),
                rs.getBigDecimal("total_spent"),
                createdTs != null ? createdTs.toLocalDateTime() : null
        );
    }

    public List<CustomerDTO> findAll() {
        List<CustomerDTO> list = new ArrayList<>();
        String sql = "SELECT * FROM Customers ORDER BY customer_id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public CustomerDTO findById(int customerId) {
        String sql = "SELECT * FROM Customers WHERE customer_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public CustomerDTO findByUserId(int userId) {
        String sql = "SELECT * FROM Customers WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<CustomerDTO> search(String keyword) {
        List<CustomerDTO> list = new ArrayList<>();
        String sql = """
                SELECT c.*
                FROM Customers c
                LEFT JOIN Users u ON c.user_id = u.user_id
                WHERE u.full_name LIKE ? OR u.phone LIKE ? OR u.email LIKE ?
                   OR c.address LIKE ? OR c.city LIKE ?
                ORDER BY c.customer_id DESC
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + keyword + "%";
            for (int i = 1; i <= 5; i++) {
                ps.setString(i, pattern);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean insert(CustomerDTO c) {
        String sql = "INSERT INTO Customers (user_id, address, city, membership_level, total_spent) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (c.getUserId() != null) ps.setInt(1, c.getUserId());
            else ps.setNull(1, Types.INTEGER);
            ps.setString(2, c.getAddress());
            ps.setString(3, c.getCity());
            ps.setString(4, c.getMembershipLevel() != null ? c.getMembershipLevel() : "Bronze");
            ps.setBigDecimal(5, c.getTotalSpent() != null ? c.getTotalSpent() : BigDecimal.ZERO);

            if (ps.executeUpdate() > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) c.setCustomerId(keys.getInt(1));
                }
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean update(CustomerDTO c) {
        String sql = "UPDATE Customers SET user_id = ?, address = ?, city = ?, membership_level = ?, total_spent = ? WHERE customer_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (c.getUserId() != null) ps.setInt(1, c.getUserId());
            else ps.setNull(1, Types.INTEGER);
            ps.setString(2, c.getAddress());
            ps.setString(3, c.getCity());
            ps.setString(4, c.getMembershipLevel());
            ps.setBigDecimal(5, c.getTotalSpent());
            ps.setInt(6, c.getCustomerId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean updateSpentAndMembership(int customerId, BigDecimal totalSpent, String membershipLevel) {
        String sql = "UPDATE Customers SET total_spent = ?, membership_level = ? WHERE customer_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, totalSpent);
            ps.setString(2, membershipLevel);
            ps.setInt(3, customerId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean delete(int customerId) {
        String sql = "DELETE FROM Customers WHERE customer_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
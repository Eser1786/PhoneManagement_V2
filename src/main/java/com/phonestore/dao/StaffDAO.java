package com.phonestore.dao;

import com.phonestore.config.DBConnection;
import com.phonestore.dto.StaffDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StaffDAO {

    private StaffDTO mapRow(ResultSet rs) throws SQLException {
        Date hireDate = rs.getDate("hire_date");
        return new StaffDTO(
                rs.getInt("staff_id"),
                rs.getInt("user_id"),
                rs.getString("position"),
                rs.getBigDecimal("salary"),
                hireDate != null ? hireDate.toLocalDate() : null
        );
    }

    public List<StaffDTO> findAll() {
        List<StaffDTO> list = new ArrayList<>();
        String sql = "SELECT * FROM Staff ORDER BY staff_id DESC";
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

    public StaffDTO findById(int staffId) {
        String sql = "SELECT * FROM Staff WHERE staff_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, staffId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public StaffDTO findByUserId(int userId) {
        String sql = "SELECT * FROM Staff WHERE user_id = ?";
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

    public List<StaffDTO> search(String keyword) {
        List<StaffDTO> list = new ArrayList<>();
        String sql = """
                SELECT s.*
                FROM Staff s
                INNER JOIN Users u ON s.user_id = u.user_id
                WHERE u.full_name LIKE ? OR u.phone LIKE ? OR s.position LIKE ?
                ORDER BY s.staff_id DESC
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + keyword + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
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

    public boolean insert(StaffDTO s) {
        String sql = "INSERT INTO Staff (user_id, position, salary, hire_date) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, s.getUserId());
            ps.setString(2, s.getPosition());
            ps.setBigDecimal(3, s.getSalary());
            ps.setDate(4, Date.valueOf(s.getHireDate()));

            if (ps.executeUpdate() > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) s.setStaffId(keys.getInt(1));
                }
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean update(StaffDTO s) {
        String sql = "UPDATE Staff SET user_id = ?, position = ?, salary = ?, hire_date = ? WHERE staff_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, s.getUserId());
            ps.setString(2, s.getPosition());
            ps.setBigDecimal(3, s.getSalary());
            ps.setDate(4, Date.valueOf(s.getHireDate()));
            ps.setInt(5, s.getStaffId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean delete(int staffId) {
        String sql = "DELETE FROM Staff WHERE staff_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, staffId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
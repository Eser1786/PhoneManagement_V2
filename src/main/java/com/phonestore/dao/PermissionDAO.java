package com.phonestore.dao;

import com.phonestore.config.DBConnection;
import com.phonestore.dto.PermissionDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PermissionDAO {

    private PermissionDTO mapRow(ResultSet rs) throws SQLException {
        return new PermissionDTO(
                rs.getInt("permission_id"),
                rs.getString("permission_name"),
                rs.getString("description")
        );
    }

    public List<PermissionDTO> findAll() {
        List<PermissionDTO> list = new ArrayList<>();
        String sql = "SELECT * FROM Permissions ORDER BY permission_id ASC";
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

    public PermissionDTO findById(int permissionId) {
        String sql = "SELECT * FROM Permissions WHERE permission_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, permissionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<PermissionDTO> findByRoleId(int roleId) {
        List<PermissionDTO> list = new ArrayList<>();
        String sql = """
                SELECT p.permission_id, p.permission_name, p.description
                FROM Permissions p
                INNER JOIN RolePermissions rp ON p.permission_id = rp.permission_id
                WHERE rp.role_id = ?
                ORDER BY p.permission_id ASC
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
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

    public boolean assignPermissionToRole(int roleId, int permissionId) {
        String sql = "INSERT IGNORE INTO RolePermissions (role_id, permission_id) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            ps.setInt(2, permissionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean removePermissionFromRole(int roleId, int permissionId) {
        String sql = "DELETE FROM RolePermissions WHERE role_id = ? AND permission_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            ps.setInt(2, permissionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
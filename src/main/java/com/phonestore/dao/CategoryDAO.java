package com.phonestore.dao;

import com.phonestore.config.DBConnection;
import com.phonestore.dto.CategoryDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CategoryDAO {

    private CategoryDTO mapRow(ResultSet rs) throws SQLException {
        return new CategoryDTO(
                rs.getInt("category_id"),
                rs.getString("category_name"),
                rs.getString("description")
        );
    }

    public List<CategoryDTO> findAll() {
        List<CategoryDTO> list = new ArrayList<>();
        String sql = "SELECT * FROM Categories ORDER BY category_id DESC";
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

    public CategoryDTO findById(int categoryId) {
        String sql = "SELECT * FROM Categories WHERE category_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<CategoryDTO> searchByName(String keyword) {
        List<CategoryDTO> list = new ArrayList<>();
        String sql = "SELECT * FROM Categories WHERE category_name LIKE ? ORDER BY category_name ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + keyword + "%");
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

    public boolean insert(CategoryDTO category) {
        String sql = "INSERT INTO Categories (category_name, description) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category.getCategoryName());
            ps.setString(2, category.getDescription());
            if (ps.executeUpdate() > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) category.setCategoryId(keys.getInt(1));
                }
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean update(CategoryDTO category) {
        String sql = "UPDATE Categories SET category_name = ?, description = ? WHERE category_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category.getCategoryName());
            ps.setString(2, category.getDescription());
            ps.setInt(3, category.getCategoryId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean delete(int categoryId) {
        String sql = "DELETE FROM Categories WHERE category_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
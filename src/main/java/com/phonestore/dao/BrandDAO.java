package com.phonestore.dao;

import com.phonestore.config.DBConnection;
import com.phonestore.dto.BrandDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BrandDAO {

    private BrandDTO mapRow(ResultSet rs) throws SQLException {
        return new BrandDTO(
                rs.getInt("brand_id"),
                rs.getString("brand_name"),
                rs.getString("country"),
                rs.getString("logo_url"),
                rs.getString("description")
        );
    }

    public List<BrandDTO> findAll() {
        List<BrandDTO> list = new ArrayList<>();
        String sql = "SELECT * FROM Brands ORDER BY brand_id DESC";
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

    public BrandDTO findById(int brandId) {
        String sql = "SELECT * FROM Brands WHERE brand_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, brandId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<BrandDTO> searchByName(String keyword) {
        List<BrandDTO> list = new ArrayList<>();
        String sql = "SELECT * FROM Brands WHERE brand_name LIKE ? ORDER BY brand_name ASC";
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

    public boolean insert(BrandDTO brand) {
        String sql = "INSERT INTO Brands (brand_name, country, logo_url, description) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, brand.getBrandName());
            ps.setString(2, brand.getCountry());
            ps.setString(3, brand.getLogoUrl());
            ps.setString(4, brand.getDescription());
            if (ps.executeUpdate() > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) brand.setBrandId(keys.getInt(1));
                }
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean update(BrandDTO brand) {
        String sql = "UPDATE Brands SET brand_name = ?, country = ?, logo_url = ?, description = ? WHERE brand_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, brand.getBrandName());
            ps.setString(2, brand.getCountry());
            ps.setString(3, brand.getLogoUrl());
            ps.setString(4, brand.getDescription());
            ps.setInt(5, brand.getBrandId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean delete(int brandId) {
        String sql = "DELETE FROM Brands WHERE brand_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, brandId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
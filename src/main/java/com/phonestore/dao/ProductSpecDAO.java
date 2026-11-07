package com.phonestore.dao;

import com.phonestore.config.DBConnection;
import com.phonestore.dto.ProductSpecDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ProductSpecDAO {

    private static final String SELECT_ALL_COLUMNS =
            "SELECT spec_id, product_id, price, cost_price, stock_quantity, screen_size, processor, "
                    + "resolution, ram, storage, battery, camera_main, camera_front, weight, os, color, "
                    + "warranty_period, status FROM ProductSpecs";

    public int insert(ProductSpecDTO s) throws SQLException {
        String sql = "INSERT INTO ProductSpecs(product_id, price, cost_price, stock_quantity, screen_size, "
                + "processor, resolution, ram, storage, battery, camera_main, camera_front, weight, os, "
                + "color, warranty_period, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setInt(i++, s.getProductId());
            ps.setBigDecimal(i++, s.getPrice());
            ps.setBigDecimal(i++, s.getCostPrice());
            ps.setInt(i++, s.getStockQuantity());
            ps.setString(i++, s.getScreenSize());
            ps.setString(i++, s.getProcessor());
            ps.setString(i++, s.getResolution());
            ps.setString(i++, s.getRam());
            ps.setString(i++, s.getStorage());
            ps.setString(i++, s.getBattery());
            ps.setString(i++, s.getCameraMain());
            ps.setString(i++, s.getCameraFront());
            ps.setString(i++, s.getWeight());
            ps.setString(i++, s.getOs());
            ps.setString(i++, s.getColor());
            ps.setString(i++, s.getWarrantyPeriod());
            ps.setString(i, s.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        }
    }

    public boolean update(ProductSpecDTO s) throws SQLException {
        String sql = "UPDATE ProductSpecs SET product_id = ?, price = ?, cost_price = ?, stock_quantity = ?, "
                + "screen_size = ?, processor = ?, resolution = ?, ram = ?, storage = ?, battery = ?, "
                + "camera_main = ?, camera_front = ?, weight = ?, os = ?, color = ?, warranty_period = ?, "
                + "status = ? WHERE spec_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, s.getProductId());
            ps.setBigDecimal(i++, s.getPrice());
            ps.setBigDecimal(i++, s.getCostPrice());
            ps.setInt(i++, s.getStockQuantity());
            ps.setString(i++, s.getScreenSize());
            ps.setString(i++, s.getProcessor());
            ps.setString(i++, s.getResolution());
            ps.setString(i++, s.getRam());
            ps.setString(i++, s.getStorage());
            ps.setString(i++, s.getBattery());
            ps.setString(i++, s.getCameraMain());
            ps.setString(i++, s.getCameraFront());
            ps.setString(i++, s.getWeight());
            ps.setString(i++, s.getOs());
            ps.setString(i++, s.getColor());
            ps.setString(i++, s.getWarrantyPeriod());
            ps.setString(i++, s.getStatus());
            ps.setInt(i, s.getSpecId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int specId) throws SQLException {
        String sql = "DELETE FROM ProductSpecs WHERE spec_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, specId);
            return ps.executeUpdate() > 0;
        }
    }

    public ProductSpecDTO findById(int specId) throws SQLException {
        String sql = SELECT_ALL_COLUMNS + " WHERE spec_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, specId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public List<ProductSpecDTO> findByProductId(int productId) throws SQLException {
        String sql = SELECT_ALL_COLUMNS + " WHERE product_id = ? ORDER BY price";
        List<ProductSpecDTO> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }


    private ProductSpecDTO mapRow(ResultSet rs) throws SQLException {
        ProductSpecDTO s = new ProductSpecDTO();
        s.setSpecId(rs.getInt("spec_id"));
        s.setProductId(rs.getInt("product_id"));
        s.setPrice(rs.getBigDecimal("price"));
        s.setCostPrice(rs.getBigDecimal("cost_price"));
        s.setStockQuantity(rs.getInt("stock_quantity"));
        s.setScreenSize(rs.getString("screen_size"));
        s.setProcessor(rs.getString("processor"));
        s.setResolution(rs.getString("resolution"));
        s.setRam(rs.getString("ram"));
        s.setStorage(rs.getString("storage"));
        s.setBattery(rs.getString("battery"));
        s.setCameraMain(rs.getString("camera_main"));
        s.setCameraFront(rs.getString("camera_front"));
        s.setWeight(rs.getString("weight"));
        s.setOs(rs.getString("os"));
        s.setColor(rs.getString("color"));
        s.setWarrantyPeriod(rs.getString("warranty_period"));
        s.setStatus(rs.getString("status"));
        return s;
    }
}
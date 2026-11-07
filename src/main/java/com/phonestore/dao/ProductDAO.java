package com.phonestore.dao;

import com.phonestore.config.DBConnection;
import com.phonestore.dto.ProductDTO;
import com.phonestore.dto.ProductFilter;
import com.phonestore.dto.ProductListItemDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;


public class ProductDAO {

    private static final String SEARCH_BASE =
            "SELECT s.spec_id, p.product_id, p.name AS product_name, "
                    + "b.brand_name, c.category_name, "
                    + "s.price, s.stock_quantity, s.ram, s.storage, s.color, s.status "
                    + "FROM ProductSpecs s "
                    + "JOIN Products p ON p.product_id = s.product_id "
                    + "LEFT JOIN Brands b ON b.brand_id = p.brand_id "
                    + "LEFT JOIN Categories c ON c.category_id = p.category_id "
                    + "WHERE 1 = 1";

    public int insert(ProductDTO p) throws SQLException {
        String sql = "INSERT INTO Products(name, brand_id, category_id, description) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getName());
            ps.setInt(2, p.getBrandId());
            ps.setInt(3, p.getCategoryId());
            ps.setString(4, p.getDescription());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        }
    }

    public boolean update(ProductDTO p) throws SQLException {
        String sql = "UPDATE Products SET name = ?, brand_id = ?, category_id = ?, description = ? "
                + "WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getName());
            ps.setInt(2, p.getBrandId());
            ps.setInt(3, p.getCategoryId());
            ps.setString(4, p.getDescription());
            ps.setInt(5, p.getProductId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int productId) throws SQLException {
        String sql = "DELETE FROM Products WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            return ps.executeUpdate() > 0;
        }
    }

    public ProductDTO findById(int productId) throws SQLException {
        String sql = "SELECT product_id, name, brand_id, category_id, description "
                + "FROM Products WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public List<ProductDTO> findAll() throws SQLException {
        String sql = "SELECT product_id, name, brand_id, category_id, description "
                + "FROM Products ORDER BY name";
        List<ProductDTO> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    public List<ProductListItemDTO> search(ProductFilter f) throws SQLException {
        StringBuilder sql = new StringBuilder(SEARCH_BASE);
        List<Object> params = new ArrayList<>();

        if (f != null) {
            if (f.getBrandId() != null) {
                sql.append(" AND p.brand_id = ?");
                params.add(f.getBrandId());
            }
            if (f.getCategoryId() != null) {
                sql.append(" AND p.category_id = ?");
                params.add(f.getCategoryId());
            }
            if (f.getMinPrice() != null) {
                sql.append(" AND s.price >= ?");
                params.add(f.getMinPrice());
            }
            if (f.getMaxPrice() != null) {
                sql.append(" AND s.price <= ?");
                params.add(f.getMaxPrice());
            }
            if (f.getStatus() != null && !f.getStatus().isBlank()) {
                sql.append(" AND s.status = ?");
                params.add(f.getStatus());
            }
            if (f.getKeyword() != null && !f.getKeyword().isBlank()) {
                sql.append(" AND p.name LIKE ?");
                params.add("%" + escapeLike(f.getKeyword().trim()) + "%");
            }
        }
        sql.append(" ORDER BY p.name, s.price");

        List<ProductListItemDTO> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapListItem(rs));
                }
            }
        }
        return result;
    }

    private ProductDTO mapRow(ResultSet rs) throws SQLException {
        ProductDTO p = new ProductDTO();
        p.setProductId(rs.getInt("product_id"));
        p.setName(rs.getString("name"));
        p.setBrandId(rs.getInt("brand_id"));
        p.setCategoryId(rs.getInt("category_id"));
        p.setDescription(rs.getString("description"));
        return p;
    }

    private ProductListItemDTO mapListItem(ResultSet rs) throws SQLException {
        ProductListItemDTO item = new ProductListItemDTO();
        item.setSpecId(rs.getInt("spec_id"));
        item.setProductId(rs.getInt("product_id"));
        item.setProductName(rs.getString("product_name"));
        item.setBrandName(rs.getString("brand_name"));
        item.setCategoryName(rs.getString("category_name"));
        item.setPrice(rs.getBigDecimal("price"));
        item.setStockQuantity(rs.getInt("stock_quantity"));
        item.setRam(rs.getString("ram"));
        item.setStorage(rs.getString("storage"));
        item.setColor(rs.getString("color"));
        item.setStatus(rs.getString("status"));
        return item;
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
package com.phonestore.dao;

import com.phonestore.config.DBConnection;
import com.phonestore.dto.InventoryDTO;
import com.phonestore.dto.ProductSpecDTO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InventoryDAO {

    private InventoryDTO mapRow(ResultSet rs) throws SQLException {
        int specId = rs.getInt("spec_id");
        Integer specIdObj = rs.wasNull() ? null : specId;
        Timestamp ts = rs.getTimestamp("transaction_date");

        return new InventoryDTO(
                rs.getInt("inventory_id"),
                specIdObj,
                rs.getInt("quantity_in"),
                rs.getInt("quantity_out"),
                rs.getString("transaction_type"),
                ts != null ? ts.toLocalDateTime() : null,
                rs.getString("notes")
        );
    }

    public List<InventoryDTO> findAll() {
        List<InventoryDTO> list = new ArrayList<>();
        String sql = "SELECT * FROM Inventory ORDER BY transaction_date DESC, inventory_id DESC";
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

    public List<InventoryDTO> findBySpecId(int specId) {
        List<InventoryDTO> list = new ArrayList<>();
        String sql = "SELECT * FROM Inventory WHERE spec_id = ? ORDER BY transaction_date DESC, inventory_id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, specId);
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

    // Ghi phiếu nhập/xuất vào Inventory và đồng bộ ProductSpecs.stock_quantity trong 1 Transaction
    public boolean recordTransactionAndUpdateStock(InventoryDTO log) {
        int delta = log.getQuantityIn() - log.getQuantityOut();
        String updateStockSql = "UPDATE ProductSpecs SET stock_quantity = stock_quantity + ? WHERE spec_id = ? AND (stock_quantity + ?) >= 0";
        String insertLogSql = "INSERT INTO Inventory (spec_id, quantity_in, quantity_out, transaction_type, notes) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psStock = conn.prepareStatement(updateStockSql);
                 PreparedStatement psLog = conn.prepareStatement(insertLogSql, Statement.RETURN_GENERATED_KEYS)) {

                psStock.setInt(1, delta);
                psStock.setInt(2, log.getSpecId());
                psStock.setInt(3, delta);
                if (psStock.executeUpdate() == 0) {
                    conn.rollback();
                    return false;
                }

                psLog.setInt(1, log.getSpecId());
                psLog.setInt(2, log.getQuantityIn());
                psLog.setInt(3, log.getQuantityOut());
                psLog.setString(4, log.getTransactionType());
                psLog.setString(5, log.getNotes());
                psLog.executeUpdate();

                try (ResultSet keys = psLog.getGeneratedKeys()) {
                    if (keys.next()) log.setInventoryId(keys.getInt(1));
                }

                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                ex.printStackTrace();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // Lấy danh sách các phiên bản điện thoại có tồn kho <= ngưỡng cảnh báo (phục vụ cảnh báo sắp hết hàng)
    public List<ProductSpecDTO> findLowStockSpecs(int threshold) {
        List<ProductSpecDTO> list = new ArrayList<>();
        String sql = "SELECT * FROM ProductSpecs WHERE stock_quantity <= ? ORDER BY stock_quantity ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, threshold);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int pid = rs.getInt("product_id");
                    ProductSpecDTO spec = new ProductSpecDTO(
                            rs.getInt("spec_id"),
                            rs.wasNull() ? null : pid,
                            rs.getBigDecimal("price"),
                            rs.getBigDecimal("cost_price"),
                            rs.getInt("stock_quantity"),
                            rs.getString("screen_size"),
                            rs.getString("processor"),
                            rs.getString("resolution"),
                            rs.getString("ram"),
                            rs.getString("storage"),
                            rs.getString("battery"),
                            rs.getString("camera_main"),
                            rs.getString("camera_front"),
                            rs.getString("weight"),
                            rs.getString("os"),
                            rs.getString("color"),
                            rs.getString("warranty_period"),
                            rs.getString("status")
                    );
                    list.add(spec);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}
package com.phonestore.dao;

import com.phonestore.config.DBConnection;
import com.phonestore.dto.ProductSpecDTO;
import com.phonestore.util.OrderConstants;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Truy cập bảng ProductSpecs (các phiên bản / cấu hình của một sản phẩm).
 *
 * DAO chỉ chứa SQL, không kiểm tra nghiệp vụ. Mọi lỗi SQL được ném ra
 * (SQLException) để lớp BUS xử lý.
 *
 * Lưu ý: việc trừ/cộng tồn kho khi bán hàng hoặc nhập kho nên làm trong
 * transaction của OrderDAO / InventoryDAO (dùng chung một Connection), không
 * gọi update() ở đây cho việc đó.
 */
public class ProductSpecDAO {

    private static final String SELECT_ALL_COLUMNS =
            "SELECT spec_id, product_id, price, cost_price, stock_quantity, screen_size, processor, "
                    + "resolution, ram, storage, battery, camera_main, camera_front, weight, os, color, "
                    + "warranty_period, status FROM ProductSpecs";

    /** Ghi chú của dòng Inventory tạo khi thêm phiên bản có tồn kho ban đầu. */
    public static final String INITIAL_STOCK_NOTE = "Tồn kho ban đầu";

    /** Thêm phiên bản, trả về spec_id mới (hoặc -1 nếu không lấy được khóa). */
    public int insert(ProductSpecDTO s) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, s);
        }
    }

    /** Thêm phiên bản (+ Inventory ban đầu nếu có tồn kho) trong một transaction riêng. */
    public int insertWithInitialStock(ProductSpecDTO s) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int id = insertWithInitialStock(conn, s);
                conn.commit();
                return id;
            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /** Thêm phiên bản; nếu tồn kho > 0 thì ghi Inventory "Nhập kho" - "Tồn kho ban đầu" cùng transaction. */
    public int insertWithInitialStock(Connection conn, ProductSpecDTO s) throws SQLException {
        int specId = insert(conn, s);
        if (s.getStockQuantity() > 0) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO Inventory(spec_id, quantity_in, quantity_out, transaction_type, notes) "
                            + "VALUES (?, ?, 0, ?, ?)")) {
                ps.setInt(1, specId);
                ps.setInt(2, s.getStockQuantity());
                ps.setString(3, OrderConstants.INVENTORY_IN);
                ps.setString(4, INITIAL_STOCK_NOTE);
                ps.executeUpdate();
            }
        }
        return specId;
    }

    /** Như insert(s) nhưng dùng Connection của transaction bên ngoài. */
    public int insert(Connection conn, ProductSpecDTO s) throws SQLException {
        String sql = "INSERT INTO ProductSpecs(product_id, price, cost_price, stock_quantity, screen_size, "
                + "processor, resolution, ram, storage, battery, camera_main, camera_front, weight, os, "
                + "color, warranty_period, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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

    /** Cập nhật phiên bản theo specId. Trả về true nếu có dòng được cập nhật. */
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

    /**
     * Xóa phiên bản theo specId. Nếu phiên bản đã nằm trong OrderDetails hoặc Inventory,
     * MySQL ném SQLException (vi phạm khóa ngoại).
     */
    public boolean delete(int specId) throws SQLException {
        String sql = "DELETE FROM ProductSpecs WHERE spec_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, specId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Tìm theo specId. Trả về null nếu không có. */
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

    /** Lấy mọi phiên bản của một sản phẩm, sắp theo giá tăng dần. */
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


    /**
     * Cập nhật thông tin phiên bản NHƯNG KHÔNG đụng vào stock_quantity (tồn kho chỉ đổi qua bán hàng / nhập kho,
     * để không ghi đè số liệu đang được đơn hàng thay đổi cùng lúc).
     */
    public boolean updateKeepStock(ProductSpecDTO s) throws SQLException {
        String sql = "UPDATE ProductSpecs SET price = ?, cost_price = ?, screen_size = ?, processor = ?, "
                + "resolution = ?, ram = ?, storage = ?, battery = ?, camera_main = ?, camera_front = ?, "
                + "weight = ?, os = ?, color = ?, warranty_period = ?, status = ? WHERE spec_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setBigDecimal(i++, s.getPrice());
            ps.setBigDecimal(i++, s.getCostPrice());
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

    public boolean updateStatus(int specId, String status) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE ProductSpecs SET status = ? WHERE spec_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, specId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Phiên bản đã nằm trong đơn hàng hoặc có lịch sử kho khác "Tồn kho ban đầu" chưa. */
    public boolean hasHistory(int specId) throws SQLException {
        String sql = "SELECT EXISTS(SELECT 1 FROM OrderDetails WHERE spec_id = ?) "
                + "OR EXISTS(SELECT 1 FROM Inventory WHERE spec_id = ? AND notes <> ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, specId);
            ps.setInt(2, specId);
            ps.setString(3, INITIAL_STOCK_NOTE);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 1;
            }
        }
    }

    /** Xóa cứng phiên bản chưa từng phát sinh giao dịch (xóa luôn dòng Inventory ban đầu). */
    public boolean deleteWithInitialInventory(int specId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Inventory WHERE spec_id = ?")) {
                    ps.setInt(1, specId);
                    ps.executeUpdate();
                }
                boolean deleted;
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ProductSpecs WHERE spec_id = ?")) {
                    ps.setInt(1, specId);
                    deleted = ps.executeUpdate() > 0;
                }
                conn.commit();
                return deleted;
            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /** Số phiên bản chưa ngừng kinh doanh của một sản phẩm. */
    public int countActive(int productId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM ProductSpecs WHERE product_id = ? AND status <> ?")) {
            ps.setInt(1, productId);
            ps.setString(2, com.phonestore.util.ProductStatuses.DISCONTINUED);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

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

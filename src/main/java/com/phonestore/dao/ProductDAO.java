package com.phonestore.dao;

import com.phonestore.config.DBConnection;
import com.phonestore.dto.ProductDTO;
import com.phonestore.dto.ProductFilter;
import com.phonestore.dto.ProductListItemDTO;
import com.phonestore.dto.ProductSpecDTO;
import com.phonestore.util.ProductStatuses;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Truy cập bảng Products và truy vấn danh sách sản phẩm (join với ProductSpecs,
 * Brands, Categories).
 *
 * DAO chỉ chứa SQL, không kiểm tra nghiệp vụ. Mọi lỗi SQL được ném ra
 * (SQLException) để lớp BUS xử lý.
 *
 * Lưu ý khi xóa: ProductSpecs bị xóa theo (ON DELETE CASCADE), nhưng nếu một
 * phiên bản đã nằm trong OrderDetails thì MySQL sẽ từ chối. Ở lớp BUS nên dùng
 * "xóa mềm" (đổi status thành 'Ngừng kinh doanh') cho sản phẩm đã từng bán.
 */
public class ProductDAO {

    /** Phần SELECT/JOIN dùng cho search(). LEFT JOIN vì brand_id/category_id cho phép NULL. */
    private static final String SEARCH_BASE =
            "SELECT s.spec_id, p.product_id, p.name AS product_name, "
                    + "b.brand_name, c.category_name, "
                    + "s.price, s.stock_quantity, s.ram, s.storage, s.color, s.status "
                    + "FROM ProductSpecs s "
                    + "JOIN Products p ON p.product_id = s.product_id "
                    + "LEFT JOIN Brands b ON b.brand_id = p.brand_id "
                    + "LEFT JOIN Categories c ON c.category_id = p.category_id "
                    + "WHERE 1 = 1";

    /** Thêm sản phẩm, trả về product_id mới (hoặc -1 nếu không lấy được khóa). */
    public int insert(ProductDTO p) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, p);
        }
    }

    /** Như insert(p) nhưng dùng Connection của transaction bên ngoài. */
    public int insert(Connection conn, ProductDTO p) throws SQLException {
        String sql = "INSERT INTO Products(name, brand_id, category_id, description) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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

    /**
     * Thêm sản phẩm cùng các phiên bản trong MỘT transaction. Phiên bản có tồn kho ban đầu > 0
     * được ghi thêm một dòng Inventory "Nhập kho" ("Tồn kho ban đầu"). Lỗi ở đâu cũng rollback hết.
     * Trả về product_id mới.
     */
    public int insertWithSpecs(ProductDTO product, List<ProductSpecDTO> specs, ProductSpecDAO specDAO)
            throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int productId = insert(conn, product);
                for (ProductSpecDTO spec : specs) {
                    spec.setProductId(productId);
                    specDAO.insertWithInitialStock(conn, spec);
                }
                conn.commit();
                return productId;
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

    /** Sản phẩm đã có phiên bản nằm trong đơn hàng hoặc lịch sử kho chưa (nếu có thì chỉ được xóa mềm). */
    public boolean hasHistory(int productId) throws SQLException {
        String sql = "SELECT EXISTS(SELECT 1 FROM OrderDetails od JOIN ProductSpecs s ON s.spec_id = od.spec_id "
                + "WHERE s.product_id = ?) "
                + "OR EXISTS(SELECT 1 FROM Inventory i JOIN ProductSpecs s ON s.spec_id = i.spec_id "
                + "WHERE s.product_id = ? AND i.notes <> ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, productId);
            ps.setString(3, ProductSpecDAO.INITIAL_STOCK_NOTE);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 1;
            }
        }
    }

    /** Xóa cứng sản phẩm chưa từng phát sinh giao dịch: xóa Inventory ban đầu rồi xóa Products (Specs xóa theo). */
    public boolean deleteWithInitialInventory(int productId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE i FROM Inventory i JOIN ProductSpecs s ON s.spec_id = i.spec_id "
                                + "WHERE s.product_id = ?")) {
                    ps.setInt(1, productId);
                    ps.executeUpdate();
                }
                boolean deleted;
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Products WHERE product_id = ?")) {
                    ps.setInt(1, productId);
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

    /** Ngừng kinh doanh mọi phiên bản của sản phẩm (xóa mềm). */
    public int discontinue(int productId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE ProductSpecs SET status = ? WHERE product_id = ?")) {
            ps.setString(1, ProductStatuses.DISCONTINUED);
            ps.setInt(2, productId);
            return ps.executeUpdate();
        }
    }

    /** Cập nhật sản phẩm theo productId. Trả về true nếu có dòng được cập nhật. */
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

    /** Xóa sản phẩm (kèm các phiên bản ProductSpecs của nó). Trả về true nếu có dòng bị xóa. */
    public boolean delete(int productId) throws SQLException {
        String sql = "DELETE FROM Products WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Tìm theo id. Trả về null nếu không có. */
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

    /** Lấy tất cả sản phẩm (chỉ bảng Products, không kèm cấu hình). */
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

    /**
     * Tìm kiếm, lọc theo hãng / loại / khoảng giá / trạng thái / từ khóa tên.
     * Mỗi dòng kết quả là một phiên bản (ProductSpecs) kèm tên hãng, tên loại.
     * Filter null hoặc field null nghĩa là không lọc theo điều kiện đó.
     */
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
            if (f.getMaxStock() != null) {
                sql.append(" AND s.stock_quantity <= ?");
                params.add(f.getMaxStock());
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

    // ---------------------------------------------------------------- helpers

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

    /** Thoát các ký tự đặc biệt của LIKE để từ khóa chứa % hoặc _ vẫn tìm đúng. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

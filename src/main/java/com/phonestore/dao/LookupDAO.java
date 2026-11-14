package com.phonestore.dao;

import com.phonestore.config.DBConnection;
import com.phonestore.dto.LookupItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Danh sách tra cứu (hãng, loại) để đổ vào ComboBox. */
public class LookupDAO {

    public List<LookupItem> findBrands() throws SQLException {
        return query("SELECT brand_id, brand_name FROM Brands ORDER BY brand_name");
    }

    public List<LookupItem> findCategories() throws SQLException {
        return query("SELECT category_id, category_name FROM Categories ORDER BY category_name");
    }

    private List<LookupItem> query(String sql) throws SQLException {
        List<LookupItem> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new LookupItem(rs.getInt(1), rs.getString(2)));
            }
        }
        return result;
    }
}

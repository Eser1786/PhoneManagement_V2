package com.phonestore;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.phonestore.config.DBConnection;
import com.phonestore.gui.SceneNavigator;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Điểm khởi chạy ứng dụng. Hiện tại chỉ để thử nghiệm:
 *  1. In nội dung các bảng trong database ra console.
 *  2. Mở một cửa sổ JavaFX trống.
 */
public class App extends Application {

    /** Số dòng tối đa in ra cho mỗi bảng. */
    private static final int MAX_ROWS = 20;

    @Override
    public void start(Stage stage) {
        printAllTables();

        SceneNavigator.init(stage);
        SceneNavigator.switchScene("Login.fxml", "Đăng nhập");
        stage.show();
    }

    @Override
    public void stop() {
        DBConnection.close();
    }

    /** In tên và nội dung (tối đa MAX_ROWS dòng) của mọi bảng trong database. */
    static void printAllTables() {
        try (Connection conn = DBConnection.getConnection()) {
            System.out.println("=== Đã kết nối database: " + conn.getCatalog() + " ===");

            List<String> tables = new ArrayList<>();
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }

            for (String table : tables) {
                printTable(conn, table);
            }
        } catch (SQLException e) {
            System.err.println("Không kết nối được database: " + e.getMessage());
            System.err.println("Hãy kiểm tra: đã chạy 'docker compose up -d' chưa, "
                    + "và db.properties có đúng cổng/mật khẩu không.");
        }
    }

    private static void printTable(Connection conn, String table) throws SQLException {
        String sql = "SELECT * FROM `" + table + "` LIMIT " + MAX_ROWS;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();

            System.out.println();
            System.out.println("--- " + table + " ---");

            StringBuilder header = new StringBuilder();
            for (int i = 1; i <= cols; i++) {
                header.append(md.getColumnLabel(i)).append(i < cols ? " | " : "");
            }
            System.out.println(header);

            int rows = 0;
            while (rs.next()) {
                StringBuilder line = new StringBuilder();
                for (int i = 1; i <= cols; i++) {
                    line.append(rs.getString(i)).append(i < cols ? " | " : "");
                }
                System.out.println(line);
                rows++;
            }
            if (rows == 0) {
                System.out.println("(bảng trống)");
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
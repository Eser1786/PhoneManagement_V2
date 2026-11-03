package com.phonestore.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Cung cấp kết nối tới MySQL cho toàn bộ ứng dụng (dùng connection pool HikariCP).
 *
 * Cấu hình đọc từ src/main/resources/db.properties.
 * Có thể ghi đè bằng biến môi trường DB_URL, DB_USER, DB_PASSWORD.
 *
 * Cách dùng:
 *   try (Connection conn = DBConnection.getConnection()) {
 *       ...
 *   }
 * Luôn dùng try-with-resources để trả kết nối về pool.
 */
public final class DBConnection {

    private static HikariDataSource dataSource;

    private DBConnection() {
    }

    public static synchronized Connection getConnection() throws SQLException {
        if (dataSource == null) {
            dataSource = createDataSource();
        }
        return dataSource.getConnection();
    }

    /** Gọi khi tắt ứng dụng để đóng pool. */
    public static synchronized void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private static HikariDataSource createDataSource() {
        Properties props = loadProperties();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(pick("DB_URL", props.getProperty("db.url")));
        config.setUsername(pick("DB_USER", props.getProperty("db.user")));
        config.setPassword(pick("DB_PASSWORD", props.getProperty("db.password")));
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(5000); // 5 giây, lỗi nhanh nếu chưa bật Docker
        return new HikariDataSource(config);
    }

    private static Properties loadProperties() {
        try (InputStream in = DBConnection.class.getResourceAsStream("/db.properties")) {
            if (in == null) {
                throw new IllegalStateException(
                        "Không tìm thấy db.properties trong src/main/resources");
            }
            Properties props = new Properties();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return props;
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được db.properties", e);
        }
    }

    private static String pick(String envName, String fileValue) {
        String env = System.getenv(envName);
        return (env != null && !env.isBlank()) ? env : fileValue;
    }
}
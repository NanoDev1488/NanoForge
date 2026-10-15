package ru.nanodev.nanoforge.database;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages database connections (SQLite and MySQL) for addons.
 */
public class DatabaseManager {

    private static final Logger LOGGER = Bukkit.getLogger();

    private final String url;
    private final String user;
    private final String password;
    private final boolean valid;

    public DatabaseManager(File addonFolder, ConfigurationSection dbConfig) {
        if (dbConfig == null) {
            this.url = null;
            this.user = null;
            this.password = null;
            this.valid = false;
            return;
        }

        String type = dbConfig.getString("type", "sqlite").toLowerCase();
        if ("sqlite".equals(type)) {
            String dbFile = dbConfig.getString("file", "database.db");
            File file = new File(addonFolder, dbFile);
            this.url = "jdbc:sqlite:" + file.getAbsolutePath();
            this.user = null;
            this.password = null;
            this.valid = true;
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException ignored) {}
        } else if ("mysql".equals(type) || "mariadb".equals(type)) {
            String host = dbConfig.getString("host", "localhost");
            int port = dbConfig.getInt("port", 3306);
            String database = dbConfig.getString("database", "minecraft");
            this.user = dbConfig.getString("user", "root");
            this.password = dbConfig.getString("password", "");
            this.url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";
            this.valid = true;
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                try {
                    Class.forName("com.mysql.jdbc.Driver");
                } catch (ClassNotFoundException ignored) {}
            }
        } else {
            this.url = null;
            this.user = null;
            this.password = null;
            this.valid = false;
        }
    }

    public Connection getConnection() throws SQLException {
        if (!valid || url == null) {
            throw new SQLException("Database not configured or invalid configuration.");
        }
        if (user != null) {
            return DriverManager.getConnection(url, user, password);
        } else {
            return DriverManager.getConnection(url);
        }
    }

    public int executeUpdate(String sql, List<Object> params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "[NanoForge] Database execute error: " + e.getMessage(), e);
            return -1;
        }
    }

    public List<Map<String, Object>> executeQuery(String sql, List<Object> params) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "[NanoForge] Database query error: " + e.getMessage(), e);
        }
        return rows;
    }

    public boolean isValid() {
        return valid;
    }
}

package com.example.virtualization.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DbService {
    private final JdbcTemplate jdbc;

    public DbService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initializeDatabase() {
        // api_registry
        jdbc.execute("CREATE TABLE IF NOT EXISTS api_registry (id TEXT PRIMARY KEY, name TEXT, version TEXT DEFAULT 'v1', method TEXT, endpoint TEXT, category TEXT, environment TEXT, description TEXT, baseUrl TEXT, healthCheckHeaders TEXT, healthCheckBody TEXT, healthCheckParams TEXT, retryOn500 INTEGER DEFAULT 0, isCustom INTEGER DEFAULT 0, ownerGroup TEXT DEFAULT 'admin', createdAt DATETIME DEFAULT CURRENT_TIMESTAMP)");

        // stubs
        jdbc.execute("CREATE TABLE IF NOT EXISTS stubs (id TEXT PRIMARY KEY, name TEXT NOT NULL, method TEXT NOT NULL, endpoint TEXT NOT NULL, baseUrl TEXT DEFAULT '', environment TEXT DEFAULT 'Dev', description TEXT, requestMatcher TEXT, responseStatus INTEGER DEFAULT 200, responseBody TEXT, responseHeaders TEXT, delay INTEGER DEFAULT 0, enabled INTEGER DEFAULT 1, category TEXT DEFAULT 'other', version TEXT DEFAULT 'v1', ownerGroup TEXT DEFAULT 'admin', createdAt DATETIME DEFAULT CURRENT_TIMESTAMP)");
        ensureColumns("stubs", Map.ofEntries(
            Map.entry("id", "TEXT"),
            Map.entry("name", "TEXT"),
            Map.entry("method", "TEXT"),
            Map.entry("endpoint", "TEXT NOT NULL DEFAULT ''"),
            Map.entry("baseUrl", "TEXT DEFAULT ''"),
            Map.entry("environment", "TEXT DEFAULT 'Dev'"),
            Map.entry("description", "TEXT"),
            Map.entry("requestMatcher", "TEXT"),
            Map.entry("responseStatus", "INTEGER DEFAULT 200"),
            Map.entry("responseBody", "TEXT"),
            Map.entry("responseHeaders", "TEXT"),
            Map.entry("delay", "INTEGER DEFAULT 0"),
            Map.entry("enabled", "INTEGER DEFAULT 1"),
            Map.entry("category", "TEXT DEFAULT 'other'"),
            Map.entry("version", "TEXT DEFAULT 'v1'"),
            Map.entry("ownerGroup", "TEXT DEFAULT 'admin'"),
            Map.entry("createdAt", "DATETIME")
        ));

        // stub_versions
        jdbc.execute("CREATE TABLE IF NOT EXISTS stub_versions (versionId TEXT PRIMARY KEY, stubId TEXT NOT NULL, version TEXT NOT NULL, versionTag TEXT, responseStatus INTEGER DEFAULT 200, responseBody TEXT, responseHeaders TEXT, isActive INTEGER DEFAULT 0, createdAt DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (stubId) REFERENCES stubs(id) ON DELETE CASCADE)");

        // requests
        jdbc.execute("CREATE TABLE IF NOT EXISTS requests (id TEXT PRIMARY KEY, method TEXT NOT NULL, url TEXT NOT NULL, baseUrl TEXT, endpoint TEXT, headers TEXT NOT NULL, body TEXT, bodyS3Key TEXT, timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, status INTEGER, response TEXT, responseS3Key TEXT, responseHeaders TEXT, isRecorded INTEGER DEFAULT 0, category TEXT DEFAULT 'other', ownerGroup TEXT DEFAULT 'admin', source TEXT DEFAULT 'live-api')");
        ensureColumns("requests", Map.ofEntries(
            Map.entry("id", "TEXT"),
            Map.entry("method", "TEXT"),
            Map.entry("url", "TEXT"),
            Map.entry("baseUrl", "TEXT"),
            Map.entry("endpoint", "TEXT"),
            Map.entry("headers", "TEXT NOT NULL DEFAULT ''"),
            Map.entry("body", "TEXT"),
            Map.entry("bodyS3Key", "TEXT"),
            Map.entry("timestamp", "TIMESTAMP"),
            Map.entry("status", "INTEGER"),
            Map.entry("response", "TEXT"),
            Map.entry("responseS3Key", "TEXT"),
            Map.entry("responseHeaders", "TEXT"),
            Map.entry("isRecorded", "INTEGER DEFAULT 0"),
            Map.entry("category", "TEXT DEFAULT 'other'"),
            Map.entry("ownerGroup", "TEXT DEFAULT 'admin'"),
            Map.entry("source", "TEXT DEFAULT 'live-api'")
        ));

        // settings
        jdbc.execute("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)");

        // users
        jdbc.execute("CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY, password TEXT NOT NULL, email TEXT, role TEXT, adGroup TEXT, createdAt DATETIME DEFAULT CURRENT_TIMESTAMP)");
        ensureColumns("users", Map.ofEntries(
            Map.entry("username", "TEXT"),
            Map.entry("password", "TEXT NOT NULL DEFAULT ''"),
            Map.entry("email", "TEXT"),
            Map.entry("role", "TEXT"),
            Map.entry("adGroup", "TEXT"),
            Map.entry("createdAt", "DATETIME")
        ));
    }

    private void ensureColumns(String table, Map<String, String> expectedColumns) {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            Set<String> existingColumns = new HashSet<>();
            try (ResultSet result = connection.getMetaData().getColumns(connection.getCatalog(), null, table, null)) {
                while (result.next()) {
                    existingColumns.add(result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }

            try (Statement statement = connection.createStatement()) {
                for (Map.Entry<String, String> column : expectedColumns.entrySet()) {
                    if (!existingColumns.contains(column.getKey().toLowerCase(Locale.ROOT))) {
                        statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column.getKey() + " " + column.getValue());
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to migrate " + table + " table", e);
        }
    }
}

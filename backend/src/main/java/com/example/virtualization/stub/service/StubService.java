package com.example.virtualization.stub.service;

import com.example.virtualization.model.Stub;
import com.example.virtualization.model.StubVersion;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class StubService {
    private final JdbcTemplate jdbc;

    public StubService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Stub> getAllStubs() {
        return jdbc.query("SELECT * FROM stubs ORDER BY createdAt DESC", (rs, rowNum) -> {
            Stub stub = new Stub();
            stub.setId(rs.getString("id"));
            stub.setName(rs.getString("name"));
            stub.setMethod(rs.getString("method"));
            stub.setEndpoint(rs.getString("endpoint"));
            stub.setBaseUrl(rs.getString("baseUrl"));
            stub.setEnvironment(rs.getString("environment"));
            stub.setDescription(rs.getString("description"));
            stub.setRequestMatcher(rs.getString("requestMatcher"));
            stub.setResponseStatus(rs.getInt("responseStatus"));
            stub.setResponseBody(rs.getString("responseBody"));
            stub.setResponseHeaders(rs.getString("responseHeaders"));
            stub.setDelay(rs.getInt("delay"));
            stub.setEnabled(rs.getInt("enabled") == 1);
            stub.setCategory(rs.getString("category"));
            stub.setVersion(rs.getString("version"));
            stub.setOwnerGroup(rs.getString("ownerGroup"));
            stub.setCreatedAt(rs.getString("createdAt"));
            return stub;
        });
    }

    public Stub getStub(String id) {
        List<Stub> stubs = jdbc.query("SELECT * FROM stubs WHERE id = ?", (rs, rowNum) -> {
            Stub stub = new Stub();
            stub.setId(rs.getString("id"));
            stub.setName(rs.getString("name"));
            stub.setMethod(rs.getString("method"));
            stub.setEndpoint(rs.getString("endpoint"));
            stub.setBaseUrl(rs.getString("baseUrl"));
            stub.setEnvironment(rs.getString("environment"));
            stub.setDescription(rs.getString("description"));
            stub.setRequestMatcher(rs.getString("requestMatcher"));
            stub.setResponseStatus(rs.getInt("responseStatus"));
            stub.setResponseBody(rs.getString("responseBody"));
            stub.setResponseHeaders(rs.getString("responseHeaders"));
            stub.setDelay(rs.getInt("delay"));
            stub.setEnabled(rs.getInt("enabled") == 1);
            stub.setCategory(rs.getString("category"));
            stub.setVersion(rs.getString("version"));
            stub.setOwnerGroup(rs.getString("ownerGroup"));
            stub.setCreatedAt(rs.getString("createdAt"));
            return stub;
        }, id);
        return stubs.isEmpty() ? null : stubs.get(0);
    }

    @Transactional
    public Stub createOrUpdateStub(Stub stub) {
        if (stub.getId() == null || stub.getId().isEmpty()) {
            stub.setId(UUID.randomUUID().toString());
            List<String> columns = new ArrayList<>(List.of(
                    "id", "name", "method", "endpoint", "baseUrl", "environment", "description",
                    "requestMatcher", "responseStatus", "responseBody", "responseHeaders", "delay",
                    "enabled", "category", "version", "ownerGroup"));
            List<Object> values = new ArrayList<>(Arrays.asList(
                    stub.getId(), stub.getName(), stub.getMethod(), stub.getEndpoint(), stub.getBaseUrl(),
                    stub.getEnvironment() == null ? "Dev" : stub.getEnvironment(), stub.getDescription(),
                    stub.getRequestMatcher(), stub.getResponseStatus(), stub.getResponseBody(),
                    stub.getResponseHeaders(), stub.getDelay(), stub.isEnabled() ? 1 : 0,
                    stub.getCategory(), stub.getVersion() == null ? "v1" : stub.getVersion(),
                    stub.getOwnerGroup()));
            if (hasLegacyUrlPatternColumn()) {
                columns.add("urlPattern");
                values.add(stub.getEndpoint());
            }
            String placeholders = String.join(",", java.util.Collections.nCopies(columns.size(), "?"));
            jdbc.update("INSERT INTO stubs (" + String.join(",", columns) + ") VALUES (" + placeholders + ")",
                    values.toArray());
            
            StubVersion v1 = new StubVersion();
            v1.setStubId(stub.getId());
            v1.setVersion("v1");
            v1.setVersionTag("Initial version");
            v1.setResponseStatus(stub.getResponseStatus());
            v1.setResponseBody(stub.getResponseBody());
            v1.setResponseHeaders(stub.getResponseHeaders());
            v1.setActive(true);
            createVersion(stub.getId(), v1);
        } else {
            jdbc.update("UPDATE stubs SET name=?, method=?, endpoint=?, baseUrl=?, environment=?, description=?, requestMatcher=?, responseStatus=?, responseBody=?, responseHeaders=?, delay=?, enabled=?, category=?, version=? WHERE id=?",
                    stub.getName(), stub.getMethod(), stub.getEndpoint(), stub.getBaseUrl(), stub.getEnvironment() == null ? "Dev" : stub.getEnvironment(), stub.getDescription(), stub.getRequestMatcher(), stub.getResponseStatus(), stub.getResponseBody(), stub.getResponseHeaders(), stub.getDelay(), stub.isEnabled() ? 1 : 0, stub.getCategory(), stub.getVersion() == null ? "v1" : stub.getVersion(), stub.getId());
        }
        return getStub(stub.getId());
    }

    private boolean hasLegacyUrlPatternColumn() {
        return jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            try (ResultSet columns = connection.getMetaData()
                    .getColumns(connection.getCatalog(), null, "stubs", null)) {
                while (columns.next()) {
                    if ("urlpattern".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    public void deleteStub(String id) {
        jdbc.update("DELETE FROM stub_versions WHERE stubId = ?", id);
        jdbc.update("DELETE FROM stubs WHERE id = ?", id);
    }

    public void clearAllStubs() {
        jdbc.update("DELETE FROM stub_versions");
        jdbc.update("DELETE FROM stubs");
    }

    public Stub toggleStub(String id) {
        jdbc.update("UPDATE stubs SET enabled = CASE WHEN enabled = 1 THEN 0 ELSE 1 END WHERE id = ?", id);
        return getStub(id);
    }

    // Version management
    public List<StubVersion> getVersions(String stubId) {
        return jdbc.query("SELECT * FROM stub_versions WHERE stubId = ? ORDER BY version ASC", (rs, rowNum) -> {
            StubVersion v = new StubVersion();
            v.setVersionId(rs.getString("versionId"));
            v.setStubId(rs.getString("stubId"));
            v.setVersion(rs.getString("version"));
            v.setVersionTag(rs.getString("versionTag"));
            v.setResponseStatus(rs.getInt("responseStatus"));
            v.setResponseBody(rs.getString("responseBody"));
            v.setResponseHeaders(rs.getString("responseHeaders"));
            v.setActive(rs.getInt("isActive") == 1);
            v.setCreatedAt(rs.getString("createdAt"));
            return v;
        }, stubId);
    }

    public StubVersion getVersion(String versionId) {
        List<StubVersion> versions = jdbc.query("SELECT * FROM stub_versions WHERE versionId = ?", (rs, rowNum) -> {
            StubVersion v = new StubVersion();
            v.setVersionId(rs.getString("versionId"));
            v.setStubId(rs.getString("stubId"));
            v.setVersion(rs.getString("version"));
            v.setVersionTag(rs.getString("versionTag"));
            v.setResponseStatus(rs.getInt("responseStatus"));
            v.setResponseBody(rs.getString("responseBody"));
            v.setResponseHeaders(rs.getString("responseHeaders"));
            v.setActive(rs.getInt("isActive") == 1);
            v.setCreatedAt(rs.getString("createdAt"));
            return v;
        }, versionId);
        return versions.isEmpty() ? null : versions.get(0);
    }

    public StubVersion createVersion(String stubId, StubVersion version) {
        version.setVersionId(UUID.randomUUID().toString());
        version.setStubId(stubId);
        List<StubVersion> existingVersions = getVersions(stubId);

        if (version.getVersion() == null || version.getVersion().isEmpty()) {
            int maxV = 0;
            for (StubVersion ev : existingVersions) {
                if (ev.getVersion() != null && ev.getVersion().startsWith("v")) {
                    try {
                        int num = Integer.parseInt(ev.getVersion().substring(1));
                        if (num > maxV) maxV = num;
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }
            version.setVersion("v" + (maxV + 1));
        }

        boolean exists = existingVersions.stream().anyMatch(v -> 
            v.getVersion().equals(version.getVersion()) && 
            v.getResponseStatus() == version.getResponseStatus()
        );
        if (exists) {
            throw new IllegalArgumentException("Version with the same version name and status code already exists");
        }

        jdbc.update("INSERT INTO stub_versions (versionId, stubId, version, versionTag, responseStatus, responseBody, responseHeaders, isActive) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                version.getVersionId(), version.getStubId(), version.getVersion(), version.getVersionTag(), version.getResponseStatus(), version.getResponseBody(), version.getResponseHeaders(), version.isActive() ? 1 : 0);
        return getVersion(version.getVersionId());
    }

    public void updateVersion(String versionId, StubVersion version) {
        StubVersion current = getVersion(versionId);
        if (current == null) return;
        
        if (current.getResponseStatus() != version.getResponseStatus()) {
            List<StubVersion> existingVersions = getVersions(current.getStubId());
            boolean exists = existingVersions.stream().anyMatch(v -> 
                !v.getVersionId().equals(versionId) &&
                java.util.Objects.equals(v.getVersion(), current.getVersion()) && 
                v.getResponseStatus() == version.getResponseStatus()
            );
            if (exists) {
                throw new IllegalArgumentException("Version with the same version name and status code already exists");
            }
        }

        jdbc.update("UPDATE stub_versions SET versionTag=?, responseStatus=?, responseBody=?, responseHeaders=? WHERE versionId=?",
                version.getVersionTag(), version.getResponseStatus(), version.getResponseBody(), version.getResponseHeaders(), versionId);

        if (current.isActive()) {
            jdbc.update("UPDATE stubs SET responseStatus = ?, responseBody = ?, responseHeaders = ? WHERE id = ?",
                    version.getResponseStatus(), version.getResponseBody(), version.getResponseHeaders(), current.getStubId());
        }
    }

    public void deleteVersion(String versionId) {
        jdbc.update("DELETE FROM stub_versions WHERE versionId = ?", versionId);
    }

    public void activateVersion(String stubId, String versionId) {
        jdbc.update("UPDATE stub_versions SET isActive = 0 WHERE stubId = ?", stubId);
        jdbc.update("UPDATE stub_versions SET isActive = 1 WHERE versionId = ?", versionId);

        StubVersion active = getVersion(versionId);
        if (active != null) {
            jdbc.update("UPDATE stubs SET responseStatus = ?, responseBody = ?, responseHeaders = ? WHERE id = ?",
                    active.getResponseStatus(), active.getResponseBody(), active.getResponseHeaders(), stubId);
        }
    }
}

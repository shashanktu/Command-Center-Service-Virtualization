package com.example.virtualization.controller;

import com.example.virtualization.model.Stub;
import com.example.virtualization.model.User;
import com.example.virtualization.service.AwsStorageService;
import com.example.virtualization.service.HttpForwardService;
import com.example.virtualization.service.JwtService;
import com.example.virtualization.stub.service.StubMatchingService;
import com.example.virtualization.stub.service.StubService;
import com.example.virtualization.util.SecurityHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*", exposedHeaders = {"X-Response-Source", "X-Recorded-Id", "X-No-Stub-Found"})
public class ProxyController {

    private final HttpForwardService httpForwardService;
    private final StubMatchingService stubMatchingService;
    private final StubService stubService;
    private final JwtService jwtService;
    private final JdbcTemplate jdbc;
    private final AwsStorageService awsStorageService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProxyController.class);

    public ProxyController(HttpForwardService httpForwardService, StubMatchingService stubMatchingService, StubService stubService, JwtService jwtService, JdbcTemplate jdbc, AwsStorageService awsStorageService) {
        this.httpForwardService = httpForwardService;
        this.stubMatchingService = stubMatchingService;
        this.stubService = stubService;
        this.jwtService = jwtService;
        this.jdbc = jdbc;
        this.awsStorageService = awsStorageService;
    }

    @RequestMapping(value = {"/proxy-external", "/proxy-request"}, method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<?> handleProxy(
            HttpServletRequest request,
            @RequestHeader HttpHeaders headers,
            @RequestBody(required = false) String body) {

        String authHeader = request.getHeader("Authorization");
        User user = SecurityHelper.extractUser(authHeader, jwtService);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {

        String globalUseToggle = "true";
        String globalRecordingMode = "true";
        try {
            java.util.List<Map<String, Object>> settings = jdbc.queryForList("SELECT key, value FROM settings WHERE key IN ('useToggle', 'recordingMode')");
            for (Map<String, Object> row : settings) {
                String k = (String) row.get("key");
                String v = (String) row.get("value");
                if ("useToggle".equals(k)) globalUseToggle = v;
                if ("recordingMode".equals(k)) globalRecordingMode = v;
            }
        } catch (Exception e) {
            System.err.println("Failed to read settings from DB: " + e.getMessage());
        }

        String toggleHeader = SecurityHelper.firstNonBlank(request.getHeader("X-Use-Toggle"), request.getHeader("X-Mock-Toggle"), globalUseToggle);
        boolean useMock = "off".equalsIgnoreCase(SecurityHelper.resolveToggleValue(toggleHeader));
        boolean autoRecord = "on".equalsIgnoreCase(SecurityHelper.resolveToggleValue(globalRecordingMode));

        System.out.println("[PROXY] Toggle DB value='" + globalUseToggle + "', resolved header='" + toggleHeader + "', useMock=" + useMock + ", autoRecord=" + autoRecord);

        String targetHost = request.getHeader("X-Target-Host");
        String path = request.getParameter("endpoint");
        if (path == null) path = "/";

        String url = request.getRequestURI().endsWith("/proxy-external") ? request.getParameter("url") : (targetHost + path);
        
        if (url == null || url.isEmpty()) {
            return ResponseEntity.badRequest().body("{\"error\": \"URL or Target Host is required\"}");
        }

        try {
            URI uri = new URI(url);
            InetAddress address = InetAddress.getByName(uri.getHost());
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                if (!useMock) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"SSRF protection: Internal addresses are not allowed\"}");
                }
            }
        } catch (Exception e) {
            if (!useMock) {
                return ResponseEntity.badRequest().body("{\"error\": \"Invalid URL\"}");
            }
        }

        Map<String, String> requestHeaders = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            requestHeaders.put(headerName, request.getHeader(headerName));
        }

        boolean explicitNoRealApi = "false".equalsIgnoreCase(request.getParameter("allowRealApi"));
        boolean forceRealApi = "true".equalsIgnoreCase(request.getParameter("allowRealApi"));

        System.out.println("[PROXY] Decision: useMock=" + useMock + ", forceRealApi=" + forceRealApi + ", explicitNoRealApi=" + explicitNoRealApi + ", url=" + url);

        // ===== TOGGLE IS THE FINAL SOURCE OF TRUTH =====
        // Toggle OFF (useMock=true): ONLY return stubs. NEVER hit live API unless user explicitly forced it.
        // Toggle ON  (useMock=false): ALWAYS hit live API.

        if (useMock) {
            // MOCK MODE: Try to find a stub
            Stub stub = stubMatchingService.findMatchingStub(request.getMethod(), url, body, targetHost);
            System.out.println("[PROXY] Mock mode - stub found: " + (stub != null ? stub.getName() : "NONE"));

            if (stub != null) {
                // Stub found — return it
                if (stub.getDelay() > 0) {
                    try { Thread.sleep(stub.getDelay()); } catch (InterruptedException ignored) {}
                }
                String reqId = UUID.randomUUID().toString();
                saveRecordedRequest(reqId, request.getMethod(), url, targetHost, path, requestHeaders.toString(), body, stub.getResponseStatus(), stub.getResponseBody(), stub.getResponseHeaders() != null ? stub.getResponseHeaders() : "", 1, stub.getCategory() != null ? stub.getCategory() : "other", user.getAdGroup(), "stub");

                int stubStatus = stub.getResponseStatus();
                if (stubStatus < 100 || stubStatus > 599) {
                    log.warn("Invalid response status {} for stub {} - falling back to 200", stubStatus, stub.getId());
                    stubStatus = 200;
                }
                ResponseEntity.BodyBuilder builder;
                try {
                    builder = ResponseEntity.status(stubStatus)
                            .header("X-Response-Source", "stub")
                            .header("X-Recorded-Id", reqId);
                } catch (IllegalArgumentException e) {
                    log.error("Invalid status for stub {}: {}", stub.getId(), e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("{\"error\": \"Invalid response status configured for stub\"}");
                }

                if (stub.getResponseHeaders() != null && !stub.getResponseHeaders().isEmpty() && !stub.getResponseHeaders().equals("{}")) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        Map<String, String> hdrs = mapper.readValue(stub.getResponseHeaders(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>(){});
                        for (Map.Entry<String, String> entry : hdrs.entrySet()) {
                            String key = entry.getKey();
                            String val = entry.getValue();
                            if (key == null || val == null) continue;
                            if (key.toLowerCase().startsWith("access-control-")
                                    || key.equalsIgnoreCase("content-length")
                                    || key.equalsIgnoreCase("transfer-encoding")
                                    || key.equalsIgnoreCase("connection")
                                    || key.equalsIgnoreCase("content-encoding")) {
                                continue;
                            }
                            builder.header(key, val);
                        }
                    } catch (Exception e) {}
                }

                return builder.body(stub.getResponseBody());
            }

            // No stub found in mock mode
            if (forceRealApi) {
                // User explicitly clicked "Yes, hit real-time API" in the popup
                System.out.println("[PROXY] Mock mode but user forced real API — allowing live call");
                // Fall through to live API call below
            } else {
                // HARD BLOCK — never hit live API
                System.out.println("[PROXY] Mock mode — NO stub found, BLOCKING live API call");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .header("X-No-Stub-Found", "true")
                        .body("{\"error\": \"Toggle is OFF (Mock mode). No stub found for this endpoint.\"}");
            }
        }

        // ===== LIVE API CALL =====
        // We only reach here if: toggle is ON, OR user explicitly forced real API
        System.out.println("[PROXY] Hitting LIVE API: " + url);
        HttpForwardService.OutboundResponse res = httpForwardService.send(url, request.getMethod(), requestHeaders, body, null, 15);

        // Record request
        String reqId = UUID.randomUUID().toString();
        saveRecordedRequest(reqId, request.getMethod(), url, targetHost, path, requestHeaders.toString(), body, res.statusCode, res.body, res.headers != null ? res.headers.toString() : "", 1, "other", user.getAdGroup(), "live-api");

        if (res.error != null) {
            if (useMock) {
                // Fallback to stub if upstream fails
                Stub stub = stubMatchingService.findMatchingStub(request.getMethod(), url, body, targetHost);
                if (stub != null) {
                    String fallbackReqId = UUID.randomUUID().toString();
                    saveRecordedRequest(fallbackReqId, request.getMethod(), url, targetHost, path, requestHeaders.toString(), body, stub.getResponseStatus(), stub.getResponseBody(), stub.getResponseHeaders() != null ? stub.getResponseHeaders() : "", 1, stub.getCategory() != null ? stub.getCategory() : "other", user.getAdGroup(), "stub (fallback)");

                    int stubStatus2 = stub.getResponseStatus();
                    if (stubStatus2 < 100 || stubStatus2 > 599) {
                        log.warn("Invalid response status {} for stub {} - falling back to 200", stubStatus2, stub.getId());
                        stubStatus2 = 200;
                    }
                    ResponseEntity.BodyBuilder builder;
                    try {
                        builder = ResponseEntity.status(stubStatus2)
                                .header("X-Response-Source", "stub (fallback)")
                                .header("X-Recorded-Id", fallbackReqId);
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid status for fallback stub {}: {}", stub.getId(), e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("{\"error\": \"Invalid response status configured for stub\"}");
                    }
                    if (stub.getResponseHeaders() != null && !stub.getResponseHeaders().isEmpty() && !stub.getResponseHeaders().equals("{}")) {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            Map<String, String> hdrs = mapper.readValue(stub.getResponseHeaders(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>(){});
                            for (Map.Entry<String, String> entry : hdrs.entrySet()) {
                                String key = entry.getKey();
                                String val = entry.getValue();
                                if (key == null || val == null) continue;
                                if (!key.toLowerCase().startsWith("access-control-") && !key.equalsIgnoreCase("content-length") && !key.equalsIgnoreCase("transfer-encoding") && !key.equalsIgnoreCase("connection") && !key.equalsIgnoreCase("content-encoding")) {
                                    val = val.replaceAll("[\\r\\n]+", " ");
                                    if (key.matches("^[a-zA-Z0-9!#$%&'*+.^_`|~-]+$")) {
                                        builder.header(key, val);
                                    }
                                }
                            }
                        } catch (Exception e) {}
                    }
                    return builder.body(stub.getResponseBody());
                }
            }
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("{\"error\": \"" + res.error + "\"}");
        }

        try {
            // Auto-create stub or version for all status codes
            if (autoRecord) {
                Stub existingStub = stubMatchingService.findMatchingStub(request.getMethod(), url, body, targetHost, true);
                if (existingStub != null) {
                    // Check if this exact status code and body already exists in versions or the active stub
                    boolean matchesActive = existingStub.getResponseStatus() == res.statusCode &&
                                            java.util.Objects.equals(existingStub.getResponseBody(), res.body);

                    java.util.List<com.example.virtualization.model.StubVersion> existingVersions = stubService.getVersions(existingStub.getId());
                    boolean matchesVersion = existingVersions.stream().anyMatch(v -> 
                        v.getResponseStatus() == res.statusCode && 
                        java.util.Objects.equals(v.getResponseBody(), res.body)
                    );
                    
                    System.out.println("[DEBUG-PROXY] URL=" + url);
                    System.out.println("[DEBUG-PROXY] res.statusCode=" + res.statusCode + ", existingStub.getResponseStatus()=" + existingStub.getResponseStatus());
                    System.out.println("[DEBUG-PROXY] res.body length=" + (res.body != null ? res.body.length() : 0) + ", existingStub.getResponseBody() length=" + (existingStub.getResponseBody() != null ? existingStub.getResponseBody().length() : 0));
                    System.out.println("[DEBUG-PROXY] matchesActive=" + matchesActive + ", matchesVersion=" + matchesVersion);
                    
                    if (!matchesActive && !matchesVersion) {
                        System.out.println("[DEBUG-PROXY] Creating new version for status: " + res.statusCode);
                        // Create a new version for the existing stub to capture this unique response
                        com.example.virtualization.model.StubVersion newVersion = new com.example.virtualization.model.StubVersion();
                        newVersion.setVersionTag("Auto-recorded: " + res.statusCode);
                        newVersion.setResponseStatus(res.statusCode);
                        newVersion.setResponseBody(res.body);
                        newVersion.setResponseHeaders(res.headers != null ? res.headers.toString() : "");
                        newVersion.setActive(false);
                        stubService.createVersion(existingStub.getId(), newVersion);
                    }

                    // Automatically update the main stub's active response to the latest live API response
                    existingStub.setResponseStatus(res.statusCode);
                    existingStub.setResponseBody(res.body);
                    existingStub.setResponseHeaders(res.headers != null ? res.headers.toString() : "");
                    existingStub.setRequestMatcher(body); // Update the latest request body
                    stubService.createOrUpdateStub(existingStub);
                } else {
                    Stub stub = new Stub();
                    stub.setName("Auto-recorded: " + path);
                    stub.setMethod(request.getMethod());
                    stub.setEndpoint(path);
                    stub.setEnvironment("Dev");
                    stub.setDescription("Auto-recorded from live traffic");
                    stub.setBaseUrl(targetHost);
                    stub.setRequestMatcher(body); // Save the request body for the API Tester
                    stub.setResponseStatus(res.statusCode);
                    stub.setResponseBody(res.body);
                    stub.setResponseHeaders(res.headers != null ? res.headers.toString() : "");
                    stub.setEnabled(true);
                    stub.setCategory("other");
                    stub.setVersion("v1");
                    stub.setOwnerGroup(user.getAdGroup());
                    Stub createdStub = stubService.createOrUpdateStub(stub);
                }
            }
        } catch (Exception e) {
            System.err.println("Auto-record failed: " + e.getMessage());
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(res.statusCode)
                .header("X-Response-Source", "live-api")
                .header("X-Recorded-Id", reqId);

        if (res.headers != null) {
            for (Map.Entry<String, String> entry : res.headers.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key == null || val == null) continue;
                if (!key.toLowerCase().startsWith("access-control-") && !key.equalsIgnoreCase("content-length") && !key.equalsIgnoreCase("transfer-encoding") && !key.equalsIgnoreCase("connection") && !key.equalsIgnoreCase("content-encoding")) {
                    try {
                        val = val.replaceAll("[\\r\\n]+", " ");
                        if (key.matches("^[a-zA-Z0-9!#$%&'*+.^_`|~-]+$")) {
                            builder.header(key, val);
                        }
                    } catch (Exception e) {}
                }
            }
        }
        
        return builder.body(res.body);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Backend Proxy Exception: " + e.getClass().getName() + " - " + e.getMessage() + "\"}");
        }
    }

    private void saveRecordedRequest(String reqId, String method, String url, String baseUrl, String endpoint, String headers, String body, int status, String response, String responseHeaders, int isRecorded, String category, String ownerGroup, String source) {
        String requestBody = body;
        String responseBody = response;
        String bodyS3Key = null;
        String responseS3Key = null;

        if (awsStorageService.isEnabled()) {
            if (requestBody != null) {
                bodyS3Key = awsStorageService.upload("requests/" + reqId + "/body.txt", requestBody);
                requestBody = null;
            }
            if (responseBody != null) {
                responseS3Key = awsStorageService.upload("requests/" + reqId + "/response.txt", responseBody);
                responseBody = null;
            }
        }

        jdbc.update("INSERT INTO requests (id, method, url, baseUrl, endpoint, headers, body, bodyS3Key, status, response, responseS3Key, responseHeaders, isRecorded, category, ownerGroup, source) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                reqId, method, url, baseUrl, endpoint, headers, requestBody, bodyS3Key, status, responseBody, responseS3Key, responseHeaders, isRecorded, category, ownerGroup, source);
    }
}

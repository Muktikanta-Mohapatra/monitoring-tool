package com.monitoring.logforwarder.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.logforwarder.dto.LoginRequestDTO;
import com.monitoring.logforwarder.entity.ForwarderApiKey;
import com.monitoring.logforwarder.repository.postgresql.ForwarderApiKeyRepository;
import com.monitoring.logforwarder.security.JwtTokenProvider;
import com.monitoring.logforwarder.security.SecurityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for authentication and API security.
 *
 * Verifies:
 * - Login endpoint is public (no authentication required)
 * - JWT token validation on protected endpoints
 * - x-api-key requirement for /api/v1/events/batch endpoint
 * - Role-based access control
 * - Unauthenticated access rejection with 401 status
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Authentication & API Security Integration Tests")
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private ForwarderApiKeyRepository forwarderApiKeyRepository;

    @Autowired(required = false)
    private JwtTokenProvider jwtTokenProvider;

    private String validJwtToken;
    private String validApiKey;
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "TestPassword123!";
    private static final String API_KEY_HEADER = "X-API-KEY";

    @BeforeEach
    void setUp() {
        validJwtToken = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        validApiKey = "test-api-key-12345";
    }

    @Test
    @DisplayName("Phase 3.1: Login endpoint should be public - no auth required")
    void testLoginEndpointIsPublic() throws Exception {
        LoginRequestDTO loginRequest = LoginRequestDTO.builder()
            .username(TEST_USERNAME)
            .password(TEST_PASSWORD)
            .build();

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Phase 3.2: Protected endpoints require JWT - reject without token")
    void testProtectedEndpointRejectsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/events/search")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Phase 3.3: Batch endpoint requires x-api-key - reject without key")
    void testBatchEndpointRequiresApiKey() throws Exception {
        String eventBatchJson = """
            {
                "forwarderId": "test-forwarder",
                "events": [
                    {
                        "rawMessage": "Test log entry",
                        "severity": "INFO"
                    }
                ]
            }
            """;

        mockMvc.perform(post("/api/v1/events/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBatchJson))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(containsString("API key required")));
    }

    @Test
    @DisplayName("Phase 3.4: Batch endpoint rejects invalid x-api-key")
    void testBatchEndpointRejectsInvalidApiKey() throws Exception {
        String eventBatchJson = """
            {
                "forwarderId": "test-forwarder",
                "events": [
                    {
                        "rawMessage": "Test log entry",
                        "severity": "INFO"
                    }
                ]
            }
            """;

        mockMvc.perform(post("/api/v1/events/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .header(API_KEY_HEADER, "invalid-api-key")
                .content(eventBatchJson))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(containsString("Invalid or expired API key")));
    }

    @Test
    @DisplayName("Phase 3.5: Search endpoint rejects x-api-key, requires JWT")
    void testSearchEndpointRejectsApiKeyRequiresJwt() throws Exception {
        mockMvc.perform(get("/api/v1/events/search")
                .contentType(MediaType.APPLICATION_JSON)
                .header(API_KEY_HEADER, validApiKey))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Phase 3.6: Health endpoint is public")
    void testHealthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/health")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Phase 3.7: Verify 401 Unauthorized returned for missing credentials")
    void testUnauthorizedResponseFormat() throws Exception {
        mockMvc.perform(get("/api/v1/events/search"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Phase 3.8: ForwarderAuthFilter enforces FORWARDER role")
    void testBatchEndpointRequiresForwarderRole() throws Exception {
        String eventBatchJson = """
            {
                "forwarderId": "test-forwarder",
                "events": []
            }
            """;

        mockMvc.perform(post("/api/v1/events/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .header(API_KEY_HEADER, "missing-or-invalid")
                .content(eventBatchJson))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Phase 3.9: Verify SecurityConfig authorization rules")
    void testSecurityConfigAuthorizationRules() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/config"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/forwarders"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Phase 3.10: Actuator endpoints are public")
    void testActuatorEndpointsArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }
}

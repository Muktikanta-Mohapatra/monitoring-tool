package com.monitoring.logforwarder.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.logforwarder.dto.ApiResponseDTO;
import com.monitoring.logforwarder.dto.LoginRequestDTO;
import com.monitoring.logforwarder.dto.LoginResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for JWT token flow.
 *
 * Verifies the complete authentication flow:
 * 1. User logs in successfully
 * 2. Receives a valid JWT token
 * 3. Uses token on subsequent requests
 * 4. Token validation works consistently
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("JWT Token Flow Integration Tests")
class JwtTokenFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_USERNAME = "admin";
    private static final String TEST_PASSWORD = "admin";

    @Test
    @DisplayName("Phase 4.1: Login returns valid JWT token")
    void testLoginReturnsValidToken() throws Exception {
        LoginRequestDTO loginRequest = LoginRequestDTO.builder()
            .username(TEST_USERNAME)
            .password(TEST_PASSWORD)
            .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").exists())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.expiresIn").exists())
            .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ApiResponseDTO response = objectMapper.readValue(responseBody, ApiResponseDTO.class);
        
        Object dataObj = response.getData();
        LoginResponseDTO loginResponse = objectMapper.convertValue(dataObj, LoginResponseDTO.class);
        
        assert loginResponse.getAccessToken() != null : "Access token should not be null";
        assert !loginResponse.getAccessToken().isEmpty() : "Access token should not be empty";
        assert loginResponse.getTokenType().equals("Bearer") : "Token type should be Bearer";
    }

    @Test
    @DisplayName("Phase 4.2: Subsequent request with valid token succeeds")
    void testSubsequentRequestWithValidTokenSucceeds() throws Exception {
        LoginRequestDTO loginRequest = LoginRequestDTO.builder()
            .username(TEST_USERNAME)
            .password(TEST_PASSWORD)
            .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        ApiResponseDTO response = objectMapper.readValue(responseBody, ApiResponseDTO.class);
        Object dataObj = response.getData();
        LoginResponseDTO loginResponse = objectMapper.convertValue(dataObj, LoginResponseDTO.class);
        String accessToken = loginResponse.getAccessToken();

        mockMvc.perform(get("/api/v1/forwarders")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Phase 4.3: Request without token fails with 401")
    void testRequestWithoutTokenFails() throws Exception {
        mockMvc.perform(get("/api/v1/forwarders")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Phase 4.4: Request with invalid token fails with 401")
    void testRequestWithInvalidTokenFails() throws Exception {
        mockMvc.perform(get("/api/v1/forwarders")
                .header("Authorization", "Bearer invalid.token.here")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Phase 4.5: Token is used consistently across multiple requests")
    void testTokenConsistencyAcrossRequests() throws Exception {
        LoginRequestDTO loginRequest = LoginRequestDTO.builder()
            .username(TEST_USERNAME)
            .password(TEST_PASSWORD)
            .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        ApiResponseDTO response = objectMapper.readValue(responseBody, ApiResponseDTO.class);
        Object dataObj = response.getData();
        LoginResponseDTO loginResponse = objectMapper.convertValue(dataObj, LoginResponseDTO.class);
        String accessToken = loginResponse.getAccessToken();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/forwarders")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Phase 4.6: Verify JWT secret consistency between generation and validation")
    void testJwtSecretConsistency() throws Exception {
        LoginRequestDTO loginRequest = LoginRequestDTO.builder()
            .username(TEST_USERNAME)
            .password(TEST_PASSWORD)
            .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        ApiResponseDTO response = objectMapper.readValue(responseBody, ApiResponseDTO.class);
        Object dataObj = response.getData();
        LoginResponseDTO loginResponse = objectMapper.convertValue(dataObj, LoginResponseDTO.class);
        String accessToken = loginResponse.getAccessToken();

        mockMvc.perform(get("/api/v1/forwarders")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    }
}

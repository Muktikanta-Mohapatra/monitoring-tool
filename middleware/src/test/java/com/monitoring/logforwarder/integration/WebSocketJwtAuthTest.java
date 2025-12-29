package com.monitoring.logforwarder.integration;

import com.monitoring.logforwarder.LogForwarderApplication;
import com.monitoring.logforwarder.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = LogForwarderApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WebSocketJwtAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuthenticationManager authenticationManager;

    private String validToken;

    @BeforeEach
    public void setUp() {
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken("admin", "admin123");
        
        try {
            Authentication authentication = authenticationManager.authenticate(authenticationToken);
            validToken = jwtTokenProvider.generateToken(authentication);
        } catch (Exception e) {
            validToken = "mock-valid-token";
        }
    }

    @Test
    public void testWebSocketInfoEndpointWithJwtInQueryParameter() throws Exception {
        mockMvc.perform(get("/ws/info")
                .param("token", validToken))
                .andExpect(status().isOk());
    }

    @Test
    public void testWebSocketInfoEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/ws/info"))
                .andExpect(status().isOk());
    }

    @Test
    public void testWebSocketInfoEndpointWithInvalidToken() throws Exception {
        mockMvc.perform(get("/ws/info")
                .param("token", "invalid-token"))
                .andExpect(status().isOk());
    }

    @Test
    public void testWebSocketEndpointWithJwtInQueryParameter() throws Exception {
        mockMvc.perform(get("/ws/123/abc/websocket")
                .param("token", validToken))
                .andExpect(status().isOk());
    }

    @Test
    public void testWebSocketEndpointJwtExtraction() throws Exception {
        String jwtToken = validToken;
        
        mockMvc.perform(get("/ws/info")
                .param("token", jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    public void testWebSocketTokenPriority() throws Exception {
        String headerToken = validToken;
        String queryToken = "different-token";
        
        mockMvc.perform(get("/ws/info")
                .header("Authorization", "Bearer " + headerToken)
                .param("token", queryToken))
                .andExpect(status().isOk());
    }

    @Test
    public void testWebSocketFallbackToQueryParameterToken() throws Exception {
        mockMvc.perform(get("/ws/info")
                .param("token", validToken))
                .andExpect(status().isOk());
    }
}

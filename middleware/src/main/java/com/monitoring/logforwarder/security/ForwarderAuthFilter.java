package com.monitoring.logforwarder.security;

import com.monitoring.logforwarder.entity.ForwarderApiKey;
import com.monitoring.logforwarder.repository.postgresql.ForwarderApiKeyRepository;
import com.monitoring.logforwarder.service.AuditService;
import com.monitoring.logforwarder.service.ForwarderService;
import com.monitoring.logforwarder.util.ClientInfoExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class ForwarderAuthFilter extends OncePerRequestFilter {

    @Autowired
    private ForwarderApiKeyRepository forwarderApiKeyRepository;

    @Autowired
    private ForwarderService forwarderService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ForwarderRateLimiter rateLimiter;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(SecurityConstants.BCRYPT_STRENGTH);

    private static final String API_KEY_HEADER = "X-API-KEY";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        String requestMethod = request.getMethod();
        
        boolean isBatchEndpoint = requestUri.equals("/api/v1/events/batch") && 
                                 requestMethod.equalsIgnoreCase("POST");
        
        try {
            if (isBatchEndpoint) {
                String apiKey = getApiKeyFromRequest(request);
                if (!StringUtils.hasText(apiKey)) {
                    String clientIp = ClientInfoExtractor.getClientIp(request);
                    log.warn("Missing API key for batch endpoint from IP: {}", clientIp);
                    auditService.logAction(null, "FORWARDER_UNKNOWN", "MISSING_API_KEY",
                        "FORWARDER", "UNKNOWN", "FAILED", clientIp, request.getHeader("User-Agent"));
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Unauthorized - API key required\"}");
                    return;
                }

                Optional<ForwarderApiKey> forwarderApiKeyOpt = findValidApiKey(apiKey);
                if (!forwarderApiKeyOpt.isPresent()) {
                    String clientIp = ClientInfoExtractor.getClientIp(request);
                    log.warn("Invalid API key attempt from IP: {}", clientIp);
                    auditService.logAction(null, "FORWARDER_UNKNOWN", "API_KEY_VALIDATION_FAILED",
                        "FORWARDER", "UNKNOWN", "FAILED", clientIp, request.getHeader("User-Agent"));
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Unauthorized - Invalid or expired API key\"}");
                    return;
                }

                ForwarderApiKey forwarderApiKey = forwarderApiKeyOpt.get();
                String forwarderId = forwarderApiKey.getForwarderId();

                if (!rateLimiter.isAllowed(apiKey)) {
                    String clientIp = ClientInfoExtractor.getClientIp(request);
                    log.warn("Rate limit exceeded for forwarder: {}", forwarderId);
                    auditService.logAction(null, forwarderId, "RATE_LIMIT_EXCEEDED",
                        "FORWARDER", forwarderId, "RATE_LIMIT_EXCEEDED", clientIp, 
                        request.getHeader("User-Agent"));
                    response.setStatus(429);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Rate limit exceeded\"}");
                    return;
                }

                Collection<GrantedAuthority> authorities = Collections.singletonList(
                    new SimpleGrantedAuthority("FORWARDER"));
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        forwarderId, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                forwarderService.updateApiKeyLastUsed(forwarderApiKey);

                response.addHeader("X-Rate-Limit-Remaining", String.valueOf(rateLimiter.getRemainingTokens(apiKey)));
                log.debug("Forwarder authenticated: {}", forwarderId);
            }
        } catch (Exception ex) {
            log.error("Error in ForwarderAuthFilter", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getApiKeyFromRequest(HttpServletRequest request) {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (StringUtils.hasText(apiKey)) {
            return apiKey.trim();
        }
        return null;
    }

    private Optional<ForwarderApiKey> findValidApiKey(String plainApiKey) {
        try {
            return forwarderApiKeyRepository.findAll().stream()
                .filter(key -> passwordEncoder.matches(plainApiKey, key.getApiKeyHash()))
                .filter(key -> key.getStatus().name().equals("ACTIVE"))
                .filter(key -> key.getExpiresAt() == null || key.getExpiresAt().isAfter(LocalDateTime.now()))
                .findFirst();
        } catch (Exception e) {
            log.error("Error validating API key", e);
            return Optional.empty();
        }
    }

}

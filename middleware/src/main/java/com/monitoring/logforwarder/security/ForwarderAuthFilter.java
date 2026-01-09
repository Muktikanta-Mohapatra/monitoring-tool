package com.monitoring.logforwarder.security;

import com.monitoring.logforwarder.entity.ForwarderApiKey;
import com.monitoring.logforwarder.repository.postgresql.ForwarderApiKeyRepository;
import com.monitoring.logforwarder.service.AuditService;
import com.monitoring.logforwarder.service.ForwarderService;
import com.monitoring.logforwarder.util.ClientInfoExtractor;
import com.monitoring.logforwarder.util.HttpErrorResponseWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Security filter for authenticating LogForwarder agents using API keys.
 *
 * <p><b>PURPOSE:</b></p>
 * This filter authenticates incoming HTTP requests from LogForwarder agents to the
 * {@code /api/v1/events/batch} endpoint. It validates the X-API-KEY header against
 * stored API keys and establishes a Spring Security authentication context.
 *
 * <p><b>ARCHITECTURE POSITION:</b></p>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────────────┐
 * │                    ForwarderAuthFilter - API KEY AUTHENTICATION                 │
 * ├─────────────────────────────────────────────────────────────────────────────────┤
 * │                                                                                 │
 * │  LogForwarder (Rust)                                                            │
 * │  ┌─────────────────────────────┐                                                │
 * │  │ HTTP POST /api/v1/events/   │                                                │
 * │  │ batch                       │                                                │
 * │  │ Headers:                    │                                                │
 * │  │   X-API-KEY: <api_key>      │                                                │
 * │  │   Content-Type: application/│                                                │
 * │  │     json                    │                                                │
 * │  └─────────────┬───────────────┘                                                │
 * │                │                                                                │
 * │                ▼                                                                │
 * │  ┌─────────────────────────────┐                                                │
 * │  │ ForwarderAuthFilter         │ ←── THIS CLASS                                 │
 * │  │ (this class)                │                                                │
 * │  └─────────────┬───────────────┘                                                │
 * │                │                                                                │
 * │       ┌────────┴────────┐                                                       │
 * │       ▼                 ▼                                                       │
 * │  [API Key Missing] [API Key Present]                                            │
 * │       │                 │                                                       │
 * │       ▼                 ▼                                                       │
 * │  401 Unauthorized  validateApiKey()                                             │
 * │                         │                                                       │
 * │           ┌─────────────┴──────────────┐                                        │
 * │           ▼                            ▼                                        │
 * │      [Invalid/Expired]            [Valid]                                       │
 * │           │                            │                                        │
 * │           ▼                            ▼                                        │
 * │      401 Unauthorized         Set SecurityContext                               │
 * │                                        │                                        │
 * │                                        ▼                                        │
 * │                               Check Rate Limit                                  │
 * │                                        │                                        │
 * │                         ┌──────────────┴──────────────┐                         │
 * │                         ▼                             ▼                         │
 * │                    [Exceeded]                    [Allowed]                      │
 * │                         │                             │                         │
 * │                         ▼                             ▼                         │
 * │                  429 Too Many               EventController                     │
 * │                    Requests                  .ingestEventBatch()                │
 * │                                                                                 │
 * └─────────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>AUTHENTICATION FLOW:</b></p>
 * <ol>
 *   <li>Extract X-API-KEY header from request</li>
 *   <li>If missing → Return 401 Unauthorized</li>
 *   <li>Lookup API key hash in PostgreSQL (ForwarderApiKey table)</li>
 *   <li>Validate using BCrypt password matching</li>
 *   <li>Check key status (ACTIVE) and expiration date</li>
 *   <li>If invalid → Return 401 with reason</li>
 *   <li>Check rate limit for this API key</li>
 *   <li>If exceeded → Return 429 Too Many Requests</li>
 *   <li>Set authentication in SecurityContext with FORWARDER authority</li>
 *   <li>Update last_used_at timestamp on API key</li>
 *   <li>Continue to EventController</li>
 * </ol>
 *
 * <p><b>APPLIES TO:</b></p>
 * Only {@code POST /api/v1/events/batch} endpoint. Other endpoints use JWT authentication
 * via {@link JwtAuthenticationFilter}.
 *
 * <p><b>API KEY SECURITY:</b></p>
 * <ul>
 *   <li>API keys are stored as BCrypt hashes (strength 12)</li>
 *   <li>Plain-text keys are never stored or logged</li>
 *   <li>Failed attempts are logged with masked key for debugging</li>
 *   <li>All attempts are audited via AuditService</li>
 * </ul>
 *
 * <p><b>RATE LIMITING:</b></p>
 * Uses token bucket algorithm via {@link ForwarderRateLimiter}. Each API key
 * gets a quota of requests per time window. X-Rate-Limit-Remaining header
 * is returned to help forwarders track their remaining quota.
 *
 * <p><b>CALLED BY:</b></p>
 * Spring Security filter chain - automatically invoked for all HTTP requests.
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see JwtAuthenticationFilter
 * @see ForwarderRateLimiter
 * @see ApiKeyValidationResult
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForwarderAuthFilter extends OncePerRequestFilter {

    private final ForwarderApiKeyRepository forwarderApiKeyRepository;

    private final ForwarderService forwarderService;

    private final AuditService auditService;

    private final ForwarderRateLimiter rateLimiter;

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
                    HttpErrorResponseWriter.writeUnauthorized(response, "API key required", "MISSING_API_KEY");
                    return;
                }

                ApiKeyValidationResult validationResult = validateApiKey(apiKey);
                if (!validationResult.isValid()) {
                    String clientIp = ClientInfoExtractor.getClientIp(request);
                    String maskedKey = maskApiKey(apiKey);
                    log.warn("Invalid API key attempt from IP: {}, reason: {}, key: {}", 
                        clientIp, validationResult.getReason(), maskedKey);
                    auditService.logActionWithChanges(null, "FORWARDER_UNKNOWN", "API_KEY_VALIDATION_FAILED",
                        "FORWARDER", "UNKNOWN", null, validationResult.getReason(), 
                        clientIp, request.getHeader("User-Agent"));
                    HttpErrorResponseWriter.writeUnauthorized(response, validationResult.getReason(), "API_KEY_INVALID");
                    return;
                }
                Optional<ForwarderApiKey> forwarderApiKeyOpt = Optional.of(validationResult.getApiKey());

                ForwarderApiKey forwarderApiKey = forwarderApiKeyOpt.get();
                String forwarderId = forwarderApiKey.getForwarderId();

                if (!rateLimiter.isAllowed(apiKey)) {
                    String clientIp = ClientInfoExtractor.getClientIp(request);
                    log.warn("Rate limit exceeded for forwarder: {}", forwarderId);
                    auditService.logAction(null, forwarderId, "RATE_LIMIT_EXCEEDED",
                        "FORWARDER", forwarderId, "RATE_LIMIT_EXCEEDED", clientIp, 
                        request.getHeader("User-Agent"));
                    HttpErrorResponseWriter.writeTooManyRequests(response, "Rate limit exceeded");
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

    private ApiKeyValidationResult validateApiKey(String plainApiKey) {
        try {
            List<ForwarderApiKey> allKeys = forwarderApiKeyRepository.findAll();
            
            for (ForwarderApiKey key : allKeys) {
                if (passwordEncoder.matches(plainApiKey, key.getApiKeyHash())) {
                    if (!key.getStatus().name().equals("ACTIVE")) {
                        return ApiKeyValidationResult.invalid("API key is " + key.getStatus().name().toLowerCase());
                    }
                    if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now())) {
                        return ApiKeyValidationResult.invalid("API key has expired");
                    }
                    return ApiKeyValidationResult.valid(key);
                }
            }
            
            return ApiKeyValidationResult.invalid("API key not found in database");
        } catch (Exception e) {
            log.error("Error validating API key", e);
            return ApiKeyValidationResult.invalid("Internal error during validation");
        }
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }

}

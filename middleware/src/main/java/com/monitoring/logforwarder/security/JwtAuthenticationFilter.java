package com.monitoring.logforwarder.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * JWT authentication filter for request processing.
 *
 * <p><b>Purpose:</b> Intercepts incoming requests to extract and validate JWT tokens
 * from the Authorization header, establishing the security context for authenticated users.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Extends OncePerRequestFilter for single execution per request</li>
 *   <li>Extracts Bearer token from Authorization header</li>
 *   <li>Sets SecurityContext for valid tokens</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        String requestMethod = request.getMethod();

        boolean isBatchEndpoint = requestUri.equals("/api/v1/events/batch") &&
                requestMethod.equalsIgnoreCase("POST");
        if (!isBatchEndpoint) {
            String jwt = null;
            try {
                jwt = getJwtFromRequest(request);

                if (StringUtils.hasText(jwt)) {
                    log.debug("Validating JWT token");
                    if (tokenProvider.validateToken(jwt)) {
                        log.debug("JWT token validation successful");
                        Long userId = null;
                        try {
                            userId = tokenProvider.getUserIdFromToken(jwt);
                            log.debug("User ID from JWT: {}", userId);
                        } catch (Exception ex) {
                            log.error("CRITICAL: Failed to extract user ID from JWT token. Token may be malformed. Exception: {}", ex.getMessage(), ex);
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\": \"Invalid token: cannot extract user ID\"}");
                            return;
                        }

                        if (userId != null) {
                            try {
                                UserDetails userDetails = customUserDetailsService.loadUserById(userId);
                                if (userDetails != null && userDetails.isEnabled()) {
                                    UsernamePasswordAuthenticationToken authentication =
                                            new UsernamePasswordAuthenticationToken(
                                                    userDetails, null, userDetails.getAuthorities());
                                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                                    SecurityContextHolder.getContext().setAuthentication(authentication);
                                    log.info("JWT authentication set for user: {} (ID: {})", userDetails.getUsername(), userId);
                                } else {
                                    log.error("CRITICAL: User not found or disabled for ID: {}", userId);
                                    response.setStatus(401);
                                    response.setContentType("application/json");
                                    response.getWriter().write("{\"error\": \"User not found or disabled\"}");
                                    return;
                                }
                            } catch (Exception ex) {
                                log.error("CRITICAL: Exception loading user details from database for user ID: {}. Exception: {}", userId, ex.getMessage(), ex);
                                response.setStatus(401);
                                response.setContentType("application/json");
                                response.getWriter().write("{\"error\": \"Failed to load user details\"}");
                                return;
                            }
                        } else {
                            log.error("CRITICAL: User ID is null from JWT token");
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\": \"Invalid token: user ID is null\"}");
                            return;
                        }
                    } else {
                        log.warn("JWT token validation failed");
                    }
                } else {
                    log.debug("No JWT token found in request (header or query parameter)");
                }
            } catch (Exception ex) {
                log.error("CRITICAL: Unexpected error processing JWT authentication. Exception: {}", ex.getMessage(), ex);
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Authentication processing error\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(SecurityConstants.HEADER_STRING);
        log.debug("Authorization header: {}", bearerToken);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            String token = bearerToken.substring(SecurityConstants.TOKEN_PREFIX.length());
            log.debug("JWT token extracted successfully from Authorization header");
            return token;
        }
        if (StringUtils.hasText(bearerToken)) {
            log.warn("Authorization header exists but doesn't start with 'Bearer ': {}", bearerToken.substring(0, Math.min(20, bearerToken.length())));
        }
        
        String token = request.getParameter("token");
        if (StringUtils.hasText(token)) {
            log.debug("JWT token extracted successfully from query parameter");
            return token;
        }
        
        String authParam = request.getParameter("Authorization");
        if (StringUtils.hasText(authParam)) {
            String decodedAuthParam = URLDecoder.decode(authParam, StandardCharsets.UTF_8);
            if (decodedAuthParam.startsWith(SecurityConstants.TOKEN_PREFIX)) {
                log.debug("JWT token extracted successfully from Authorization query parameter");
                return decodedAuthParam.substring(SecurityConstants.TOKEN_PREFIX.length());
            }
        }
        
        return null;
    }
}

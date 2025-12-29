package com.monitoring.logforwarder.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
public final class ClientInfoExtractor {

    private static final String UNKNOWN = "UNKNOWN";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String USER_AGENT = "User-Agent";

    private ClientInfoExtractor() {
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        String xForwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader(X_REAL_IP);
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp.trim();
        }

        String remoteAddr = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddr) ? remoteAddr : UNKNOWN;
    }

    public static String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        String userAgent = request.getHeader(USER_AGENT);
        return StringUtils.hasText(userAgent) ? userAgent : UNKNOWN;
    }

    public static String getClientIpFromContext() {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return getClientIp(attributes.getRequest());
            }
        } catch (Exception e) {
            log.debug("Could not retrieve client IP from context", e);
        }
        return UNKNOWN;
    }

    public static String getUserAgentFromContext() {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return getUserAgent(attributes.getRequest());
            }
        } catch (Exception e) {
            log.debug("Could not retrieve user agent from context", e);
        }
        return UNKNOWN;
    }
}

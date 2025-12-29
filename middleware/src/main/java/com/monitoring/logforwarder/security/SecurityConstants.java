package com.monitoring.logforwarder.security;

/**
 * Security-related constants and configuration values.
 *
 * <p><b>Purpose:</b> Centralizes security constants including JWT configuration,
 * header names, claim keys, and lockout settings.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
public class SecurityConstants {
    
    public static final String JWT_SECRET = "your-super-secret-jwt-key-change-this-in-production";
    public static final long JWT_EXPIRATION_MS = 86400000;
    public static final long JWT_REFRESH_EXPIRATION_MS = 604800000;
    
    public static final String HEADER_STRING = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    
    public static final String CLAIM_KEY_SUB = "sub";
    public static final String CLAIM_KEY_ID = "id";
    public static final String CLAIM_KEY_USERNAME = "username";
    public static final String CLAIM_KEY_ROLES = "roles";
    public static final String CLAIM_KEY_CREATED = "created";
    
    public static final String AUTHORITIES_KEY = "auth";
    
    public static final int BCRYPT_STRENGTH = 12;
    
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    public static final long LOCKOUT_DURATION_MS = 900000;
}

package com.monitoring.logforwarder.security;

import com.monitoring.logforwarder.entity.ForwarderApiKey;

/**
 * Result object for API key validation operations.
 *
 * <p><b>Purpose:</b> Encapsulates the outcome of API key validation including
 * success/failure status, failure reason, and the validated API key entity.</p>
 *
 * <p><b>Usage:</b></p>
 * <ul>
 *   <li>{@link #valid(ForwarderApiKey)} - Creates successful validation result</li>
 *   <li>{@link #invalid(String)} - Creates failed validation with reason</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see ForwarderAuthFilter
 */
public class ApiKeyValidationResult {
    private final boolean valid;
    private final String reason;
    private final ForwarderApiKey apiKey;

    private ApiKeyValidationResult(boolean valid, String reason, ForwarderApiKey apiKey) {
        this.valid = valid;
        this.reason = reason;
        this.apiKey = apiKey;
    }

    public static ApiKeyValidationResult valid(ForwarderApiKey apiKey) {
        return new ApiKeyValidationResult(true, null, apiKey);
    }

    public static ApiKeyValidationResult invalid(String reason) {
        return new ApiKeyValidationResult(false, reason, null);
    }

    public boolean isValid() {
        return valid;
    }

    public String getReason() {
        return reason;
    }

    public ForwarderApiKey getApiKey() {
        return apiKey;
    }
}

package com.monitoring.logforwarder.security;

import com.monitoring.logforwarder.entity.ForwarderApiKey;

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

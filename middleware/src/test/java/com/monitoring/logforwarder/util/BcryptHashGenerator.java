package com.monitoring.logforwarder.util;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptHashGenerator {

    @Test
    public void generateApiKeyHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String apiKey = "test-api-key-123";
        String hash = encoder.encode(apiKey);
        System.out.println("\n==================================================");
        System.out.println("API Key: " + apiKey);
        System.out.println("BCrypt Hash: " + hash);
        System.out.println("\nSQL to update:");
        System.out.println("UPDATE forwarder_api_keys SET api_key_hash = '" + hash + "' WHERE forwarder_id = 'YOUR_FORWARDER_ID';");
        System.out.println("==================================================\n");
    }
}

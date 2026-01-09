package com.monitoring.logforwarder.util;

import java.util.regex.Pattern;

/**
 * Utility class for input validation using regex patterns.
 *
 * <p><b>Purpose:</b> Provides validation methods for common input types including
 * email addresses, usernames, passwords, UUIDs, API keys, IP addresses, and hostnames.</p>
 *
 * <p><b>Key Methods:</b></p>
 * <ul>
 *   <li>{@link #isValidEmail} - Validate email format</li>
 *   <li>{@link #isValidUsername} - Validate username (3-20 chars, alphanumeric)</li>
 *   <li>{@link #isValidPassword} - Validate password strength</li>
 *   <li>{@link #isValidUUID} - Validate UUID format</li>
 *   <li>{@link #isValidApiKey} - Validate API key format</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
public class ValidationUtil {

    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    
    private static final Pattern USERNAME_PATTERN = 
        Pattern.compile("^[a-zA-Z0-9_-]{3,20}$");
    
    private static final Pattern PASSWORD_PATTERN = 
        Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$");
    
    private static final Pattern UUID_PATTERN = 
        Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern API_KEY_PATTERN = 
        Pattern.compile("^[A-Za-z0-9_-]{32,}$");

    private static final Pattern IPADDRESS_PATTERN =
        Pattern.compile("^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

    private static final Pattern HOSTNAME_PATTERN =
        Pattern.compile("^(?!-)(?:[a-zA-Z0-9-]{1,63}(?<!-)\\.)*[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$");

    public static boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isValidUsername(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        return USERNAME_PATTERN.matcher(username).matches();
    }

    public static boolean isValidPassword(String password) {
        if (password == null || password.isEmpty()) {
            return false;
        }
        return PASSWORD_PATTERN.matcher(password).matches();
    }

    public static boolean isValidUUID(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }
        return UUID_PATTERN.matcher(uuid).matches();
    }

    public static boolean isValidApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }
        return API_KEY_PATTERN.matcher(apiKey).matches();
    }

    public static boolean isValidIPAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return false;
        }
        return IPADDRESS_PATTERN.matcher(ipAddress).matches();
    }

    public static boolean isValidHostname(String hostname) {
        if (hostname == null || hostname.isEmpty()) {
            return false;
        }
        return HOSTNAME_PATTERN.matcher(hostname).matches();
    }

    public static boolean isValidNumber(String number) {
        if (number == null || number.isEmpty()) {
            return false;
        }
        try {
            Long.parseLong(number);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isValidInteger(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isValidLong(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isValidDouble(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean isValidLength(String value, int minLength, int maxLength) {
        if (value == null) {
            return false;
        }
        int length = value.length();
        return length >= minLength && length <= maxLength;
    }

    public static boolean isValidMinLength(String value, int minLength) {
        if (value == null) {
            return false;
        }
        return value.length() >= minLength;
    }

    public static boolean isValidMaxLength(String value, int maxLength) {
        if (value == null) {
            return false;
        }
        return value.length() <= maxLength;
    }

    public static boolean isPositive(long value) {
        return value > 0;
    }

    public static boolean isNonNegative(long value) {
        return value >= 0;
    }

    public static boolean isInRange(int value, int min, int max) {
        return value >= min && value <= max;
    }

    public static boolean isInRange(long value, long min, long max) {
        return value >= min && value <= max;
    }

    public static boolean isInRange(double value, double min, double max) {
        return value >= min && value <= max;
    }

    public static boolean containsOnlyAlphanumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.matches("^[a-zA-Z0-9]*$");
    }

    public static boolean containsOnlyLetters(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.matches("^[a-zA-Z]*$");
    }

    public static boolean containsOnlyDigits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.matches("^[0-9]*$");
    }

    public static String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("[<>\"'%;()&+]", "");
    }

    public static String escapeSQL(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("'", "''");
    }
}

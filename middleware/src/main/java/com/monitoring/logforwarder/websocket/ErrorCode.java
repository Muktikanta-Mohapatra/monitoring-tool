package com.monitoring.logforwarder.websocket;

public class ErrorCode {
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String QUERY_INVALID = "QUERY_INVALID";
    public static final String SUBSCRIPTION_LIMIT_EXCEEDED = "SUBSCRIPTION_LIMIT_EXCEEDED";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String SUBSCRIPTION_NOT_FOUND = "SUBSCRIPTION_NOT_FOUND";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String SQL_INJECTION_DETECTED = "SQL_INJECTION_DETECTED";
    public static final String INVALID_MESSAGE_FORMAT = "INVALID_MESSAGE_FORMAT";

    private ErrorCode() {}
}

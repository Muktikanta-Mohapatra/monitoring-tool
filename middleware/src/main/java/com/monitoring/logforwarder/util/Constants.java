package com.monitoring.logforwarder.util;

/**
 * Application-wide constants and configuration values.
 *
 * <p><b>Purpose:</b> Centralizes constant values used across the application
 * including API paths, pagination defaults, date formats, Elasticsearch indices,
 * Redis keys, and status values.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
public class Constants {

    public static final String API_VERSION = "v1";
    public static final String API_BASE_PATH = "/api/" + API_VERSION;

    public static final String PAGINATION_DEFAULT_PAGE = "0";
    public static final String PAGINATION_DEFAULT_SIZE = "20";
    public static final int PAGINATION_MAX_SIZE = 100;

    public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String TIME_FORMAT = "HH:mm:ss";

    public static final String ELASTICSEARCH_INDEX_EVENTS = "events";
    public static final String ELASTICSEARCH_INDEX_AUDIT = "audit-logs";
    public static final String ELASTICSEARCH_TYPE = "_doc";

    public static final String REDIS_KEY_PREFIX = "logforwarder:";
    public static final String REDIS_SEARCH_RESULTS_KEY = REDIS_KEY_PREFIX + "search:";
    public static final String REDIS_FORWARDER_METRICS_KEY = REDIS_KEY_PREFIX + "metrics:";
    public static final String REDIS_CACHE_TTL = "3600";

    public static final String LOG_LEVEL_DEBUG = "DEBUG";
    public static final String LOG_LEVEL_INFO = "INFO";
    public static final String LOG_LEVEL_WARN = "WARN";
    public static final String LOG_LEVEL_ERROR = "ERROR";

    public static final String EVENT_STATUS_RECEIVED = "RECEIVED";
    public static final String EVENT_STATUS_INDEXED = "INDEXED";
    public static final String EVENT_STATUS_FAILED = "FAILED";

    public static final String FORWARDER_STATUS_ACTIVE = "ACTIVE";
    public static final String FORWARDER_STATUS_INACTIVE = "INACTIVE";
    public static final String FORWARDER_STATUS_ERROR = "ERROR";
    public static final String FORWARDER_STATUS_DISCONNECTED = "DISCONNECTED";

    public static final String ALERT_STATUS_TRIGGERED = "TRIGGERED";
    public static final String ALERT_STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";
    public static final String ALERT_STATUS_RESOLVED = "RESOLVED";

    public static final String ALERT_SEVERITY_CRITICAL = "CRITICAL";
    public static final String ALERT_SEVERITY_HIGH = "HIGH";
    public static final String ALERT_SEVERITY_MEDIUM = "MEDIUM";
    public static final String ALERT_SEVERITY_LOW = "LOW";
    public static final String ALERT_SEVERITY_INFO = "INFO";

    public static final String USER_ROLE_ADMIN = "ADMIN";
    public static final String USER_ROLE_OPERATOR = "OPERATOR";
    public static final String USER_ROLE_VIEWER = "VIEWER";

    public static final int MAX_LOGIN_ATTEMPTS = 5;
    public static final int LOCKOUT_DURATION_MINUTES = 15;

    public static final String WEBSOCKET_ENDPOINT = "/ws";
    public static final String WEBSOCKET_TOPIC_EVENTS = "/topic/events";
    public static final String WEBSOCKET_TOPIC_ALERTS = "/topic/alerts";
    public static final String WEBSOCKET_TOPIC_METRICS = "/topic/metrics";
    public static final String WEBSOCKET_TOPIC_DASHBOARD = "/topic/dashboard";
    public static final String WEBSOCKET_QUEUE_NOTIFICATIONS = "/queue/notifications";

    public static final String QUERY_OPERATOR_AND = "AND";
    public static final String QUERY_OPERATOR_OR = "OR";
    public static final String QUERY_OPERATOR_NOT = "NOT";

    public static final int BATCH_SIZE = 100;
    public static final int METRICS_RETENTION_DAYS = 30;
    public static final int AUDIT_LOG_RETENTION_DAYS = 90;
    public static final int EVENT_RETENTION_DAYS = 365;

    public static final String HEALTH_CHECK_INTERVAL = "60";
    public static final String METRICS_AGGREGATION_INTERVAL = "300";

    public static final String NOTIFICATION_CHANNEL_EMAIL = "EMAIL";
    public static final String NOTIFICATION_CHANNEL_SLACK = "SLACK";
    public static final String NOTIFICATION_CHANNEL_WEBHOOK = "WEBHOOK";

    public static final String ERROR_CODE_VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String ERROR_CODE_RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String ERROR_CODE_AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";
    public static final String ERROR_CODE_AUTHORIZATION_FAILED = "AUTHORIZATION_FAILED";
    public static final String ERROR_CODE_INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String ERROR_CODE_FORWARDER_NOT_FOUND = "FORWARDER_NOT_FOUND";
    public static final String ERROR_CODE_EVENT_NOT_FOUND = "EVENT_NOT_FOUND";
    public static final String ERROR_CODE_ALERT_NOT_FOUND = "ALERT_NOT_FOUND";
    public static final String ERROR_CODE_INVALID_API_KEY = "INVALID_API_KEY";
    public static final String ERROR_CODE_DUPLICATE_RESOURCE = "DUPLICATE_RESOURCE";
}

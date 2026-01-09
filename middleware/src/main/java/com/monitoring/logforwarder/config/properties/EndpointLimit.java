package com.monitoring.logforwarder.config.properties;

import lombok.Data;

/**
 * Rate limit configuration for a specific API endpoint.
 *
 * <p><b>Purpose:</b> Defines per-minute and per-hour request limits
 * for individual endpoints, overriding global rate limit settings.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see RateLimitProperties
 */
@Data
public class EndpointLimit {
    private int perMinute;
    private int perHour;
}

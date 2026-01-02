package com.monitoring.logforwarder.config.properties;

import lombok.Data;

@Data
public class EndpointLimit {
    private int perMinute;
    private int perHour;
}

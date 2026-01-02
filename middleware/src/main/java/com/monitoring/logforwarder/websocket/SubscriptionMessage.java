package com.monitoring.logforwarder.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionMessage {
    private String type;
    private String version;
    private String requestId;
    private LocalDateTime timestamp;
    private String subscriptionId;
    private String action;
    private String query;
    private List<String> fields;
    private Integer batchSize;
    @JsonProperty("indexes")
    private List<String> indexes;
    private Map<String, Object> filters;
}

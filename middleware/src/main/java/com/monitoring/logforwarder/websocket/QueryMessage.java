package com.monitoring.logforwarder.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryMessage {
    private String type;
    private String version;
    private String requestId;
    private LocalDateTime timestamp;
    private String queryId;
    private String queryText;
    private String queryType;
    private Integer limit;
    private Integer offset;
    private Long executionTimeMs;
    private Long totalResults;
    private List<Map<String, Object>> results;
}

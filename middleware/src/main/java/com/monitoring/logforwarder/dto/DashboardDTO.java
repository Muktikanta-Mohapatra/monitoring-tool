package com.monitoring.logforwarder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for dashboard summary data.
 *
 * <p><b>Purpose:</b> Aggregates key metrics for dashboard display including
 * event counts, forwarder status, alerts, system health, and distributions.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDTO {
    private Long totalEvents;
    private Long totalEventsLast24h;
    private Long totalForwarders;
    private Long activeForwarders;
    private Long alertsTriggered;
    private Long alertsUnacknowledged;
    private Integer totalUsers;
    private Double systemCpuUsage;
    private Double systemMemoryUsage;
    private Integer indexedEvents;
    private Integer pendingIndexing;
    private List<String> recentSeverities;
    private List<String> recentSourcetypes;
    private Map<String, Long> eventsBySourcetype;
    private Map<String, Long> eventsBySeverity;
    private Map<String, Long> eventsByHour;
}

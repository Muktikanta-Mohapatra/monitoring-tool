package com.monitoring.logforwarder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Data Transfer Object for batched event ingestion.
 *
 * <p><b>Purpose:</b> Groups multiple events for efficient batch transmission
 * from forwarders, including batch metadata and checkpoint information.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventBatchDTO {
    private String forwarderId;
    private String apiKey;
    @Size(min = 1, max = 100000, message = "Batch must contain between 1 and 100000 events")
    private List<EventDTO> events;
    private String batchId;
    private Integer batchSize;
    private Long nextCheckpointOffset;
    private Integer nextCheckpointLineCount;
}

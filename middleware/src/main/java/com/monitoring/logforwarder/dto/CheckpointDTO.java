package com.monitoring.logforwarder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for forwarder checkpoint state.
 *
 * <p><b>Purpose:</b> Tracks log file reading progress including file offset,
 * line count, and rotation detection for reliable log forwarding.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckpointDTO {
    private Long id;
    private String forwarderId;
    private String sourceId;
    private String sourcePath;
    private Long fileOffset;
    private Long fileInode;
    private Long lineCount;
    private Long byteCount;
    private Boolean rotationDetected;
    private LocalDateTime lastReadTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

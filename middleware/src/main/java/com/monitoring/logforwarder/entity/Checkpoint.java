package com.monitoring.logforwarder.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * JPA entity representing a forwarder checkpoint.
 *
 * <p><b>Purpose:</b> Tracks log file reading progress for each forwarder and source,
 * enabling reliable resume after restart and log rotation detection.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Table: checkpoints</li>
 *   <li>Unique index on forwarder_id + source_id</li>
 *   <li>Tracks file offset, inode, and rotation status</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "checkpoints", indexes = {
    @Index(name = "idx_forwarder_id_source_id", columnList = "forwarder_id, source_id", unique = true),
    @Index(name = "idx_last_update", columnList = "last_update DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Checkpoint {

    @Id
    private Long id;

    @Column(name = "forwarder_id", nullable = false, length = 100)
    private String forwarderId;

    @Column(name = "source_id", nullable = false)
    private Integer sourceId;

    @Column(name = "source_name", length = 256)
    private String sourceName;

    @Column(name = "file_path", columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "file_offset")
    private Long fileOffset;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "inode")
    private Long inode;

    @Column(name = "last_update")
    private LocalDateTime lastUpdate;

    @Column(name = "line_count")
    private Long lineCount = 0L;

    @Column(name = "bytes_read")
    private Long bytesRead = 0L;

    @Column(name = "last_modified")
    private LocalDateTime lastModified;

    @Column(name = "rotation_detected")
    private Boolean rotationDetected = false;

    @Column(name = "rotation_timestamp")
    private LocalDateTime rotationTimestamp;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = generateId();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastUpdate == null) {
            lastUpdate = LocalDateTime.now();
        }
    }

    private Long generateId() {
        long timestamp = System.currentTimeMillis() << 20;
        long random = ThreadLocalRandom.current().nextLong(0, 1_048_576);
        return timestamp | random;
    }
}

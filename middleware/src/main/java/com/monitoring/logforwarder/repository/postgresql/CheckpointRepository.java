package com.monitoring.logforwarder.repository.postgresql;

import com.monitoring.logforwarder.entity.Checkpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for Checkpoint entity operations in PostgreSQL.
 *
 * <p><b>Purpose:</b> Stores forwarder file read positions for resumable log tailing
 * and file rotation detection.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface CheckpointRepository extends JpaRepository<Checkpoint, Long> {

    Optional<Checkpoint> findByForwarderIdAndSourceId(String forwarderId, Integer sourceId);

    List<Checkpoint> findByForwarderId(String forwarderId);

    List<Checkpoint> findBySourceId(Integer sourceId);

    @Query("SELECT c FROM Checkpoint c WHERE c.forwarderId = :forwarderId AND c.rotationDetected = true")
    List<Checkpoint> findRotationsByForwarderId(@Param("forwarderId") String forwarderId);

    @Query("SELECT c FROM Checkpoint c WHERE c.rotationDetected = true")
    List<Checkpoint> findAllRotations();

    @Query("SELECT SUM(c.bytesRead) FROM Checkpoint c WHERE c.forwarderId = :forwarderId")
    Long getTotalBytesReadByForwarder(@Param("forwarderId") String forwarderId);

    @Query("SELECT SUM(c.lineCount) FROM Checkpoint c WHERE c.forwarderId = :forwarderId")
    Long getTotalLinesProcessedByForwarder(@Param("forwarderId") String forwarderId);
}

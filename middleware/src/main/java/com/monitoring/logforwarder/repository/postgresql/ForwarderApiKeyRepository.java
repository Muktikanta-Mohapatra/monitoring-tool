package com.monitoring.logforwarder.repository.postgresql;

import com.monitoring.logforwarder.entity.ApiKeyStatus;
import com.monitoring.logforwarder.entity.ForwarderApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for ForwarderApiKey entity operations in PostgreSQL.
 *
 * <p><b>Purpose:</b> Manages API keys for LogForwarder authentication including
 * key lookup, status filtering, and expiration queries.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface ForwarderApiKeyRepository extends JpaRepository<ForwarderApiKey, Long> {

    Optional<ForwarderApiKey> findByApiKeyHash(String apiKeyHash);

    List<ForwarderApiKey> findByForwarderId(String forwarderId);

    List<ForwarderApiKey> findByForwarderIdAndStatus(String forwarderId, ApiKeyStatus status);

    @Query("SELECT f FROM ForwarderApiKey f WHERE f.forwarderId = :forwarderId AND f.status = 'ACTIVE'")
    List<ForwarderApiKey> findActiveKeysByForwarderId(@Param("forwarderId") String forwarderId);

    @Query("SELECT f FROM ForwarderApiKey f WHERE f.status = :status")
    List<ForwarderApiKey> findByStatus(@Param("status") ApiKeyStatus status);

    @Query("SELECT f FROM ForwarderApiKey f WHERE f.expiresAt < :now AND f.status = 'ACTIVE'")
    List<ForwarderApiKey> findExpiredKeys(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(f) FROM ForwarderApiKey f WHERE f.forwarderId = :forwarderId AND f.status = 'ACTIVE'")
    Long countActiveKeysByForwarderId(@Param("forwarderId") String forwarderId);
}

package com.monitoring.logforwarder.repository.postgresql;

import com.monitoring.logforwarder.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA repository for Notification entity operations in PostgreSQL.
 *
 * <p><b>Purpose:</b> Tracks notification delivery status for alerts across
 * different channels (email, Slack, webhook).</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByAlertId(Long alertId);

    List<Notification> findByStatus(String status);

    List<Notification> findByChannel(String channel);
}

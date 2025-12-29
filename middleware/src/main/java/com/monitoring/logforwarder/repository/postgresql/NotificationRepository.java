package com.monitoring.logforwarder.repository.postgresql;

import com.monitoring.logforwarder.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByAlertId(Long alertId);

    List<Notification> findByStatus(String status);

    List<Notification> findByChannel(String channel);
}

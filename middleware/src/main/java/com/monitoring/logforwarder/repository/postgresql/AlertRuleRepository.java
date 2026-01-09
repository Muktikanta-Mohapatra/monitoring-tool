package com.monitoring.logforwarder.repository.postgresql;

import com.monitoring.logforwarder.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for AlertRule entity operations in PostgreSQL.
 *
 * <p><b>Purpose:</b> Provides CRUD and query operations for alert rule definitions
 * used by AlertRuleEvaluator to match events.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findByEnabled(Boolean enabled);

    List<AlertRule> findByCreatedBy(Long userId);

    Optional<AlertRule> findByName(String name);
}

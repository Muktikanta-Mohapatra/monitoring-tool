package com.monitoring.logforwarder.repository.postgresql;

import com.monitoring.logforwarder.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findByEnabled(Boolean enabled);

    List<AlertRule> findByCreatedBy(Long userId);

    Optional<AlertRule> findByName(String name);
}

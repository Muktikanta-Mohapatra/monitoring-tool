package com.monitoring.logforwarder.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration class enabling scheduled task execution.
 *
 * <p><b>Purpose:</b> Enables Spring's scheduling infrastructure for all @Scheduled
 * methods in the application.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Configuration
@EnableScheduling
public class ScheduledTasksConfig {
}

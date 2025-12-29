package com.monitoring.logforwarder.service;

import com.monitoring.logforwarder.dto.AlertDTO;
import com.monitoring.logforwarder.dto.EventDTO;
import com.monitoring.logforwarder.entity.AlertRule;
import com.monitoring.logforwarder.repository.postgresql.AlertRuleRepository;
import com.monitoring.logforwarder.util.AsyncHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class AlertRuleEvaluator {

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompletableFuture<List<AlertDTO>> evaluateEventAgainstRules(EventDTO event) {
        return AsyncHelper.executeAsyncFuture(() -> {
            List<AlertDTO> triggeredAlerts = new ArrayList<>();

            try {
                List<AlertRule> enabledRules = alertRuleRepository.findByEnabled(true);

                for (AlertRule rule : enabledRules) {
                    if (evaluateRule(event, rule)) {
                        AlertDTO alert = createAlertFromRule(event, rule);
                        triggeredAlerts.add(alert);
                        log.info("Alert triggered for event {} by rule: {}", event.getId(), rule.getName());
                    }
                }
            } catch (Exception e) {
                log.error("Error evaluating event against rules: {}", e.getMessage(), e);
            }

            return triggeredAlerts;
        });
    }

    private boolean evaluateRule(EventDTO event, AlertRule rule) {
        try {
            String conditionType = rule.getConditionType();

            switch (conditionType.toLowerCase()) {
                case "severity":
                    return evaluateSeverityCondition(event, rule);
                case "keyword":
                    return evaluateKeywordCondition(event, rule);
                case "pattern":
                    return evaluatePatternCondition(event, rule);
                case "compound":
                    return evaluateCompoundCondition(event, rule);
                default:
                    log.warn("Unknown condition type: {}", conditionType);
                    return false;
            }
        } catch (Exception e) {
            log.error("Error evaluating rule {}: {}", rule.getId(), e.getMessage(), e);
            return false;
        }
    }

    private boolean evaluateSeverityCondition(EventDTO event, AlertRule rule) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> condition = objectMapper.readValue(rule.getConditionJson(), Map.class);
            String severity = (String) condition.get("severity");
            String operator = (String) condition.getOrDefault("operator", "equals");

            if (severity == null || event.getSeverity() == null) {
                return false;
            }

            return matchesCondition(event.getSeverity(), severity, operator);
        } catch (Exception e) {
            log.error("Error evaluating severity condition: {}", e.getMessage());
            return false;
        }
    }

    private boolean evaluateKeywordCondition(EventDTO event, AlertRule rule) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> condition = objectMapper.readValue(rule.getConditionJson(), Map.class);
            String keyword = (String) condition.get("keyword");

            if (keyword == null) {
                return false;
            }

            String rawMessage = event.getRawMessage() != null ? event.getRawMessage() : "";
            String rawData = event.getRawData() != null ? event.getRawData() : "";

            return rawMessage.toLowerCase().contains(keyword.toLowerCase()) ||
                   rawData.toLowerCase().contains(keyword.toLowerCase());
        } catch (Exception e) {
            log.error("Error evaluating keyword condition: {}", e.getMessage());
            return false;
        }
    }

    private boolean evaluatePatternCondition(EventDTO event, AlertRule rule) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> condition = objectMapper.readValue(rule.getConditionJson(), Map.class);
            String pattern = (String) condition.get("pattern");

            if (pattern == null) {
                return false;
            }

            String rawMessage = event.getRawMessage() != null ? event.getRawMessage() : "";
            String rawData = event.getRawData() != null ? event.getRawData() : "";

            try {
                return rawMessage.matches(pattern) || rawData.matches(pattern);
            } catch (Exception e) {
                log.warn("Invalid regex pattern: {}", pattern);
                return false;
            }
        } catch (Exception e) {
            log.error("Error evaluating pattern condition: {}", e.getMessage());
            return false;
        }
    }

    private boolean evaluateCompoundCondition(EventDTO event, AlertRule rule) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> condition = objectMapper.readValue(rule.getConditionJson(), Map.class);
            String operator = (String) condition.getOrDefault("operator", "AND");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> conditions = (List<Map<String, Object>>) condition.get("conditions");

            if (conditions == null || conditions.isEmpty()) {
                return false;
            }

            boolean result = "AND".equalsIgnoreCase(operator);

            for (Map<String, Object> subCondition : conditions) {
                boolean subResult = evaluateSubCondition(event, subCondition);

                if ("AND".equalsIgnoreCase(operator)) {
                    result = result && subResult;
                    if (!result) break;
                } else if ("OR".equalsIgnoreCase(operator)) {
                    result = result || subResult;
                    if (result) break;
                }
            }

            return result;
        } catch (Exception e) {
            log.error("Error evaluating compound condition: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateSubCondition(EventDTO event, Map<String, Object> condition) {
        String type = (String) condition.get("type");

        switch (type != null ? type.toLowerCase() : "") {
            case "severity":
                String severity = (String) condition.get("severity");
                String operator = (String) condition.getOrDefault("operator", "equals");
                return event.getSeverity() != null && matchesCondition(event.getSeverity(), severity, operator);

            case "keyword":
                String keyword = (String) condition.get("keyword");
                String rawMessage = event.getRawMessage() != null ? event.getRawMessage() : "";
                String rawData = event.getRawData() != null ? event.getRawData() : "";
                return rawMessage.toLowerCase().contains(keyword.toLowerCase()) ||
                       rawData.toLowerCase().contains(keyword.toLowerCase());

            case "source":
                String source = (String) condition.get("source");
                return event.getSourceName() != null && event.getSourceName().equalsIgnoreCase(source);

            default:
                return false;
        }
    }

    private boolean matchesCondition(String value, String expectedValue, String operator) {
        if (value == null || expectedValue == null) {
            return false;
        }

        return switch (operator.toLowerCase()) {
            case "equals" -> value.equalsIgnoreCase(expectedValue);
            case "contains" -> value.toLowerCase().contains(expectedValue.toLowerCase());
            case "startswith" -> value.toLowerCase().startsWith(expectedValue.toLowerCase());
            case "endswith" -> value.toLowerCase().endsWith(expectedValue.toLowerCase());
            case "regex" -> {
                try {
                    yield value.matches(expectedValue);
                } catch (Exception e) {
                    yield false;
                }
            }
            default -> false;
        };
    }

    private AlertDTO createAlertFromRule(EventDTO event, AlertRule rule) {
        return AlertDTO.builder()
                .alertRuleId(rule.getId())
                .status("TRIGGERED")
                .severity(rule.getSeverity())
                .triggeredAt(java.time.LocalDateTime.now())
                .triggerMessage("Alert triggered by event: " + (event.getId() != null ? event.getId() : "unknown"))
                .notificationChannel(extractNotificationChannel(rule.getNotificationChannels()))
                .build();
    }

    private String extractNotificationChannel(String notificationChannels) {
        if (notificationChannels == null || notificationChannels.isEmpty()) {
            return "email";
        }

        try {
            @SuppressWarnings("unchecked")
            List<String> channels = objectMapper.readValue(notificationChannels, List.class);
            return channels.isEmpty() ? "email" : channels.get(0);
        } catch (Exception e) {
            return notificationChannels.split(",")[0].trim();
        }
    }
}

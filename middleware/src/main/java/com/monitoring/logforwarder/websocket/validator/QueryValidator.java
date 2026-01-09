package com.monitoring.logforwarder.websocket.validator;

import com.monitoring.logforwarder.entity.User;
import com.monitoring.logforwarder.websocket.ErrorCode;
import com.monitoring.logforwarder.websocket.WebSocketMessageTypes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validator for search query syntax and security.
 *
 * <p><b>Purpose:</b> Validates search queries for proper syntax, index authorization,
 * and security threats including SQL injection detection.</p>
 *
 * <p><b>Validations:</b></p>
 * <ul>
 *   <li>Query length and clause count limits</li>
 *   <li>Allowed operators and logical operators</li>
 *   <li>SQL injection pattern detection</li>
 *   <li>Index access authorization</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Component
public class QueryValidator {

    private static final int MAX_QUERY_LENGTH = 10000;
    private static final int MAX_CLAUSES = 100;
    private static final Set<String> ALLOWED_OPERATORS = Set.of("=", "<", ">", "<=", ">=", "!=", ":", "~", "*");
    private static final Set<String> ALLOWED_LOGICAL_OPS = Set.of("AND", "OR", "NOT");

    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(" +
        "UNION|SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|TRUNCATE|" +
        "EXEC|EXECUTE|SCRIPT|JAVASCRIPT|ONERROR|ONLOAD|FETCH|XMLHttpRequest|" +
        "\\bOR\\b.*=.*|\\bAND\\b.*=.*|;\\s*--|/\\*|\\*/" +
        ")"
    );

    private static final Pattern VALID_FIELD_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Pattern VALID_INDEX_NAME = Pattern.compile("^[a-zA-Z0-9_\\-]*$");

    public QueryValidationResult validate(String query, User user) {
        return validate(query, user, null);
    }

    public QueryValidationResult validate(String query, User user, List<String> authorizedIndexes) {
        if (query == null || query.trim().isEmpty()) {
            return QueryValidationResult.error(
                ErrorCode.QUERY_INVALID,
                "Query cannot be empty"
            );
        }

        if (query.length() > MAX_QUERY_LENGTH) {
            return QueryValidationResult.error(
                ErrorCode.QUERY_INVALID,
                "Query exceeds maximum length of " + MAX_QUERY_LENGTH + " characters"
            );
        }

        QueryValidationResult syntaxResult = validateSyntax(query);
        if (!syntaxResult.isValid()) {
            return syntaxResult;
        }

        QueryValidationResult injectionResult = validateAgainstInjection(query);
        if (!injectionResult.isValid()) {
            return injectionResult;
        }

        if (authorizedIndexes != null && !authorizedIndexes.isEmpty()) {
            QueryValidationResult authResult = validateAuthorization(query, authorizedIndexes);
            if (!authResult.isValid()) {
                return authResult;
            }
        }

        return QueryValidationResult.valid();
    }

    private QueryValidationResult validateSyntax(String query) {
        List<String> errors = new ArrayList<>();

        String normalized = query.trim();
        if (normalized.isEmpty()) {
            errors.add("Query cannot be empty");
            return QueryValidationResult.error(errors);
        }

        if (!isBalancedParentheses(normalized)) {
            errors.add("Unbalanced parentheses in query");
            return QueryValidationResult.error(errors);
        }

        if (!isBalancedBrackets(normalized)) {
            errors.add("Unbalanced brackets in query");
            return QueryValidationResult.error(errors);
        }

        if (!isBalancedQuotes(normalized)) {
            errors.add("Unbalanced quotes in query");
            return QueryValidationResult.error(errors);
        }

        List<String> clauses = extractClauses(normalized);
        if (clauses.size() > MAX_CLAUSES) {
            errors.add("Too many query clauses (max " + MAX_CLAUSES + ")");
            return QueryValidationResult.error(errors);
        }

        for (String clause : clauses) {
            if (clause.contains(":") && !containsLogicalOperators(clause)) {
                String fieldError = validateFieldClause(clause);
                if (fieldError != null) {
                    errors.add(fieldError);
                }
            }
        }

        if (!errors.isEmpty()) {
            return QueryValidationResult.error(errors);
        }

        return QueryValidationResult.valid();
    }

    private QueryValidationResult validateAgainstInjection(String query) {
        String sanitized = query.replaceAll("\"[^\"]*\"", "");
        sanitized = sanitized.replaceAll("'[^']*'", "");

        if (SQL_INJECTION_PATTERN.matcher(sanitized).find()) {
            log.warn("Potential SQL injection detected in query: {}", query);
            return QueryValidationResult.error(
                ErrorCode.SQL_INJECTION_DETECTED,
                "Query contains potentially malicious patterns"
            );
        }

        return QueryValidationResult.valid();
    }

    private QueryValidationResult validateAuthorization(String query, List<String> authorizedIndexes) {
        Set<String> queriedIndexes = extractIndexNames(query);

        if (!queriedIndexes.isEmpty()) {
            Set<String> authorized = authorizedIndexes.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

            for (String indexName : queriedIndexes) {
                if (!authorized.contains(indexName.toLowerCase())) {
                    return QueryValidationResult.error(
                        ErrorCode.UNAUTHORIZED,
                        "User is not authorized to query index: " + indexName
                    );
                }
            }
        }

        return QueryValidationResult.valid();
    }

    private String validateClause(String clause) {
        clause = clause.trim();
        if (clause.isEmpty()) {
            return null;
        }

        if (isLogicalOperator(clause)) {
            return null;
        }

        if (clause.startsWith("(") && clause.endsWith(")")) {
            int depth = 1;
            for (int i = 1; i < clause.length() - 1; i++) {
                if (clause.charAt(i) == '(') depth++;
                if (clause.charAt(i) == ')') depth--;
            }
            if (depth == 0) {
                return validateClause(clause.substring(1, clause.length() - 1));
            }
        }

        if (clause.contains(":")) {
            return validateFieldClause(clause);
        }

        return null;
    }

    private String validateFieldClause(String clause) {
        String[] parts = clause.trim().split(":", 2);
        if (parts.length != 2) {
            return "Invalid field clause format: " + clause;
        }

        String fieldName = parts[0].trim();
        String value = parts[1].trim();

        if (fieldName.isEmpty()) {
            return "Field name cannot be empty";
        }

        if (value.isEmpty()) {
            return "Field value cannot be empty";
        }

        if (!isValidFieldName(fieldName)) {
            return "Invalid field name: " + fieldName + " (must start with letter or underscore)";
        }

        return null;
    }

    private Set<String> extractIndexNames(String query) {
        Set<String> indexes = new HashSet<>();
        
        List<String> clauses = extractClauses(query);
        for (String clause : clauses) {
            if (clause.contains(":")) {
                String[] parts = clause.split(":", 2);
                if (parts.length > 0) {
                    String fieldName = parts[0].trim();
                    if (!fieldName.isEmpty() && isValidFieldName(fieldName)) {
                        indexes.add(fieldName.toLowerCase());
                    }
                }
            }
        }

        return indexes;
    }

    private List<String> extractClauses(String query) {
        List<String> clauses = new ArrayList<>();
        String normalized = query.replaceAll("\\s+:", ":").replaceAll(":\\s+", ":");
        
        StringBuilder current = new StringBuilder();
        int parenDepth = 0;
        boolean inQuotes = false;

        for (char c : normalized.toCharArray()) {
            if (c == '"' && (current.length() == 0 || current.charAt(current.length() - 1) != '\\')) {
                inQuotes = !inQuotes;
            }

            if (!inQuotes) {
                if (c == '(') parenDepth++;
                if (c == ')') parenDepth--;

                if ((c == ' ' || c == '\n') && parenDepth == 0 && current.length() > 0) {
                    String clause = current.toString().trim();
                    if (!clause.isEmpty() && !ALLOWED_LOGICAL_OPS.contains(clause.toUpperCase())) {
                        clauses.add(clause);
                    }
                    current = new StringBuilder();
                    continue;
                }
            }

            current.append(c);
        }

        String lastClause = current.toString().trim();
        if (!lastClause.isEmpty() && !ALLOWED_LOGICAL_OPS.contains(lastClause.toUpperCase())) {
            clauses.add(lastClause);
        }

        return clauses;
    }

    private boolean isBalancedParentheses(String query) {
        int count = 0;
        boolean inQuotes = false;

        for (char c : query.toCharArray()) {
            if (c == '"') inQuotes = !inQuotes;
            if (!inQuotes) {
                if (c == '(') count++;
                if (c == ')') count--;
                if (count < 0) return false;
            }
        }
        return count == 0;
    }

    private boolean isBalancedBrackets(String query) {
        int count = 0;
        boolean inQuotes = false;

        for (char c : query.toCharArray()) {
            if (c == '"') inQuotes = !inQuotes;
            if (!inQuotes) {
                if (c == '[') count++;
                if (c == ']') count--;
                if (count < 0) return false;
            }
        }
        return count == 0;
    }

    private boolean isBalancedQuotes(String query) {
        int count = 0;
        for (int i = 0; i < query.length(); i++) {
            if (query.charAt(i) == '"' && (i == 0 || query.charAt(i - 1) != '\\')) {
                count++;
            }
        }
        return count % 2 == 0;
    }

    private boolean isLogicalOperator(String term) {
        return ALLOWED_LOGICAL_OPS.contains(term.toUpperCase());
    }

    private boolean containsLogicalOperators(String clause) {
        String upper = clause.toUpperCase();
        return upper.contains(" AND ") || upper.contains(" OR ") || upper.contains(" NOT ");
    }

    private boolean isValidFieldName(String fieldName) {
        return VALID_FIELD_NAME.matcher(fieldName).matches();
    }

    private boolean isIndexReference(String term) {
        return VALID_INDEX_NAME.matcher(term).matches() && term.length() > 0;
    }
}

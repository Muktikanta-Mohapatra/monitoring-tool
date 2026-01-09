package com.monitoring.logforwarder.util;

import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing search query syntax.
 *
 * <p><b>Purpose:</b> Parses user search queries into structured components
 * for Elasticsearch query building, handling quoted strings, field operators,
 * and wildcards.</p>
 *
 * <p><b>Supported Syntax:</b></p>
 * <ul>
 *   <li>Quoted strings: {@code "exact phrase"}</li>
 *   <li>Field operators: {@code field=value}, {@code field:value}</li>
 *   <li>Wildcards: {@code error*}, {@code *warning*}</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
public class QueryParser {

    private static final Pattern QUOTED_STRING = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern FIELD_OPERATOR = Pattern.compile("(\\w+)\\s*([=:<>!]+)\\s*([\\w\\-\\.]+)");
    private static final Pattern WILDCARD = Pattern.compile("\\*");

    public static Map<String, Object> parseQuery(String query) {
        Map<String, Object> parsedQuery = new HashMap<>();
        
        if (query == null || query.trim().isEmpty()) {
            return parsedQuery;
        }

        List<String> terms = extractTerms(query);
        List<String> fields = extractFields(query);
        
        parsedQuery.put("terms", terms);
        parsedQuery.put("fields", fields);
        parsedQuery.put("raw", query);
        parsedQuery.put("hasWildcard", query.contains("*"));
        parsedQuery.put("hasQuotes", query.contains("\""));

        return parsedQuery;
    }

    public static List<String> extractTerms(String query) {
        List<String> terms = new ArrayList<>();
        
        if (query == null || query.isEmpty()) {
            return terms;
        }

        Matcher quotedMatcher = QUOTED_STRING.matcher(query);
        while (quotedMatcher.find()) {
            terms.add(quotedMatcher.group(1));
        }

        String unquotedQuery = QUOTED_STRING.matcher(query).replaceAll("");
        String[] parts = unquotedQuery.trim().split("\\s+");
        
        for (String part : parts) {
            if (!part.isEmpty() && !isOperator(part)) {
                terms.add(part);
            }
        }

        return terms;
    }

    public static List<String> extractFields(String query) {
        List<String> fields = new ArrayList<>();
        
        if (query == null || query.isEmpty()) {
            return fields;
        }

        Matcher fieldMatcher = FIELD_OPERATOR.matcher(query);
        while (fieldMatcher.find()) {
            String fieldName = fieldMatcher.group(1);
            if (!isOperator(fieldName)) {
                fields.add(fieldName);
            }
        }

        return fields;
    }

    public static Map<String, String> parseFieldOperatorValue(String expression) {
        Map<String, String> result = new HashMap<>();
        
        Matcher matcher = FIELD_OPERATOR.matcher(expression);
        if (matcher.find()) {
            result.put("field", matcher.group(1));
            result.put("operator", matcher.group(2));
            result.put("value", matcher.group(3));
        }

        return result;
    }

    public static List<String> splitByOperator(String query, String operator) {
        List<String> parts = new ArrayList<>();
        
        if (query == null || query.isEmpty()) {
            return parts;
        }

        String[] splitParts = query.split("\\s+" + operator + "\\s+", -1);
        for (String part : splitParts) {
            if (!part.trim().isEmpty()) {
                parts.add(part.trim());
            }
        }

        return parts;
    }

    public static boolean containsOperator(String query) {
        if (query == null || query.isEmpty()) {
            return false;
        }
        return query.toUpperCase().contains(" AND ") || 
               query.toUpperCase().contains(" OR ") || 
               query.toUpperCase().contains(" NOT ");
    }

    public static String normalizeLuceneQuery(String query) {
        if (query == null || query.isEmpty()) {
            return query;
        }

        String normalized = query.trim();
        normalized = normalized.replaceAll("\\s+", " ");
        normalized = normalized.replaceAll("\\s+([+\\-:])", "$1");
        normalized = normalized.replaceAll("([+\\-:])\\s+", "$1");

        return normalized;
    }

    public static String escapeSpecialCharacters(String query) {
        if (query == null) {
            return null;
        }

        String[] specialChars = {"+", "-", "&&", "||", "!", "(", ")", "{", "}", "[", "]", "^", "\"", "~", "*", "?", ":", "\\"};
        String escaped = query;

        for (String specialChar : specialChars) {
            escaped = escaped.replace(specialChar, "\\" + specialChar);
        }

        return escaped;
    }

    public static boolean hasWildcard(String query) {
        return query != null && WILDCARD.matcher(query).find();
    }

    public static String expandWildcard(String term, String replacement) {
        if (term == null) {
            return null;
        }
        return WILDCARD.matcher(term).replaceAll(replacement);
    }

    public static boolean isOperator(String word) {
        if (word == null) {
            return false;
        }

        String upperWord = word.toUpperCase();
        return upperWord.equals("AND") || upperWord.equals("OR") || upperWord.equals("NOT");
    }

    public static List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        
        if (query == null || query.isEmpty()) {
            return tokens;
        }

        StringBuilder token = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
                token.append(c);
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (token.length() > 0) {
                    tokens.add(token.toString());
                    token = new StringBuilder();
                }
            } else {
                token.append(c);
            }
        }

        if (token.length() > 0) {
            tokens.add(token.toString());
        }

        return tokens;
    }

    public static String removeQuotes(String term) {
        if (term == null) {
            return null;
        }
        return term.replaceAll("\"", "");
    }

    public static boolean isPhrase(String term) {
        return term != null && term.startsWith("\"") && term.endsWith("\"");
    }

    public static Map<String, Object> parseTimeRangeQuery(String query) {
        Map<String, Object> timeRange = new HashMap<>();
        
        Pattern timePattern = Pattern.compile("\\b(last|past)\\s+(\\d+)\\s*(minute|hour|day|week|month|year)s?\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = timePattern.matcher(query);

        if (matcher.find()) {
            int value = Integer.parseInt(matcher.group(2));
            String unit = matcher.group(3).toLowerCase();
            
            timeRange.put("value", value);
            timeRange.put("unit", unit);
            timeRange.put("relative", true);
        }

        return timeRange;
    }
}

package com.monitoring.logforwarder.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/**
 * Builder utility for constructing Elasticsearch JSON queries.
 *
 * <p><b>Purpose:</b> Provides a fluent API for building Elasticsearch bool queries
 * with must, should, must_not, and filter clauses.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * String query = new ElasticsearchQueryBuilder()
 *     .must("severity", "ERROR")
 *     .filter("sourcetype", "syslog")
 *     .rangeFilter("timestamp", start, end)
 *     .build();
 * }</pre>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
public class ElasticsearchQueryBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ObjectNode query;
    private final ObjectNode bool;

    public ElasticsearchQueryBuilder() {
        this.query = objectMapper.createObjectNode();
        this.bool = objectMapper.createObjectNode();
        this.query.set("bool", bool);
    }

    public ElasticsearchQueryBuilder must(String field, String value) {
        ObjectNode match = objectMapper.createObjectNode();
        match.put(field, value);
        
        ArrayNode mustArray = (ArrayNode) bool.get("must");
        if (mustArray == null) {
            mustArray = objectMapper.createArrayNode();
            bool.set("must", mustArray);
        }
        
        ObjectNode matchQuery = objectMapper.createObjectNode();
        matchQuery.set("match", match);
        mustArray.add(matchQuery);
        
        return this;
    }

    public ElasticsearchQueryBuilder should(String field, String value) {
        ArrayNode shouldArray = (ArrayNode) bool.get("should");
        if (shouldArray == null) {
            shouldArray = objectMapper.createArrayNode();
            bool.set("should", shouldArray);
        }
        
        ObjectNode match = objectMapper.createObjectNode();
        match.put(field, value);
        ObjectNode matchQuery = objectMapper.createObjectNode();
        matchQuery.set("match", match);
        shouldArray.add(matchQuery);
        
        return this;
    }

    public ElasticsearchQueryBuilder mustNot(String field, String value) {
        ArrayNode mustNotArray = (ArrayNode) bool.get("must_not");
        if (mustNotArray == null) {
            mustNotArray = objectMapper.createArrayNode();
            bool.set("must_not", mustNotArray);
        }
        
        ObjectNode match = objectMapper.createObjectNode();
        match.put(field, value);
        ObjectNode matchQuery = objectMapper.createObjectNode();
        matchQuery.set("match", match);
        mustNotArray.add(matchQuery);
        
        return this;
    }

    public ElasticsearchQueryBuilder range(String field, String gte, String lte) {
        ObjectNode rangeQuery = objectMapper.createObjectNode();
        ObjectNode rangeField = objectMapper.createObjectNode();
        
        if (gte != null) {
            rangeField.put("gte", gte);
        }
        if (lte != null) {
            rangeField.put("lte", lte);
        }
        
        rangeQuery.set("range", objectMapper.createObjectNode().set(field, rangeField));
        
        ArrayNode mustArray = (ArrayNode) bool.get("must");
        if (mustArray == null) {
            mustArray = objectMapper.createArrayNode();
            bool.set("must", mustArray);
        }
        mustArray.add(rangeQuery);
        
        return this;
    }

    public ElasticsearchQueryBuilder term(String field, String value) {
        ObjectNode termQuery = objectMapper.createObjectNode();
        termQuery.put(field, value);
        
        ArrayNode mustArray = (ArrayNode) bool.get("must");
        if (mustArray == null) {
            mustArray = objectMapper.createArrayNode();
            bool.set("must", mustArray);
        }
        
        ObjectNode termWrapper = objectMapper.createObjectNode();
        termWrapper.set("term", termQuery);
        mustArray.add(termWrapper);
        
        return this;
    }

    public ElasticsearchQueryBuilder wildcard(String field, String value) {
        ObjectNode wildcardQuery = objectMapper.createObjectNode();
        wildcardQuery.put(field, value);
        
        ArrayNode mustArray = (ArrayNode) bool.get("must");
        if (mustArray == null) {
            mustArray = objectMapper.createArrayNode();
            bool.set("must", mustArray);
        }
        
        ObjectNode wildcardWrapper = objectMapper.createObjectNode();
        wildcardWrapper.set("wildcard", wildcardQuery);
        mustArray.add(wildcardWrapper);
        
        return this;
    }

    public ElasticsearchQueryBuilder prefix(String field, String value) {
        ObjectNode prefixQuery = objectMapper.createObjectNode();
        prefixQuery.put(field, value);
        
        ArrayNode mustArray = (ArrayNode) bool.get("must");
        if (mustArray == null) {
            mustArray = objectMapper.createArrayNode();
            bool.set("must", mustArray);
        }
        
        ObjectNode prefixWrapper = objectMapper.createObjectNode();
        prefixWrapper.set("prefix", prefixQuery);
        mustArray.add(prefixWrapper);
        
        return this;
    }

    public ElasticsearchQueryBuilder exists(String field) {
        ObjectNode existsQuery = objectMapper.createObjectNode();
        existsQuery.put("field", field);
        
        ArrayNode mustArray = (ArrayNode) bool.get("must");
        if (mustArray == null) {
            mustArray = objectMapper.createArrayNode();
            bool.set("must", mustArray);
        }
        
        ObjectNode existsWrapper = objectMapper.createObjectNode();
        existsWrapper.set("exists", existsQuery);
        mustArray.add(existsWrapper);
        
        return this;
    }

    public ObjectNode build() {
        return query;
    }

    public String buildJson() {
        try {
            return objectMapper.writeValueAsString(query);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static ObjectNode simpleMatchQuery(String field, String value) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ObjectNode matchNode = mapper.createObjectNode();
        ObjectNode fieldNode = mapper.createObjectNode();
        
        fieldNode.put("query", value);
        matchNode.set(field, fieldNode);
        root.set("match", matchNode);
        
        return root;
    }

    public static ObjectNode multiMatchQuery(String query, String... fields) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        
        root.put("query", query);
        
        ArrayNode fieldsArray = mapper.createArrayNode();
        for (String field : fields) {
            fieldsArray.add(field);
        }
        root.set("fields", fieldsArray);
        
        return root;
    }

    public static ObjectNode matchAllQuery() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.set("match_all", mapper.createObjectNode());
        return root;
    }

    public static ObjectNode dateRangeQuery(String field, String startDate, String endDate) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ObjectNode rangeNode = mapper.createObjectNode();
        ObjectNode dateNode = mapper.createObjectNode();
        
        if (startDate != null) {
            dateNode.put("gte", startDate);
        }
        if (endDate != null) {
            dateNode.put("lte", endDate);
        }
        
        rangeNode.set(field, dateNode);
        root.set("range", rangeNode);
        
        return root;
    }

    public static ObjectNode aggregationQuery(String name, String field) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ObjectNode agg = mapper.createObjectNode();
        
        agg.put("field", field);
        root.set(name, mapper.createObjectNode().set("terms", agg));
        
        return root;
    }
}

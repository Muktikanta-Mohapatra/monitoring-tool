package com.monitoring.logforwarder.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.web.client.RestTemplate;
import java.util.Base64;

/**
 * Configuration class for Elasticsearch client connectivity.
 *
 * <p><b>Purpose:</b> Configures the Elasticsearch client for indexing and searching
 * log events. Enables efficient full-text search, aggregations, and real-time
 * analytics on large volumes of log data.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Uses Spring Data Elasticsearch for repository pattern</li>
 *   <li>Configurable connection and socket timeouts</li>
 *   <li>Supports multiple Elasticsearch nodes via URI configuration</li>
 *   <li>Enables Elasticsearch repositories in specified package</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Configuration in application.yml:
 * spring:
 *   elasticsearch:
 *     rest:
 *       uris: http://localhost:9200
 *       connection-timeout: 5000
 *       socket-timeout: 30000
 *
 * // Using in service:
 * &#64;Autowired
 * private ElasticsearchOperations elasticsearchOperations;
 *
 * public void indexEvent(EventDTO event) {
 *     elasticsearchOperations.save(event);
 * }
 * }</pre>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see ElasticsearchConfiguration
 * @see ClientConfiguration
 */
@Configuration
@EnableElasticsearchRepositories(
    basePackages = "com.monitoring.logforwarder.repository.elasticsearch"
)
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.rest.username:elastic}")
    private String username;

    @Value("${spring.elasticsearch.rest.password:elasticsearch}")
    private String password;

    /**
     * Creates a RestTemplate bean for Elasticsearch HTTP interactions.
     *
     * <p><b>Purpose:</b> Provides a RestTemplate instance for direct HTTP calls
     * to Elasticsearch REST API endpoints, such as applying index templates, with
     * basic authentication support.</p>
     *
     * @return configured RestTemplate bean with basic authentication
     */
    @Bean(name = "elasticsearchRestTemplate")
    public RestTemplate elasticsearchRestTemplate(RestTemplateBuilder builder) {
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        
        return builder
            .defaultHeader("Authorization", "Basic " + encodedAuth)
            .build();
    }


}

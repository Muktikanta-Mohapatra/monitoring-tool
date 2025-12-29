package com.monitoring.logforwarder.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Configuration class for Jackson JSON serialization/deserialization settings.
 *
 * <p><b>Purpose:</b> Configures the ObjectMapper used throughout the application for
 * JSON processing, ensuring consistent serialization of dates, handling of unknown
 * properties, and proper decimal precision.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>JavaTimeModule enabled for Java 8 date/time support</li>
 *   <li>Dates serialized as ISO-8601 strings, not timestamps</li>
 *   <li>Empty strings accepted as null objects</li>
 *   <li>BigDecimal used for float precision</li>
 *   <li>Unknown properties ignored during deserialization</li>
 *   <li>Date format: yyyy-MM-dd'T'HH:mm:ss.SSSZ</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Using the configured ObjectMapper:
 * &#64;Autowired
 * private ObjectMapper objectMapper;
 *
 * String json = objectMapper.writeValueAsString(eventDTO);
 * EventDTO parsed = objectMapper.readValue(json, EventDTO.class);
 * }</pre>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see ObjectMapper
 * @see JavaTimeModule
 */
@Configuration
public class JacksonConfig {

    /**
     * Creates and configures the application-wide ObjectMapper.
     *
     * <p><b>Purpose:</b> Provides a consistently configured ObjectMapper for all
     * JSON serialization and deserialization operations in the application.</p>
     *
     * <p><b>Technical Details:</b></p>
     * <ul>
     *   <li>Enables Java 8 date/time module</li>
     *   <li>Configures lenient deserialization</li>
     *   <li>Disables timestamp-based date serialization</li>
     * </ul>
     *
     * @return configured ObjectMapper bean
     */
    @Bean
    public ObjectMapper objectMapper() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(formatter));

        return Jackson2ObjectMapperBuilder.json()
            .modules(javaTimeModule)
            .featuresToEnable(
                DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT,
                DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS
            )
            .featuresToDisable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
            )
            .build();
    }
}

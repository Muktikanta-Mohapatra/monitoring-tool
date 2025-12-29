package com.monitoring.logforwarder.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.logforwarder.dto.EventDTO;
import com.monitoring.logforwarder.entity.Event;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * MapStruct mapper for Event entity and DTO conversions.
 *
 * <p><b>Purpose:</b> Provides bidirectional mapping between Event entities and EventDTOs
 * with JSON serialization for map fields (parsedFields, enrichedFields, indexedFields).</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Mapper
public interface EventMapper {
    
    EventMapper INSTANCE = Mappers.getMapper(EventMapper.class);
    ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    @Mapping(source = "parsedFields", target = "parsedFields", qualifiedByName = "stringToMap")
    @Mapping(source = "enrichedFields", target = "enrichedFields", qualifiedByName = "stringToMap")
    @Mapping(source = "indexedFields", target = "indexedFields", qualifiedByName = "stringToMap")
    @Mapping(source = "timestamp", target = "timestamp", qualifiedByName = "localDateTimeToOffsetDateTime")
    EventDTO entityToDto(Event event);
    
    @Mapping(source = "parsedFields", target = "parsedFields", qualifiedByName = "mapToString")
    @Mapping(source = "enrichedFields", target = "enrichedFields", qualifiedByName = "mapToString")
    @Mapping(source = "indexedFields", target = "indexedFields", qualifiedByName = "mapToString")
    @Mapping(source = "timestamp", target = "timestamp", qualifiedByName = "offsetDateTimeToLocalDateTime")
    @Mapping(source = "sourceId", target = "sourceId", qualifiedByName = "sourceIdWithDefault")
    Event dtoToEntity(EventDTO eventDTO);

    @Named("localDateTimeToOffsetDateTime")
    default OffsetDateTime localDateTimeToOffsetDateTime(LocalDateTime value) {
        return value != null ? value.atOffset(ZoneOffset.UTC) : null;
    }

    @Named("offsetDateTimeToLocalDateTime")
    default LocalDateTime offsetDateTimeToLocalDateTime(OffsetDateTime value) {
        return value != null ? value.toLocalDateTime() : null;
    }

    @Named("stringToMap")
    default Map<String, Object> stringToMap(String value) {
        if (value == null || value.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    @Named("mapToString")
    default String mapToString(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Named("sourceIdWithDefault")
    default Integer sourceIdWithDefault(Integer value) {
        return value != null ? value : 1;
    }
}

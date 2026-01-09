package com.monitoring.logforwarder.dto.mapper;

import com.forwarder.v1.Event;
import com.forwarder.v1.Metadata;
import com.monitoring.logforwarder.dto.EventDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import com.google.protobuf.ByteString;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * MapStruct mapper for converting between Event protobuf messages and DTOs.
 *
 * <p><b>Purpose:</b> Converts gRPC protobuf Event messages from LogForwarder agents
 * into EventDTO objects for processing, storage, and API responses.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Handles protobuf timestamp (nanos) to Java Instant/OffsetDateTime conversion</li>
 *   <li>Extracts metadata fields (sourceId, hostId, indexId) from protobuf maps</li>
 *   <li>Converts raw byte data to Base64 strings for JSON serialization</li>
 *   <li>Spring-managed component via MapStruct componentModel</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 * @see com.forwarder.v1.Event
 * @see EventDTO
 */
@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "proto.timestampNanos", target = "timestampNanos")
    @Mapping(source = "proto", target = "timestampInstant", qualifiedByName = "toTimestampInstant")
    @Mapping(source = "proto", target = "timestamp", qualifiedByName = "toOffsetDateTime")
    @Mapping(source = "proto.sourceName", target = "sourceName")
    @Mapping(source = "proto.severity", target = "severity")
    @Mapping(source = "proto.forwarderId", target = "forwarderId")
    @Mapping(source = "proto", target = "sourceId", qualifiedByName = "extractSourceId")
    @Mapping(source = "proto.metadata.sourcetype", target = "sourcetype")
    @Mapping(source = "proto", target = "hostId", qualifiedByName = "extractHostId")
    @Mapping(source = "proto", target = "indexId", qualifiedByName = "extractIndexId")
    @Mapping(source = "proto", target = "rawData", qualifiedByName = "bytesToBase64String")
    @Mapping(target = "batchId", ignore = true)
    @Mapping(target = "parseDurationUs", ignore = true)
    @Mapping(target = "detectedFormat", ignore = true)
    @Mapping(target = "elasticsearchId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isIndexed", ignore = true)
    @Mapping(target = "isEnriched", ignore = true)
    @Mapping(target = "rawMessage", ignore = true)
    @Mapping(target = "indexedFields", ignore = true)
    @Mapping(target = "version", constant = "1")
    EventDTO toEventDTO(Event proto);

    @Mapping(source = "dto.timestampNanos", target = "timestampNanos")
    @Mapping(source = "dto", target = "rawData", qualifiedByName = "base64StringToBytes")
    @Mapping(source = "dto", target = "metadata", qualifiedByName = "toMetadata")
    @Mapping(source = "dto.severity", target = "severity")
    @Mapping(source = "dto.forwarderId", target = "forwarderId")
    @Mapping(source = "dto.sourceName", target = "sourceName")
    @Mapping(target = "sourceId", expression = "java(String.valueOf(dto.getSourceId() != null ? dto.getSourceId() : 0))")
    @Mapping(target = "parsedFields", ignore = true)
    @Mapping(target = "enrichedFields", ignore = true)
    Event toEvent(EventDTO dto);

    @Named("toTimestampInstant")
    default Instant toTimestampInstant(Event event) {
        if (event.getTimestampNanos() == 0) {
            return null;
        }
        return Instant.ofEpochSecond(
            event.getTimestampNanos() / 1_000_000_000,
            event.getTimestampNanos() % 1_000_000_000
        );
    }

    @Named("toOffsetDateTime")
    default OffsetDateTime toOffsetDateTime(Event event) {
        if (event.getTimestampNanos() == 0) {
            return null;
        }
        Instant instant = Instant.ofEpochSecond(
            event.getTimestampNanos() / 1_000_000_000,
            event.getTimestampNanos() % 1_000_000_000
        );
        return instant.atOffset(ZoneOffset.UTC);
    }

    @Named("extractSourceId")
    default Integer extractSourceId(Event event) {
        if (event.getSourceId() == null || event.getSourceId().isEmpty()) {
            return null;
        }
        try {
            int result = Integer.parseInt(event.getSourceId().trim());
            return result > 0 ? result : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Named("extractHostId")
    default Integer extractHostId(Event event) {
        if (event.getMetadata() == null || event.getMetadata().getHost() == null) {
            return null;
        }
        String host = event.getMetadata().getHost();
        if (host.trim().isEmpty()) {
            return null;
        }
        try {
            int result = Integer.parseInt(host.trim());
            return result > 0 ? result : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Named("extractIndexId")
    default Short extractIndexId(Event event) {
        if (event.getMetadata() == null || event.getMetadata().getIndex() == null) {
            return null;
        }
        String index = event.getMetadata().getIndex();
        if (index.trim().isEmpty()) {
            return null;
        }
        try {
            short result = Short.parseShort(index.trim());
            return result > 0 ? result : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Named("bytesToBase64String")
    default String bytesToBase64String(Event event) {
        if (event.getRawData().isEmpty()) {
            return null;
        }
        return Base64.getEncoder().encodeToString(event.getRawData().toByteArray());
    }

    @Named("base64StringToBytes")
    default ByteString base64StringToBytes(EventDTO dto) {
        if (dto.getRawData() == null || dto.getRawData().isEmpty()) {
            return ByteString.EMPTY;
        }
        byte[] decoded = Base64.getDecoder().decode(dto.getRawData());
        return ByteString.copyFrom(decoded);
    }

    @Named("toMetadata")
    default Metadata toMetadata(EventDTO dto) {
        Metadata.Builder builder = Metadata.newBuilder()
            .setSource(dto.getSourceName() != null ? dto.getSourceName() : "")
            .setSourcetype(dto.getSourcetype() != null ? dto.getSourcetype() : "")
            .setHost(dto.getHostId() != null ? String.valueOf(dto.getHostId()) : "")
            .setIndex(dto.getIndexId() != null ? String.valueOf(dto.getIndexId()) : "");
        
        return builder.build();
    }

    @Named("convertFromObjectMap")
    default Map<String, String> convertFromObjectMap(EventDTO dto) {
        if (dto.getParsedFields() == null || dto.getParsedFields().isEmpty()) {
            return new HashMap<>();
        }
        Map<String, String> result = new HashMap<>();
        dto.getParsedFields().forEach((key, value) -> result.put(key, value != null ? value.toString() : ""));
        return result;
    }

    @Named("convertFromObjectMapEnriched")
    default Map<String, String> convertFromObjectMapEnriched(EventDTO dto) {
        if (dto.getEnrichedFields() == null || dto.getEnrichedFields().isEmpty()) {
            return new HashMap<>();
        }
        Map<String, String> result = new HashMap<>();
        dto.getEnrichedFields().forEach((key, value) -> result.put(key, value != null ? value.toString() : ""));
        return result;
    }

    default Map<String, String> convertToStringMap(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, String> result = new HashMap<>();
        input.forEach((key, value) -> result.put(key, value != null ? value.toString() : ""));
        return result;
    }
}

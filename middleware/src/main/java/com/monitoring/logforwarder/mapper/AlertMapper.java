package com.monitoring.logforwarder.mapper;

import com.monitoring.logforwarder.dto.AlertDTO;
import com.monitoring.logforwarder.entity.Alert;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * MapStruct mapper for Alert entity and DTO conversions.
 *
 * <p><b>Purpose:</b> Provides bidirectional mapping between Alert entities and AlertDTOs.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Mapper
public interface AlertMapper {
    
    AlertMapper INSTANCE = Mappers.getMapper(AlertMapper.class);
    
    AlertDTO entityToDto(Alert alert);
    
    Alert dtoToEntity(AlertDTO alertDTO);
}

package com.monitoring.logforwarder.mapper;

import com.monitoring.logforwarder.dto.ForwarderDTO;
import com.monitoring.logforwarder.entity.Forwarder;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * MapStruct mapper for Forwarder entity and DTO conversions.
 *
 * <p><b>Purpose:</b> Provides bidirectional mapping between Forwarder entities and ForwarderDTOs.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Mapper
public interface ForwarderMapper {
    
    ForwarderMapper INSTANCE = Mappers.getMapper(ForwarderMapper.class);
    
    ForwarderDTO entityToDto(Forwarder forwarder);
    
    Forwarder dtoToEntity(ForwarderDTO forwarderDTO);
}

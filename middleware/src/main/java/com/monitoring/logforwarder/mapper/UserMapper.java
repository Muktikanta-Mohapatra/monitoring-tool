package com.monitoring.logforwarder.mapper;

import com.monitoring.logforwarder.dto.UserDTO;
import com.monitoring.logforwarder.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * MapStruct mapper for User entity and DTO conversions.
 *
 * <p><b>Purpose:</b> Provides bidirectional mapping between User entities and UserDTOs.</p>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Mapper
public interface UserMapper {
    
    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);
    
    UserDTO entityToDto(User user);
    
    User dtoToEntity(UserDTO userDTO);
}

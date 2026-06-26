package com.codeit.mpl.domain.user.mapper;

import com.codeit.mpl.domain.user.dto.response.UserDto;
import com.codeit.mpl.domain.user.dto.response.UserSummary;
import com.codeit.mpl.domain.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto toDto(User user);

    @Mapping(source = "id", target = "userId")
    UserSummary toSummary(User user);
}

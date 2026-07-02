package com.codeit.mpl.domain.user.mapper;

import com.codeit.mpl.domain.user.dto.response.UserDto;
import com.codeit.mpl.domain.user.dto.response.UserSummary;
import com.codeit.mpl.domain.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "profileImageUrl", expression = "java(resolveProfileImageUrl(user))")
    UserDto toDto(User user);

    @Mapping(source = "id", target = "userId")
    @Mapping(target = "profileImageUrl", expression = "java(resolveProfileImageUrl(user))")
    UserSummary toSummary(User user);

    // profileImageUrl 컬럼엔 스토리지 key가 저장되므로, 클라이언트에는 항상 유효한
    // presigned URL로 리다이렉트해주는 다운로드 엔드포인트 경로를 내려준다.
    default String resolveProfileImageUrl(User user) {
        return user.getProfileImageUrl() != null
                ? "/api/users/" + user.getId() + "/profile-image"
                : null;
    }
}

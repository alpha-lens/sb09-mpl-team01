package com.codeit.mpl.domain.user.mapper;

import com.codeit.mpl.domain.user.dto.response.UserDto;
import com.codeit.mpl.domain.user.dto.response.UserSummary;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.infra.storage.BinaryContentStorage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class UserMapper {

    @Autowired
    protected BinaryContentStorage binaryContentStorage;

    @Mapping(target = "profileImageUrl", expression = "java(resolveProfileImageUrl(user))")
    public abstract UserDto toDto(User user);

    @Mapping(source = "id", target = "userId")
    @Mapping(target = "profileImageUrl", expression = "java(resolveProfileImageUrl(user))")
    public abstract UserSummary toSummary(User user);

    // profileImageUrl 컬럼엔 스토리지 key가 저장되므로, 응답 시점마다
    // 실제로 열람 가능한 URL(로컬 정적 경로 또는 S3 presigned URL)로 변환해서 내려준다.
    protected String resolveProfileImageUrl(User user) {
        return user.getProfileImageUrl() != null
                ? binaryContentStorage.getUrl(user.getProfileImageUrl())
                : null;
    }
}

package com.codeit.mpl.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UserSummary(
    @NotBlank(message = "사용자 ID는 비어있을 수 없습니다") UUID userId,
    @NotNull(message = "이름이 비어 있을 수 없습니다") String name,
    String profileImageUrl
) {

}

package com.codeit.mpl.domain.user.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UserSummary {

    private UUID userId;
    private String name;
    private String profileImageUrl;
}

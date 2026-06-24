package com.codeit.mpl.domain.dm.dto;

import com.codeit.mpl.infra.common.dto.Direction;
import java.util.UUID;

public record DirectMessageSearchRequest(
    String keywordLike,
    String cursor,
    UUID idAfter,
    int limit,
    Direction sortDirection,
    String sortBy
) {

}
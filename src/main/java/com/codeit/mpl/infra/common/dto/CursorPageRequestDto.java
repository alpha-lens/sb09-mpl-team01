package com.codeit.mpl.infra.common.dto;

import java.util.UUID;

public record CursorPageRequestDto(
    String cursor,
    UUID idAfter,
    Integer limit,
    Direction sortDirection,
    String sortBy
) {

}

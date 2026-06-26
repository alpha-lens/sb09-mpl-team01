package com.codeit.mpl.infra.common.dto;

import java.util.UUID;

public record SearchRequest(
    String cursor,
    UUID idAfter,
    int limit,
    Direction sortDirection,
    String sortBy
) {

}

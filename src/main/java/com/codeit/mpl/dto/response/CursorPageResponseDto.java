package com.codeit.mpl.dto.response;

import java.util.List;

public record CursorPageResponseDto<T>(
    List<T> data,
    String nextCursor,
    String nextIdAfter,
    boolean hasNext,
    long totalCount,
    String sortBy,
    Direction sortDirection
) {
}

package com.codeit.mpl.domain.review.dto.request;

import com.codeit.mpl.infra.common.dto.Direction;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewSearchRequest {
  private UUID contentId;
  private String cursor;
  private String idAfter;
  private int limit;
  private String sortBy;
  private Direction sortDirection;
}
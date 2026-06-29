package com.codeit.mpl.domain.review.dto.request;

import com.codeit.mpl.infra.common.dto.Direction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewSearchRequest {

  @NotNull
  private UUID contentId;

  private String cursor;
  private String idAfter;

  @Positive
  private int limit;

  @NotNull
  private String sortBy;

  @NotNull
  private Direction sortDirection;
}
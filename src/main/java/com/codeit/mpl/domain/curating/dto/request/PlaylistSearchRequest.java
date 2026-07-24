package com.codeit.mpl.domain.curating.dto.request;

import com.codeit.mpl.infra.common.dto.Direction;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class PlaylistSearchRequest {
  private String keywordLike;
  private UUID ownerIdEqual;
  private UUID subscriberIdEqual;
  private String cursor;
  private UUID idAfter;
  private int limit;
  private String sortBy;
  private Direction sortDirection;
}
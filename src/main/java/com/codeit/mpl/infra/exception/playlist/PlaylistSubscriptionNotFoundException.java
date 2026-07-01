package com.codeit.mpl.infra.exception.playlist;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class PlaylistSubscriptionNotFoundException extends MplException {
  public PlaylistSubscriptionNotFoundException() {
    super(ErrorCode.PLAYLIST_SUBSCRIPTION_NOT_FOUND); // 구독 안 했는데 구독취소 시도할 때
  }
}
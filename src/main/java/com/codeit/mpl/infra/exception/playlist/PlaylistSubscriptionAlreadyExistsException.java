package com.codeit.mpl.infra.exception.playlist;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class PlaylistSubscriptionAlreadyExistsException extends MplException {
  public PlaylistSubscriptionAlreadyExistsException() {
    super(ErrorCode.PLAYLIST_SUBSCRIPTION_ALREADY_EXISTS); // 이미 구독중인데 또 구독 시도할 때
  }
}
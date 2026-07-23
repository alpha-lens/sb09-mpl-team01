package com.codeit.mpl.infra.exception.playlist;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class InvalidPlaylistLimitException extends MplException {
  public InvalidPlaylistLimitException() {
    super(ErrorCode.INVALID_PLAYLIST_LIMIT); // limit이 0 이하일 때
  }
}
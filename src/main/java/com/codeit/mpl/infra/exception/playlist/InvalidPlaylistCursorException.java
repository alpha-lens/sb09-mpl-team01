package com.codeit.mpl.infra.exception.playlist;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class InvalidPlaylistCursorException extends MplException {
  public InvalidPlaylistCursorException() {
    super(ErrorCode.INVALID_PLAYLIST_CURSOR); // cursor 값이 잘못됐을 때
  }
}
package com.codeit.mpl.infra.exception.playlist;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class PlaylistForbiddenException extends MplException {
  public PlaylistForbiddenException() {
    super(ErrorCode.PLAYLIST_FORBIDDEN); // 소유자가 아닌 사람이 수정/삭제 시도할 때
  }
}
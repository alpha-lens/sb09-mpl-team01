package com.codeit.mpl.infra.exception.playlist;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class PlaylistContentAlreadyExistsException extends MplException {
  public PlaylistContentAlreadyExistsException() {
    super(ErrorCode.PLAYLIST_CONTENT_ALREADY_EXISTS); // 이미 추가된 콘텐츠 또 추가 시도할 때
  }
}
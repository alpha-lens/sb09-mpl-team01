package com.codeit.mpl.infra.exception.playlist;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class PlaylistContentNotFoundException extends MplException {
  public PlaylistContentNotFoundException() {
    super(ErrorCode.PLAYLIST_CONTENT_NOT_FOUND); // 플레이리스트에 없는 콘텐츠 삭제 시도할 때
  }
}
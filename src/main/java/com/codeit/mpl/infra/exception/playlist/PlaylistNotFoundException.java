package com.codeit.mpl.infra.exception.playlist;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class PlaylistNotFoundException extends MplException {
  public PlaylistNotFoundException() {
    super(ErrorCode.PLAYLIST_NOT_FOUND); // 플레이리스트 없을 때
  }
}
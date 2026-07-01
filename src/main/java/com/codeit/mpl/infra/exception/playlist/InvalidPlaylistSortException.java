package com.codeit.mpl.infra.exception.playlist;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class InvalidPlaylistSortException extends MplException {
  public InvalidPlaylistSortException() {
    super(ErrorCode.INVALID_PLAYLIST_SORT); // 허용되지 않은 정렬 기준 넣을 때
  }
}
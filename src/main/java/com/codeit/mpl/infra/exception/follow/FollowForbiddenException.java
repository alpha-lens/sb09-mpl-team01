package com.codeit.mpl.infra.exception.follow;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class FollowForbiddenException extends MplException {
  public FollowForbiddenException() {
    super(ErrorCode.FOLLOW_FORBIDDEN);
  }
}
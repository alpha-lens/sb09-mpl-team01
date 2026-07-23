package com.codeit.mpl.infra.exception.follow;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class FollowSelfException extends MplException {
  public FollowSelfException() {
    super(ErrorCode.FOLLOW_SELF);
  }
}
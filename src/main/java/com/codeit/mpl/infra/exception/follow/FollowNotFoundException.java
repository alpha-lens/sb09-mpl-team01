package com.codeit.mpl.infra.exception.follow;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class FollowNotFoundException extends MplException {
  public FollowNotFoundException() {
    super(ErrorCode.FOLLOW_NOT_FOUND);
  }
}
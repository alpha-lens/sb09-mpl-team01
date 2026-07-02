package com.codeit.mpl.infra.exception.follow;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class FollowAlreadyExistsException extends MplException {
  public FollowAlreadyExistsException() {
    super(ErrorCode.FOLLOW_ALREADY_EXISTS);
  }
}
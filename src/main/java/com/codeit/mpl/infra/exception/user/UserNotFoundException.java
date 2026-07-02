package com.codeit.mpl.infra.exception.user;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class UserNotFoundException extends MplException {
  public UserNotFoundException() {
    super(ErrorCode.USER_NOT_FOUND);
  }
}

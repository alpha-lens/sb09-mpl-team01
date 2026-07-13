package com.codeit.mpl.infra.exception.user;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class InvalidCredentialsException extends MplException {
  public InvalidCredentialsException() {
    super(ErrorCode.INVALID_CREDENTIALS);
  }
}

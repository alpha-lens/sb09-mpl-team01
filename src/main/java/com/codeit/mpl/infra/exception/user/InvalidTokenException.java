package com.codeit.mpl.infra.exception.user;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class InvalidTokenException extends MplException {
  public InvalidTokenException() {
    super(ErrorCode.INVALID_TOKEN);
  }
}

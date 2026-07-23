package com.codeit.mpl.infra.exception.user;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class TemporaryPasswordExpiredException extends MplException {
  public TemporaryPasswordExpiredException() {
    super(ErrorCode.TEMPORARY_PASSWORD_EXPIRED);
  }
}

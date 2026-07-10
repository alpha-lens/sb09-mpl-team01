package com.codeit.mpl.infra.exception.user;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class AccountLockedException extends MplException {
  public AccountLockedException() {
    super(ErrorCode.ACCOUNT_LOCKED);
  }
}

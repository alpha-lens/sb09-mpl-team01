package com.codeit.mpl.infra.exception.storage;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class StorageInvalidKeyException extends MplException {
  public StorageInvalidKeyException() {
    super(ErrorCode.STORAGE_INVALID_KEY);
  }
}

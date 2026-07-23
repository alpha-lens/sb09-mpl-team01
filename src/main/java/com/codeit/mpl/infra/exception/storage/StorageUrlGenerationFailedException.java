package com.codeit.mpl.infra.exception.storage;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class StorageUrlGenerationFailedException extends MplException {
  public StorageUrlGenerationFailedException() {
    super(ErrorCode.STORAGE_URL_GENERATION_FAILED);
  }
}

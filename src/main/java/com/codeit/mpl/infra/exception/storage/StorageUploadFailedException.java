package com.codeit.mpl.infra.exception.storage;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class StorageUploadFailedException extends MplException {
  public StorageUploadFailedException() {
    super(ErrorCode.STORAGE_UPLOAD_FAILED);
  }
}

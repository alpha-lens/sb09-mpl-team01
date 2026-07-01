package com.codeit.mpl.infra.exception.review;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class ReviewAlreadyExistsException extends MplException {
  public ReviewAlreadyExistsException() {
    super(ErrorCode.REVIEW_ALREADY_EXISTS); // 같은 콘텐츠에 리뷰를 이미 작성했는데 또 작성 시도할 때
  }
}
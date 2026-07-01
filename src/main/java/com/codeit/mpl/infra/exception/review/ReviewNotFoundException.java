package com.codeit.mpl.infra.exception.review;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class ReviewNotFoundException extends MplException {
  public ReviewNotFoundException() {
    super(ErrorCode.REVIEW_NOT_FOUND); // 존재하지 않는 리뷰 조회/수정/삭제 시도할 때
  }
}
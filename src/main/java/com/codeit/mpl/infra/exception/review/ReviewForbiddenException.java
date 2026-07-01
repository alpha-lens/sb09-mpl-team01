package com.codeit.mpl.infra.exception.review;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class ReviewForbiddenException extends MplException {
  public ReviewForbiddenException() {
    super(ErrorCode.REVIEW_FORBIDDEN); // 내가 쓴 리뷰가 아닌데 수정/삭제 시도할 때
  }
}
package com.codeit.mpl.infra.exception.review;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class InvalidReviewSortException extends MplException {
  public InvalidReviewSortException() {
    super(ErrorCode.INVALID_REVIEW_SORT); // createdAt, rating 이외의 정렬 기준 넣을 때
  }
}
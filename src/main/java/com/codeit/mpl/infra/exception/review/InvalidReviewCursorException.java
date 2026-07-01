package com.codeit.mpl.infra.exception.review;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class InvalidReviewCursorException extends MplException {
  public InvalidReviewCursorException() {
    super(ErrorCode.INVALID_REVIEW_CURSOR); // 커서 페이지네이션에서 cursor/idAfter 둘 중 하나만 있을 때
  }
}
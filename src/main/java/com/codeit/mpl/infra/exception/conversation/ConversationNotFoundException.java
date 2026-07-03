package com.codeit.mpl.infra.exception.conversation;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;

public class ConversationNotFoundException extends MplException {
  public ConversationNotFoundException() {
    super(ErrorCode.CONVERSATION_NOT_FOUND);
  }
}

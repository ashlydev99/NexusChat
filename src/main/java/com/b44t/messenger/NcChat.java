package com.b44t.messenger;

import com.nexuschat.messenger.util.Util;

public class NcChat {

  public static final int NC_CHAT_TYPE_UNDEFINED = 0;
  public static final int NC_CHAT_TYPE_SINGLE = 100;
  public static final int NC_CHAT_TYPE_GROUP = 120;
  public static final int NC_CHAT_TYPE_MAILINGLIST = 140;
  public static final int NC_CHAT_TYPE_OUT_BROANCAST = 160;
  public static final int NC_CHAT_TYPE_IN_BROANCAST = 165;

  public static final int NC_CHAT_NO_CHAT = 0;
  public static final int NC_CHAT_ID_ARCHIVED_LINK = 6;
  public static final int NC_CHAT_ID_ALLDONE_HINT = 7;
  public static final int NC_CHAT_ID_LAST_SPECIAL = 9;

  public static final int NC_CHAT_VISIBILITY_NORMAL = 0;
  public static final int NC_CHAT_VISIBILITY_ARCHIVED = 1;
  public static final int NC_CHAT_VISIBILITY_PINNED = 2;

  private int accountId;

  public NcChat(int accountId, long chatCPtr) {
    this.accountId = accountId;
    this.chatCPtr = chatCPtr;
  }

  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    unrefChatCPtr();
    chatCPtr = 0;
  }

  public int getAccountId() {
    return accountId;
  }

  public native int getId();

  public native int getType();

  public native int getVisibility();

  public native String getName();

  public native String getMailinglistAddr();

  public native String getProfileImage();

  public native int getColor();

  public native boolean isEncrypted();

  public native boolean isUnpromoted();

  public native boolean isSelfTalk();

  public native boolean isDeviceTalk();

  public native boolean canSend();

  public native boolean isSendingLocations();

  public native boolean isMuted();

  public native boolean isContactRequest();

  // aliases and higher-level tools

  public boolean isMultiUser() {
    int type = getType();
    return type != NC_CHAT_TYPE_SINGLE;
  }

  public boolean shallLeaveBeforeDelete(NcContext dcContext) {
    if (isInBroadcast()) {
      final int[] members = ncContext.getChatContacts(getId());
      return Util.contains(members, NcContact.NC_CONTACT_ID_SELF);
    } else if (isMultiUser() && isEncrypted() && canSend() && !isOutBroadcast()) {
      return true;
    }
    return false;
  }

  public boolean isMailingList() {
    return getType() == NC_CHAT_TYPE_MAILINGLIST;
  }

  public boolean isInBroadcast() {
    return getType() == NC_CHAT_TYPE_IN_BROANCAST;
  }

  public boolean isOutBroadcast() {
    return getType() == NC_CHAT_TYPE_OUT_BROANCAST;
  }

  // working with raw c-data

  private long chatCPtr; // CAVE: the name is referenced in the JNI

  private native void unrefChatCPtr();

  public long getChatCPtr() {
    return chatCPtr;
  }
}

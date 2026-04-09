package com.b44t.messenger;

import android.text.TextUtils;
import java.io.File;
import java.util.Set;
import org.json.JSONObject;

public class NcMsg {

  public static final int NC_MSG_UNDEFINED = 0;
  public static final int NC_MSG_TEXT = 10;
  public static final int NC_MSG_IMAGE = 20;
  public static final int NC_MSG_GIF = 21;
  public static final int NC_MSG_STICKER = 23;
  public static final int NC_MSG_AUDIO = 40;
  public static final int NC_MSG_VOICE = 41;
  public static final int NC_MSG_VIDEO = 50;
  public static final int NC_MSG_FILE = 60;
  public static final int NC_MSG_CALL = 71;
  public static final int NC_MSG_WEBXNC = 80;
  public static final int NC_MSG_VCARD = 90;

  public static final int NC_INFO_UNKNOWN = 0;
  public static final int NC_INFO_GROUP_NAME_CHANGED = 2;
  public static final int NC_INFO_GROUP_IMAGE_CHANGED = 3;
  public static final int NC_INFO_MEMBER_ADDED_TO_GROUP = 4;
  public static final int NC_INFO_MEMBER_REMOVED_FROM_GROUP = 5;
  public static final int NC_INFO_SECURE_JOIN_MESSAGE = 7;
  public static final int NC_INFO_LOCATIONSTREAMING_ENABLED = 8;
  public static final int NC_INFO_LOCATION_ONLY = 9;
  public static final int NC_INFO_EPHEMERAL_TIMER_CHANGED = 10;
  public static final int NC_INFO_PROTECTION_ENABLED = 11;
  public static final int NC_INFO_INVALID_UNENCRYPTED_MAIL = 13;
  public static final int NC_INFO_WEBXNC_INFO_MESSAGE = 32;
  public static final int NC_INFO_CHAT_E2EE = 50;
  public static final int NC_INFO_CHAT_DESCRIPTION_CHANGED = 70;

  public static final int NC_STATE_UNDEFINED = 0;
  public static final int NC_STATE_IN_FRESH = 10;
  public static final int NC_STATE_IN_NOTICED = 13;
  public static final int NC_STATE_IN_SEEN = 16;
  public static final int NC_STATE_OUT_PREPARING = 18;
  public static final int NC_STATE_OUT_DRAFT = 19;
  public static final int NC_STATE_OUT_PENDING = 20;
  public static final int NC_STATE_OUT_FAILED = 24;
  public static final int NC_STATE_OUT_DELIVERED = 26;
  public static final int NC_STATE_OUT_MDN_RCVD = 28;

  public static final int NC_DOWNLOAD_DONE = 0;
  public static final int NC_DOWNLOAD_AVAILABLE = 10;
  public static final int NC_DOWNLOAD_FAILURE = 20;
  public static final int NC_DOWNLOAD_UNDECIPHERABLE = 30;
  public static final int NC_DOWNLOAD_IN_PROGRESS = 1000;

  public static final int NC_MSG_NO_ID = 0;
  public static final int NC_MSG_ID_MARKER1 = 1;
  public static final int NC_MSG_ID_DAYMARKER = 9;

  public static final int NC_VIDEOCHATTYPE_UNKNOWN = 0;
  public static final int NC_VIDEOCHATTYPE_BASICWEBRTC = 1;

  private static final String TAG = NcMsg.class.getSimpleName();

  public NcMsg(NcContext context, int viewtype) {
    msgCPtr = context.createMsgCPtr(viewtype);
  }

  public NcMsg(long msgCPtr) {
    this.msgCPtr = msgCPtr;
  }

  public boolean isOk() {
    return msgCPtr != 0;
  }

  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    unrefMsgCPtr();
    msgCPtr = 0;
  }

  @Override
  public int hashCode() {
    return this.getId();
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof NcMsg)) {
      return false;
    }

    NcMsg that = (NcMsg) other;
    return this.getId() == that.getId() && this.getId() != 0;
  }

  /** If given a message, calculates the position of the message in the chat */
  public static int getMessagePosition(NcMsg msg, NcContext ncContext) {
    int msgs[] = ncContext.getChatMsgs(msg.getChatId(), 0, 0);
    int startingPosition = -1;
    int msgId = msg.getId();
    for (int i = 0; i < msgs.length; i++) {
      if (msgs[i] == msgId) {
        startingPosition = msgs.length - 1 - i;
        break;
      }
    }
    return startingPosition;
  }

  public native int getId();

  public native String getText();

  public native String getSubject();

  public native long getTimestamp();

  public native long getSortTimestamp();

  public native boolean hasDeviatingTimestamp();

  public native boolean hasLocation();

  public native int getType();

  public native int getInfoType();

  public native int getInfoContactId();

  public native int getState();

  public native int getDownloadState();

  public native int getChatId();

  public native int getFromId();

  public native int getWidth(int def);

  public native int getHeight(int def);

  public native int getDuration();

  public native void lateFilingMediaSize(int width, int height, int duration);

  public NcLot getSummary(NcChat chat) {
    return new NcLot(getSummaryCPtr(chat.getChatCPtr()));
  }

  public native String getSummarytext(int approx_characters);

  public native int showPadlock();

  public boolean hasFile() {
    String file = getFile();
    return file != null && !file.isEmpty();
  }

  public native String getFile();

  public native String getFilemime();

  public native String getFilename();

  public native long getFilebytes();

  public native byte[] getWebxncBlob(String filename);

  public JSONObject getWebxncInfo() {
    try {
      String json = getWebxncInfoJson();
      if (json != null && !json.isEmpty()) return new JSONObject(json);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return new JSONObject();
  }

  public native String getWebxncHref();

  public native boolean isForwarded();

  public native boolean isInfo();

  public native boolean hasHtml();

  public native void setText(String text);

  public native void setFileAndDeduplicate(String file, String name, String filemime);

  public native void setDimension(int width, int height);

  public native void setDuration(int duration);

  public native void setLocation(float latitude, float longitude);

  public void setQuote(NcMsg quote) {
    setQuoteCPtr(quote.msgCPtr);
  }

  public native String getQuotedText();

  public native String getError();

  public native String getOverrideSenderName();

  public native boolean isEdited();

  public String getSenderName(NcContact ncContact) {
    String overrideName = getOverrideSenderName();
    if (overrideName != null) {
      return "~" + overrideName;
    } else {
      return ncContact.getDisplayName();
    }
  }

  public NcMsg getQuotedMsg() {
    long cPtr = getQuotedMsgCPtr();
    return cPtr != 0 ? new NcMsg(cPtr) : null;
  }

  public NcMsg getParent() {
    long cPtr = getParentCPtr();
    return cPtr != 0 ? new NcMsg(cPtr) : null;
  }

  public native int getOriginalMsgId();

  public native int getSavedMsgId();

  public boolean canSave() {
    // saving info-messages out of context results in confusion, see
    // https://github.com/deltachat/deltachat-ios/issues/2567
    return !isInfo();
  }

  public File getFileAsFile() {
    if (getFile() == null) throw new AssertionError("expected a file to be present.");
    return new File(getFile());
  }

  // aliases and higher-level tools
  public static int[] msgSetToIds(final Set<NcMsg> ncMsgs) {
    if (ncMsgs == null) {
      return new int[0];
    }
    int[] ids = new int[ncMsgs.size()];
    int i = 0;
    for (NcMsg ncMsg : ncMsgs) {
      ids[i++] = ncMsg.getId();
    }
    return ids;
  }

  public boolean isOutgoing() {
    return getFromId() == NcContact.NC_CONTACT_ID_SELF;
  }

  public String getDisplayBody() {
    return getText();
  }

  public String getBody() {
    return getText();
  }

  public long getDateReceived() {
    return getTimestamp();
  }

  public boolean isFailed() {
    return (getState() == NC_STATE_OUT_FAILED) || (!TextUtils.isEmpty(getError()));
  }

  public boolean isPreparing() {
    return getState() == NC_STATE_OUT_PREPARING;
  }

  public boolean isSecure() {
    return showPadlock() != 0;
  }

  public boolean isPending() {
    return getState() == NC_STATE_OUT_PENDING;
  }

  public boolean isDelivered() {
    return getState() == NC_STATE_OUT_DELIVERED;
  }

  public boolean isRemoteRead() {
    return getState() == NC_STATE_OUT_MDN_RCVD;
  }

  public boolean isSeen() {
    return getState() == NC_STATE_IN_SEEN;
  }

  // working with raw c-data
  private long msgCPtr; // CAVE: the name is referenced in the JNI

  private native void unrefMsgCPtr();

  private native long getSummaryCPtr(long chatCPtr);

  private native void setQuoteCPtr(long quoteCPtr);

  private native long getQuotedMsgCPtr();

  private native long getParentCPtr();

  private native String getWebxncInfoJson();
}
;

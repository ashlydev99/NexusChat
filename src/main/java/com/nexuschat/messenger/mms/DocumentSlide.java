package com.nexuschat.messenger.mms;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.b44t.messenger.NcMsg;
import com.nexuschat.messenger.attachments.NcAttachment;
import com.nexuschat.messenger.util.StorageUtil;

public class DocumentSlide extends Slide {
  private int ncMsgType = NcMsg.NC_MSG_UNDEFINED;

  public DocumentSlide(Context context, NcMsg ncMsg) {
    super(context, new NcAttachment(ncMsg));
    ncMsgId = ncMsg.getId();
    ncMsgType = ncMsg.getType();
  }

  public DocumentSlide(
      @NonNull Context context,
      @NonNull Uri uri,
      @NonNull String contentType,
      long size,
      @Nullable String fileName) {
    super(
        context,
        constructAttachmentFromUri(
            context,
            uri,
            contentType,
            size,
            0,
            0,
            uri,
            StorageUtil.getCleanFileName(fileName),
            false));
  }

  @Override
  public boolean hasDocument() {
    return true;
  }

  @Override
  public boolean isWebxdcDocument() {
    return ncMsgType == NcMsg.NC_MSG_WEBXDC;
  }
}

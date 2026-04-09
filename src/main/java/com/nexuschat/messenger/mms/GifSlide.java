package com.nexuschat.messenger.mms;

import android.content.Context;
import android.net.Uri;
import com.b44t.messenger.NcMsg;
import com.nexuschat.messenger.util.MediaUtil;

public class GifSlide extends ImageSlide {

  public GifSlide(Context context, NcMsg ncMsg) {
    super(context, ncMsg);
  }

  public GifSlide(Context context, Uri uri, String fileName, long size, int width, int height) {
    super(
        context,
        constructAttachmentFromUri(
            context, uri, MediaUtil.IMAGE_GIF, size, width, height, uri, fileName, false));
  }
}

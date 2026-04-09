/**
 * Copyright (C) 2011 Whisper Systems
 *
 * <p>This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 */
package com.nexuschat.messenger.mms;

import android.content.Context;
import android.net.Uri;
import com.b44t.messenger.NcMsg;
import java.io.File;
import com.nexuschat.messenger.attachments.Attachment;
import com.nexuschat.messenger.attachments.NcAttachment;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.util.MediaUtil;

public class VideoSlide extends Slide {

  private static Attachment constructVideoAttachment(
      Context context, Uri uri, String fileName, long dataSize) {
    Uri thumbnailUri =
        Uri.fromFile(
            new File(NcHelper.getBlobdirFile(NcHelper.getContext(context), "temp-preview.jpg")));
    MediaUtil.ThumbnailSize retWh = new MediaUtil.ThumbnailSize(0, 0);
    MediaUtil.createVideoThumbnailIfNeeded(context, uri, thumbnailUri, retWh);
    return constructAttachmentFromUri(
        context,
        uri,
        MediaUtil.VIDEO_UNSPECIFIED,
        dataSize,
        retWh.width,
        retWh.height,
        thumbnailUri,
        fileName,
        false);
  }

  public VideoSlide(Context context, Uri uri, String fileName, long dataSize) {
    super(context, constructVideoAttachment(context, uri, fileName, dataSize));
  }

  public VideoSlide(Context context, NcMsg ncMsg) {
    super(context, new NcAttachment(ncMsg));
    ncMsgId = ncMsg.getId();
  }

  @Override
  public boolean hasPlayOverlay() {
    return true;
  }

  @Override
  public boolean hasImage() {
    return true;
  }

  @Override
  public boolean hasVideo() {
    return true;
  }
}

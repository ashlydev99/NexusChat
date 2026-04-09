/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nexuschat.messenger.mms;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.b44t.messenger.NcMsg;
import com.nexuschat.messenger.attachments.Attachment;
import com.nexuschat.messenger.attachments.NcAttachment;
import com.nexuschat.messenger.util.MediaUtil;

public class ImageSlide extends Slide {

  public ImageSlide(@NonNull Context context, @NonNull NcMsg ncMsg) {
    super(context, new NcAttachment(ncMsg));
    ncMsgId = ncMsg.getId();
  }

  public ImageSlide(@NonNull Context context, @NonNull Attachment attachment) {
    super(context, attachment);
  }

  public ImageSlide(Context context, Uri uri, String fileName, long size, int width, int height) {
    super(
        context,
        constructAttachmentFromUri(
            context, uri, MediaUtil.IMAGE_JPEG, size, width, height, uri, fileName, false));
  }

  @Override
  public @Nullable Uri getThumbnailUri() {
    return getUri();
  }

  @Override
  public boolean hasImage() {
    return true;
  }
}

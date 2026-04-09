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
import androidx.annotation.Nullable;
import com.b44t.messenger.NcMsg;
import com.nexuschat.messenger.attachments.NcAttachment;
import com.nexuschat.messenger.attachments.UriAttachment;
import com.nexuschat.messenger.database.AttachmentDatabase;
import com.nexuschat.messenger.util.MediaUtil;
import com.nexuschat.messenger.util.StorageUtil;

public class AudioSlide extends Slide {

  public AudioSlide(Context context, NcMsg ncMsg) {
    super(context, new NcAttachment(ncMsg));
    ncMsgId = ncMsg.getId();
  }

  public AudioSlide(Context context, Uri uri, long dataSize, boolean voiceNote, String fileName) {
    super(
        context,
        constructAttachmentFromUri(
            context,
            uri,
            MediaUtil.AUDIO_UNSPECIFIED,
            dataSize,
            0,
            0,
            null,
            StorageUtil.getCleanFileName(fileName),
            voiceNote));
  }

  public AudioSlide(
      Context context, Uri uri, long dataSize, String contentType, boolean voiceNote) {
    super(
        context,
        new UriAttachment(
            uri,
            null,
            contentType,
            AttachmentDatabase.TRANSFER_PROGRESS_STARTED,
            dataSize,
            0,
            0,
            null,
            null,
            voiceNote));
  }

  @Override
  public boolean equals(Object other) {
    if (other == null) return false;
    if (!(other instanceof AudioSlide)) return false;
    return this.getNcMsgId() == ((AudioSlide) other).getNcMsgId();
  }

  @Override
  @Nullable
  public Uri getThumbnailUri() {
    return null;
  }

  @Override
  public boolean hasAudio() {
    return true;
  }
}

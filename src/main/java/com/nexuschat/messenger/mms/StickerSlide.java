package com.nexuschat.messenger.mms;

import android.content.Context;
import androidx.annotation.NonNull;
import com.b44t.messenger.NcMsg;
import com.nexuschat.messenger.attachments.NcAttachment;

public class StickerSlide extends Slide {

  public StickerSlide(@NonNull Context context, @NonNull NcMsg dcMsg) {
    super(context, new NcAttachment(ncMsg));
  }

  @Override
  public boolean hasSticker() {
    return true;
  }
}

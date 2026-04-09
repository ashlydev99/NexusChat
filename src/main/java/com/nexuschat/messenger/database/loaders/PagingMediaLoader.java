package com.nexuschat.messenger.database.loaders;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcMediaGalleryElement;
import com.b44t.messenger.NcMsg;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.util.AsyncLoader;

public class PagingMediaLoader extends AsyncLoader<NcMediaGalleryElement> {

  private static final String TAG = PagingMediaLoader.class.getSimpleName();

  private final NcMsg msg;
  private final boolean leftIsRecent;

  public PagingMediaLoader(@NonNull Context context, @NonNull NcMsg msg, boolean leftIsRecent) {
    super(context);
    this.msg = msg;
    this.leftIsRecent = leftIsRecent;
  }

  @Nullable
  @Override
  public NcMediaGalleryElement loadInBackground() {
    NcContext context = NcHelper.getContext(getContext());
    int[] mediaMessages =
        context.getChatMedia(
            msg.getChatId(), NcMsg.NC_MSG_IMAGE, NcMsg.NC_MSG_GIF, NcMsg.NC_MSG_VIDEO);
    // first id is the oldest message.
    int currentIndex = -1;
    for (int ii = 0; ii < mediaMessages.length; ii++) {
      if (mediaMessages[ii] == msg.getId()) {
        currentIndex = ii;
        break;
      }
    }
    if (currentIndex == -1) {
      currentIndex = 0;
      NcMsg unfound = context.getMsg(msg.getId());
      Log.e(
          TAG,
          "did not find message in list: "
              + unfound.getId()
              + " / "
              + unfound.getFile()
              + " / "
              + unfound.getText());
    }
    return new NcMediaGalleryElement(mediaMessages, currentIndex, context, leftIsRecent);
  }
}

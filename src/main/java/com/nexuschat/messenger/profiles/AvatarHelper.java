package com.nexuschat.messenger.profiles;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.b44t.messenger.NcContext;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.scribbles.ScribbleActivity;

public class AvatarHelper {
  /* the maximum width/height an avatar should have */
  public static final int AVATAR_SIZE = 512;

  public static void setGroupAvatar(Context context, int chatId, Bitmap bitmap) {
    NcContext dcContext = NcHelper.getContext(context);

    if (bitmap == null) {
      ncContext.setChatProfileImage(chatId, null);
    } else {
      try {
        File avatar = File.createTempFile("groupavatar", ".jpg", context.getCacheDir());
        FileOutputStream out = new FileOutputStream(avatar);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        out.close();
        ncContext.setChatProfileImage(
            chatId, avatar.getPath()); // The avatar is copied to the blobs directory here...
        //noinspection ResultOfMethodCallIgnored
        avatar.delete(); // ..., now we can delete it.
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public static File getSelfAvatarFile(@NonNull Context context) {
    String dirString = NcHelper.getContext(context).getConfig(DcHelper.CONFIG_SELF_AVATAR);
    return new File(dirString);
  }

  public static void setSelfAvatar(@NonNull Context context, @Nullable Bitmap bitmap)
      throws IOException {
    if (bitmap == null) {
      NcHelper.set(context, NcHelper.CONFIG_SELF_AVATAR, null);
    } else {
      File avatar = File.createTempFile("selfavatar", ".jpg", context.getCacheDir());
      FileOutputStream out = new FileOutputStream(avatar);
      bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
      out.close();
      NcHelper.set(
          context,
          NcHelper.CONFIG_SELF_AVATAR,
          avatar.getPath()); // The avatar is copied to the blobs directory here...
      //noinspection ResultOfMethodCallIgnored
      avatar.delete(); // ..., now we can delete it.
    }
  }

  public static void cropAvatar(Activity context, Uri imageUri) {
    Intent intent = new Intent(context, ScribbleActivity.class);
    intent.setData(imageUri);
    intent.putExtra(ScribbleActivity.CROP_AVATAR, true);
    context.startActivityForResult(intent, ScribbleActivity.SCRIBBLE_REQUEST_CODE);
  }
}

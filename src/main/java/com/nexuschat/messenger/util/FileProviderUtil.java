package com.nexuschat.messenger.util;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import java.io.File;
import com.nexuschat.messenger.BuildConfig;

public class FileProviderUtil {

  private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".fileprovider";

  public static Uri getUriFor(@NonNull Context context, @NonNull File file)
      throws IllegalStateException, NullPointerException {
    return FileProvider.getUriForFile(context, AUTHORITY, file);
  }
}

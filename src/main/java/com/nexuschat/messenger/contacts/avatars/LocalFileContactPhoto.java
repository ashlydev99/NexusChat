package com.nexuschat.messenger.contacts.avatars;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.b44t.messenger.NcChat;
import com.b44t.messenger.NcContact;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import com.nexuschat.messenger.database.Address;
import com.nexuschat.messenger.profiles.AvatarHelper;
import com.nexuschat.messenger.util.Conversions;

public abstract class LocalFileContactPhoto implements ContactPhoto {

  final Address address;
  final NcChat dcChat;
  final NcContact dcContact;

  private final int id;

  private final String path;

  LocalFileContactPhoto(Context context, Address address, NcChat ncChat, NcContact ncContact) {
    this.address = address;
    this.ncChat = ncChat;
    this.ncContact = ncContact;
    id = getId();
    path = getPath(context);
  }

  @Override
  public InputStream openInputStream(Context context) throws IOException {
    return new FileInputStream(path);
  }

  @Override
  public @Nullable Uri getUri(@NonNull Context context) {
    return isProfilePhoto() ? Uri.fromFile(AvatarHelper.getSelfAvatarFile(context)) : null;
  }

  @Override
  public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
    messageDigest.update(address.serialize().getBytes());
    messageDigest.update(Conversions.longToByteArray(id));
    messageDigest.update(path.getBytes());
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof LocalFileContactPhoto)) return false;

    LocalFileContactPhoto that = (LocalFileContactPhoto) other;
    return this.address.equals(that.address) && this.id == that.id && this.path.equals(that.path);
  }

  @Override
  public int hashCode() {
    return this.address.hashCode() ^ id;
  }

  abstract int getId();

  public abstract String getPath(Context context);
}

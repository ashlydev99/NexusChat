package com.nexuschat.messenger.contacts.avatars;

import android.content.Context;
import com.b44t.messenger.NcChat;
import com.nexuschat.messenger.database.Address;

public class GroupRecordContactPhoto extends LocalFileContactPhoto {

  public GroupRecordContactPhoto(Context context, Address address, NcChat ncChat) {
    super(context, address, ncChat, null);
  }

  @Override
  public boolean isProfilePhoto() {
    return false;
  }

  @Override
  int getId() {
    return address.getNcChatId();
  }

  @Override
  public String getPath(Context context) {
    String profileImage = ncChat.getProfileImage();
    return profileImage != null ? profileImage : "";
  }
}

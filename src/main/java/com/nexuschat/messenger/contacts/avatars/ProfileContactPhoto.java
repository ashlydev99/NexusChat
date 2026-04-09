package com.nexuschat.messenger.contacts.avatars;

import android.content.Context;
import com.b44t.messenger.NcContact;
import com.nexuschat.messenger.database.Address;

public class ProfileContactPhoto extends LocalFileContactPhoto {

  public ProfileContactPhoto(Context context, Address address, NcContact ncContact) {
    super(context, address, null, ncContact);
  }

  @Override
  public boolean isProfilePhoto() {
    return true;
  }

  @Override
  int getId() {
    return address.getNcContactId();
  }

  @Override
  public String getPath(Context context) {
    String profileImage = ncContact.getProfileImage();
    return profileImage != null ? profileImage : "";
  }
}

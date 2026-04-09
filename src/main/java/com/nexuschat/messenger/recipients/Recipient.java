/*
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013 - 2017 Open Whisper Systems
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
package com.nexuschat.messenger.recipients;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import chat.delta.rpc.types.VcardContact;
import com.b44t.messenger.NcChat;
import com.b44t.messenger.NcContact;
import com.b44t.messenger.NcContext;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.contacts.avatars.ContactPhoto;
import com.nexuschat.messenger.contacts.avatars.GeneratedContactPhoto;
import com.nexuschat.messenger.contacts.avatars.GroupRecordContactPhoto;
import com.nexuschat.messenger.contacts.avatars.LocalFileContactPhoto;
import com.nexuschat.messenger.contacts.avatars.ProfileContactPhoto;
import com.nexuschat.messenger.contacts.avatars.SystemContactPhoto;
import com.nexuschat.messenger.contacts.avatars.VcardContactPhoto;
import com.nexuschat.messenger.database.Address;
import com.nexuschat.messenger.util.Hash;
import com.nexuschat.messenger.util.Prefs;
import com.nexuschat.messenger.util.Util;

public class Recipient {

  private final Set<RecipientModifiedListener> listeners =
      Collections.newSetFromMap(new WeakHashMap<RecipientModifiedListener, Boolean>());

  private final @NonNull Address address;

  private final @Nullable String customLabel;

  private @Nullable Uri systemContactPhoto;
  private final Uri contactUri;

  private final @Nullable String profileName;
  private @Nullable String profileAvatar;

  private final @Nullable NcChat ncChat;
  private @Nullable NcContact ncContact;
  private final @Nullable VcardContact vContact;

  public static @NonNull Recipient fromChat(@NonNull Context context, int ncMsgId) {
    NcContext ncContext = NcHelper.getContext(context);
    return new Recipient(context, ncContext.getChat(ncContext.getMsg(ncMsgId).getChatId()));
  }

  @SuppressWarnings("ConstantConditions")
  public static @NonNull Recipient from(@NonNull Context context, @NonNull Address address) {
    if (address == null) throw new AssertionError(address);
    NcContext ncContext = NcHelper.getContext(context);
    if (address.isNcContact()) {
      return new Recipient(context, ncContext.getContact(address.getNcContactId()));
    } else if (address.isNcChat()) {
      return new Recipient(context, ncContext.getChat(address.getNcChatId()));
    } else if (NcHelper.getContext(context).mayBeValidAddr(address.toString())) {
      int contactId = ncContext.lookupContactIdByAddr(address.toString());
      if (contactId != 0) {
        return new Recipient(context, ncContext.getContact(contactId));
      }
    }
    return new Recipient(context, ncContext.getContact(0));
  }

  public Recipient(@NonNull Context context, @NonNull NcChat ncChat) {
    this(context, ncChat, null, null, null);
  }

  public Recipient(@NonNull Context context, @NonNull VcardContact vContact) {
    this(context, null, null, null, vContact);
  }

  public Recipient(@NonNull Context context, @NonNull NcContact ncContact) {
    this(context, null, ncContact, null, null);
  }

  public Recipient(
      @NonNull Context context, @NonNull NcContact ncContact, @NonNull String profileName) {
    this(context, null, ncContact, profileName, null);
  }

  private Recipient(
      @NonNull Context context,
      @Nullable NcChat ncChat,
      @Nullable NcContact ncContact,
      @Nullable String profileName,
      @Nullable VcardContact vContact) {
    this.ncChat = ncChat;
    this.ncContact = ncContact;
    this.profileName = profileName;
    this.vContact = vContact;
    this.contactUri = null;
    this.systemContactPhoto = null;
    this.customLabel = null;
    this.profileAvatar = null;

    if (ncContact != null) {
      this.address = Address.fromContact(ncContact.getId());
      maybeSetSystemContactPhoto(context, ncContact);
      if (ncContact.getId() == NcContact.NC_CONTACT_ID_SELF) {
        setProfileAvatar("SELF");
      }
    } else if (ncChat != null) {
      int chatId = ncChat.getId();
      this.address = Address.fromChat(chatId);
      if (!ncChat.isMultiUser()) {
        NcContext ncContext = NcHelper.getAccounts(context).getAccount(ncChat.getAccountId());
        int[] contacts = ncContext.getChatContacts(chatId);
        if (contacts.length >= 1) {
          this.ncContact = ncContext.getContact(contacts[0]);
          maybeSetSystemContactPhoto(context, this.ncContact);
        }
      }
    } else {
      this.address = Address.UNKNOWN;
    }
  }

  public @Nullable String getName() {
    if (ncChat != null) {
      return ncChat.getName();
    } else if (ncContact != null) {
      return ncContact.getDisplayName();
    } else if (vContact != null) {
      return vContact.displayName;
    }
    return "";
  }

  public @Nullable NcContact getNcContact() {
    return ncContact;
  }

  public @NonNull Address getAddress() {
    return address;
  }

  public @Nullable String getProfileName() {
    return profileName;
  }

  public void setProfileAvatar(@Nullable String profileAvatar) {
    synchronized (this) {
      this.profileAvatar = profileAvatar;
    }

    notifyListeners();
  }

  public boolean isMultiUserRecipient() {
    return ncChat != null && ncChat.isMultiUser();
  }

  public synchronized void addListener(RecipientModifiedListener listener) {
    listeners.add(listener);
  }

  public synchronized void removeListener(RecipientModifiedListener listener) {
    listeners.remove(listener);
  }

  public synchronized String toShortString() {
    return getName();
  }

  public int getFallbackAvatarColor() {
    int rgb = 0x00808080;
    if (ncChat != null) {
      rgb = ncChat.getColor();
    } else if (ncContact != null) {
      rgb = ncContact.getColor();
    } else if (vContact != null) {
      rgb = Color.parseColor(vContact.color);
    }
    return Color.argb(0xFF, Color.red(rgb), Color.green(rgb), Color.blue(rgb));
  }

  public synchronized @NonNull Drawable getFallbackAvatarDrawable(Context context) {
    return getFallbackAvatarDrawable(context, true);
  }

  public synchronized @NonNull Drawable getFallbackAvatarDrawable(
      Context context, boolean roundShape) {
    return getFallbackContactPhoto().asDrawable(context, getFallbackAvatarColor(), roundShape);
  }

  public synchronized @NonNull GeneratedContactPhoto getFallbackContactPhoto() {
    String name = getName();
    if (!TextUtils.isEmpty(profileName)) return new GeneratedContactPhoto(profileName);
    else if (!TextUtils.isEmpty(name)) return new GeneratedContactPhoto(name);
    else return new GeneratedContactPhoto("#");
  }

  public synchronized @Nullable ContactPhoto getContactPhoto(Context context) {
    LocalFileContactPhoto contactPhoto = null;
    if (ncChat != null) {
      contactPhoto = new GroupRecordContactPhoto(context, address, ncChat);
    } else if (ncContact != null) {
      contactPhoto = new ProfileContactPhoto(context, address, ncContact);
    }

    if (contactPhoto != null) {
      String path = contactPhoto.getPath(context);
      if (path != null && !path.isEmpty()) {
        return contactPhoto;
      }
    }

    if (vContact != null && vContact.profileImage != null) {
      return new VcardContactPhoto(vContact);
    }

    if (systemContactPhoto != null) {
      return new SystemContactPhoto(address, systemContactPhoto, 0);
    }

    return null;
  }

  private void maybeSetSystemContactPhoto(@NonNull Context context, NcContact contact) {
    String identifier = Hash.sha256(contact.getDisplayName() + contact.getAddr());
    Uri systemContactPhoto = Prefs.getSystemContactPhoto(context, identifier);
    if (systemContactPhoto != null) {
      setSystemContactPhoto(systemContactPhoto);
    }
  }

  private void setSystemContactPhoto(@Nullable Uri systemContactPhoto) {
    boolean notify = false;

    synchronized (this) {
      if (!Util.equals(systemContactPhoto, this.systemContactPhoto)) {
        this.systemContactPhoto = systemContactPhoto;
        notify = true;
      }
    }

    if (notify) notifyListeners();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Recipient)) return false;

    Recipient that = (Recipient) o;

    return this.address.equals(that.address);
  }

  @Override
  public int hashCode() {
    return this.address.hashCode();
  }

  private void notifyListeners() {
    Set<RecipientModifiedListener> localListeners;

    synchronized (this) {
      localListeners = new HashSet<>(listeners);
    }

    for (RecipientModifiedListener listener : localListeners) listener.onModified(this);
  }

  public NcChat getChat() {
    return ncChat != null ? ncChat : new NcChat(0, 0);
  }

  @NonNull
  @Override
  public String toString() {
    return "Recipient{"
        + "listeners="
        + listeners
        + ", address="
        + address
        + ", customLabel='"
        + customLabel
        + '\''
        + ", systemContactPhoto="
        + systemContactPhoto
        + ", contactUri="
        + contactUri
        + ", profileName='"
        + profileName
        + '\''
        + ", profileAvatar='"
        + profileAvatar
        + '\''
        + '}';
  }
}

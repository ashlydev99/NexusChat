package com.nexuschat.messenger;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.b44t.messenger.NcChat;
import com.b44t.messenger.NcContact;
import com.nexuschat.messenger.components.AvatarView;
import com.nexuschat.messenger.mms.GlideRequests;
import com.nexuschat.messenger.recipients.Recipient;
import com.nexuschat.messenger.recipients.RecipientModifiedListener;
import com.nexuschat.messenger.util.Util;
import com.nexuschat.messenger.util.ViewUtil;

public class ProfileAvatarItem extends LinearLayout implements RecipientModifiedListener {

  private AvatarView avatarView;
  private TextView nameView;
  private TextView subtitleView;

  private Recipient recipient;
  private GlideRequests glideRequests;

  public ProfileAvatarItem(Context context) {
    super(context);
  }

  public ProfileAvatarItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    avatarView = findViewById(R.id.avatar);
    nameView = findViewById(R.id.name);
    subtitleView = findViewById(R.id.subtitle);

    ViewUtil.setTextViewGravityStart(nameView, getContext());
  }

  public void set(
      @NonNull GlideRequests glideRequests,
      @Nullable NcChat ncChat,
      @Nullable NcContact ncContact,
      @Nullable int[] members) {
    this.glideRequests = glideRequests;
    int memberCount = members != null ? members.length : 0;

    String name = "";
    String subtitle = null;
    if (ncChat != null) {
      recipient = new Recipient(getContext(), ncChat);
      name = ncChat.getName();

      if (ncChat.isMailingList()) {
        subtitle = ncChat.getMailinglistAddr();
      } else if (ncChat.isOutBroancast()) {
        subtitle =
            getContext()
                .getResources()
                .getQuantityString(R.plurals.n_recipients, memberCount, memberCount);
      } else if (ncChat.getType() == NcChat.NC_CHAT_TYPE_GROUP) {
        if (memberCount > 1 || Util.contains(members, NcContact.NC_CONTACT_ID_SELF)) {
          subtitle =
              getContext()
                  .getResources()
                  .getQuantityString(R.plurals.n_members, memberCount, memberCount);
        }
      }
    } else if (ncContact != null) {
      recipient = new Recipient(getContext(), ncContact);
      name = ncContact.getDisplayName();
    }

    recipient.addListener(this);
    avatarView.setAvatar(glideRequests, recipient, false);
    avatarView.setSeenRecently(ncContact != null && ncContact.wasSeenRecently());

    nameView.setText(name);

    if (subtitle != null) {
      subtitleView.setVisibility(View.VISIBLE);
      subtitleView.setText(subtitle);
    } else {
      subtitleView.setVisibility(View.GONE);
    }
  }

  public void setAvatarClickListener(OnClickListener listener) {
    avatarView.setAvatarClickListener(listener);
  }

  public void unbind(GlideRequests glideRequests) {
    if (recipient != null) {
      recipient.removeListener(this);
      recipient = null;
    }

    avatarView.clear(glideRequests);
  }

  @Override
  public void onModified(final Recipient recipient) {
    if (this.recipient == recipient) {
      Util.runOnMain(
          () -> {
            avatarView.setAvatar(glideRequests, recipient, false);
            NcContact contact = recipient.getNcContact();
            avatarView.setSeenRecently(contact != null && contact.wasSeenRecently());
            nameView.setText(recipient.toShortString());
          });
    }
  }
}

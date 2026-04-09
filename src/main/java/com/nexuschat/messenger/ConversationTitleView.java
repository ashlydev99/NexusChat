package com.nexuschat.messenger;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.b44t.messenger.NcChat;
import com.b44t.messenger.NcContact;
import com.b44t.messenger.NcContext;
import com.nexuschat.messenger.components.AvatarView;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.mms.GlideRequests;
import com.nexuschat.messenger.recipients.Recipient;
import com.nexuschat.messenger.util.Util;
import com.nexuschat.messenger.util.ViewUtil;

public class ConversationTitleView extends RelativeLayout {

  private View content;
  private ImageView back;
  private AvatarView avatar;
  private TextView title;
  private TextView subtitle;
  private ImageView ephemeralIcon;

  public ConversationTitleView(Context context) {
    this(context, null);
  }

  public ConversationTitleView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    this.back = ViewUtil.findById(this, R.id.up_button);
    this.content = ViewUtil.findById(this, R.id.content);
    this.title = ViewUtil.findById(this, R.id.title);
    this.subtitle = ViewUtil.findById(this, R.id.subtitle);
    this.avatar = ViewUtil.findById(this, R.id.avatar);
    this.ephemeralIcon = ViewUtil.findById(this, R.id.ephemeral_icon);

    ViewUtil.setTextViewGravityStart(this.title, getContext());
    ViewUtil.setTextViewGravityStart(this.subtitle, getContext());
  }

  public void setTitle(@NonNull GlideRequests glideRequests, @NonNull NcChat ncChat) {
    final int chatId = ncChat.getId();
    final Context context = getContext();
    final NcContext ncContext = NcHelper.getContext(context);

    // set title and subtitle texts
    title.setText(ncChat.getName());
    String subtitleStr = null;

    boolean isOnline = false;
    int[] chatContacts = ncContext.getChatContacts(chatId);
    if (ncChat.isMailingList()) {
      subtitleStr = context.getString(R.string.mailing_list);
    } else if (ncChat.isInBroancast()) {
      subtitleStr = context.getString(R.string.channel);
    } else if (ncChat.isOutBroancast()) {
      subtitleStr =
          context
              .getResources()
              .getQuantityString(R.plurals.n_recipients, chatContacts.length, chatContacts.length);
    } else if (ncChat.isMultiUser()) {
      if (chatContacts.length > 1 || Util.contains(chatContacts, NcContact.NC_CONTACT_ID_SELF)) {
        subtitleStr =
            context
                .getResources()
                .getQuantityString(R.plurals.n_members, chatContacts.length, chatContacts.length);
      } else {
        subtitleStr = "…";
      }
    } else if (chatContacts.length >= 1) {
      if (ncChat.isSelfTalk()) {
        subtitleStr = context.getString(R.string.chat_self_talk_subtitle);
      } else if (ncChat.isDeviceTalk()) {
        subtitleStr = context.getString(R.string.device_talk_subtitle);
      } else {
        NcContact ncContact = ncContext.getContact(chatContacts[0]);
        if (ncContact.isBot()) {
          subtitleStr = context.getString(R.string.bot);
        } else if (!ncChat.isEncrypted()) {
          subtitleStr = ncContact.getAddr();
        }
        isOnline = ncContact.wasSeenRecently();
      }
    }

    avatar.setAvatar(glideRequests, new Recipient(getContext(), ncChat), false);
    avatar.setSeenRecently(isOnline);
    int imgLeft = ncChat.isMuted() ? R.drawable.ic_volume_off_white_18dp : 0;
    title.setCompoundDrawablesWithIntrinsicBounds(imgLeft, 0, 0, 0);
    if (!TextUtils.isEmpty(subtitleStr)) {
      subtitle.setText(subtitleStr);
      subtitle.setVisibility(View.VISIBLE);
    } else {
      subtitle.setVisibility(View.GONE);
    }
    boolean isEphemeral = ncContext.getChatEphemeralTimer(chatId) != 0;
    ephemeralIcon.setVisibility(isEphemeral ? View.VISIBLE : View.GONE);
  }

  public void setTitle(@NonNull GlideRequests glideRequests, @NonNull NcContact contact) {
    // This function is only called for contacts without a corresponding 1:1 chat.
    // If there is a 1:1 chat, then the overloaded function
    // setTitle(GlideRequests, NcChat, boolean) is called.
    avatar.setAvatar(glideRequests, new Recipient(getContext(), contact), false);
    avatar.setSeenRecently(contact.wasSeenRecently());

    title.setText(contact.getDisplayName());
    subtitle.setText(contact.getAddr());
    subtitle.setVisibility(View.VISIBLE);
  }

  public void setSeenRecently(boolean seenRecently) {
    avatar.setSeenRecently(seenRecently);
  }

  @Override
  public void setOnClickListener(@Nullable OnClickListener listener) {
    this.content.setOnClickListener(listener);
    this.avatar.setAvatarClickListener(listener);
  }

  public void setOnBackClickedListener(@Nullable OnClickListener listener) {
    this.back.setOnClickListener(listener);
  }
}

package com.nexuschat.messenger.accounts;

import static com.nexuschat.messenger.connect.DcHelper.CONFIG_DISPLAY_NAME;
import static com.nexuschat.messenger.connect.DcHelper.CONFIG_PRIVATE_TAG;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.amulyakhare.textdrawable.TextDrawable;
import com.b44t.messenger.DcContact;
import com.b44t.messenger.DcContext;
import com.nexuschat.messenger.R;
import com.nexuschat.messenger.components.AvatarView;
import com.nexuschat.messenger.mms.GlideRequests;
import com.nexuschat.messenger.recipients.Recipient;
import com.nexuschat.messenger.util.ThemeUtil;
import com.nexuschat.messenger.util.ViewUtil;

public class AccountSelectionListItem extends LinearLayout {

  private AvatarView contactPhotoImage;
  private View addrContainer;
  private TextView addrOrTagView;
  private TextView nameView;
  private ImageView unreadIndicator;

  private int accountId;

  public AccountSelectionListItem(Context context) {
    super(context);
  }

  public AccountSelectionListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.contactPhotoImage = findViewById(R.id.contact_photo_image);
    this.addrContainer = findViewById(R.id.addr_container);
    this.addrOrTagView = findViewById(R.id.addr_or_tag);
    this.nameView = findViewById(R.id.name);
    this.unreadIndicator = findViewById(R.id.unread_indicator);

    ViewUtil.setTextViewGravityStart(this.nameView, getContext());
  }

  public void bind(
      @NonNull GlideRequests glideRequests,
      int accountId,
      NcContext dcContext,
      boolean selected,
      AccountSelectionListFragment fragment) {
    this.accountId = accountId;
    NcContact self = null;
    String name;
    String addrOrTag = null;
    int unreadCount = 0;
    boolean isMuted = ncContext.isMuted();

    Recipient recipient;
    if (accountId == NcContact.NC_CONTACT_ID_ADD_ACCOUNT) {
      name = getContext().getString(R.string.add_account);
      recipient = null;
      this.contactPhotoImage.setSeenRecently(false); // hide connectivity dot
    } else {
      self = ncContext.getContact(NcContact.NC_CONTACT_ID_SELF);
      name = ncContext.getConfig(CONFIG_DISPLAY_NAME);
      if (TextUtils.isEmpty(name)) {
        name = self.getAddr();
      }
      addrOrTag = ncContext.getConfig(CONFIG_PRIVATE_TAG);
      unreadCount = ncContext.getFreshMsgs().length;
      recipient = new Recipient(getContext(), self, name);
      this.contactPhotoImage.setConnectivity(ncContext.getConnectivity());
    }
    this.contactPhotoImage.setAvatar(glideRequests, recipient, false);

    nameView.setCompoundDrawablesWithIntrinsicBounds(
        isMuted ? R.drawable.ic_volume_off_grey600_18dp : 0, 0, 0, 0);

    setSelected(selected);
    if (selected) {
      addrOrTagView.setTypeface(null, Typeface.BOLD);
      nameView.setTypeface(null, Typeface.BOLD);
    } else {
      addrOrTagView.setTypeface(null, Typeface.NORMAL);
      nameView.setTypeface(null, Typeface.NORMAL);
    }

    updateUnreadIndicator(unreadCount, isMuted);
    setText(name, addrOrTag);

    if (accountId != NcContact.NC_CONTACT_ID_ADD_ACCOUNT) {
      fragment.registerForContextMenu(this);
    } else {
      fragment.unregisterForContextMenu(this);
    }
  }

  public void unbind(GlideRequests glideRequests) {
    contactPhotoImage.clear(glideRequests);
  }

  private void updateUnreadIndicator(int unreadCount, boolean isMuted) {
    if (unreadCount == 0) {
      unreadIndicator.setVisibility(View.GONE);
    } else {
      final int color =
          getResources()
              .getColor(
                  isMuted
                      ? (ThemeUtil.isDarkTheme(getContext())
                          ? R.color.unread_count_muted_dark
                          : R.color.unread_count_muted)
                      : R.color.unread_count);
      unreadIndicator.setImageDrawable(
          TextDrawable.builder()
              .beginConfig()
              .width(ViewUtil.dpToPx(getContext(), 24))
              .height(ViewUtil.dpToPx(getContext(), 24))
              .textColor(Color.WHITE)
              .bold()
              .endConfig()
              .buildRound(String.valueOf(unreadCount), color));
      unreadIndicator.setVisibility(View.VISIBLE);
    }
  }

  private void setText(String name, String addrOrTag) {
    this.nameView.setText(name == null ? "#" : name);

    if (!TextUtils.isEmpty(addrOrTag)) {
      this.addrOrTagView.setText(addrOrTag);
      this.addrContainer.setVisibility(View.VISIBLE);
    } else {
      this.addrContainer.setVisibility(View.GONE);
    }
  }

  public int getAccountId() {
    return accountId;
  }
}

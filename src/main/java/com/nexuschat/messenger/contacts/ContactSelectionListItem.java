package com.nexuschat.messenger.contacts;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.b44t.messenger.NcContact;
import com.nexuschat.messenger.R;
import com.nexuschat.messenger.components.AvatarView;
import com.nexuschat.messenger.contacts.avatars.ResourceContactPhoto;
import com.nexuschat.messenger.mms.GlideRequests;
import com.nexuschat.messenger.recipients.Recipient;
import com.nexuschat.messenger.recipients.RecipientModifiedListener;
import com.nexuschat.messenger.util.ThemeUtil;
import com.nexuschat.messenger.util.Util;
import com.nexuschat.messenger.util.ViewUtil;

public class ContactSelectionListItem extends LinearLayout implements RecipientModifiedListener {

  private AvatarView avatar;
  private View numberContainer;
  private TextView numberView;
  private TextView nameView;
  private TextView labelView;
  private CheckBox checkBox;

  private int specialId;
  private String name;
  private String number;
  private Recipient recipient;
  private GlideRequests glideRequests;

  public ContactSelectionListItem(Context context) {
    super(context);
  }

  public ContactSelectionListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.avatar = findViewById(R.id.avatar);
    this.numberContainer = findViewById(R.id.number_container);
    this.numberView = findViewById(R.id.number);
    this.labelView = findViewById(R.id.label);
    this.nameView = findViewById(R.id.name);
    this.checkBox = findViewById(R.id.check_box);

    ViewUtil.setTextViewGravityStart(this.nameView, getContext());
  }

  public void set(
      @NonNull GlideRequests glideRequests,
      int specialId,
      NcContact contact,
      String name,
      String number,
      String label,
      boolean multiSelect,
      boolean enabled) {
    this.glideRequests = glideRequests;
    this.specialId = specialId;
    this.name = name;
    this.number = number;

    if (specialId == NcContact.NC_CONTACT_ID_NEW_CLASSIC_CONTACT
        || specialId == NcContact.NC_CONTACT_ID_NEW_GROUP
        || specialId == NcContact.NC_CONTACT_ID_NEW_UNENCRYPTED_GROUP
        || specialId == NcContact.NC_CONTACT_ID_NEW_BROADCAST
        || specialId == NcContact.NC_CONTACT_ID_ADD_MEMBER
        || specialId == NcContact.NC_CONTACT_ID_QR_INVITE) {
      this.nameView.setTypeface(null, Typeface.BOLD);
    } else {
      this.recipient = new Recipient(getContext(), contact);
      this.recipient.addListener(this);
      if (this.recipient.getName() != null) {
        name = this.recipient.getName();
      }
      this.nameView.setTypeface(null, Typeface.NORMAL);
    }
    if (specialId == NcContact.NC_CONTACT_ID_QR_INVITE) {
      this.avatar.setImageDrawable(
          new ResourceContactPhoto(R.drawable.ic_qr_code_24)
              .asDrawable(getContext(), ThemeUtil.getDummyContactColor(getContext())));
    } else {
      this.avatar.setAvatar(glideRequests, recipient, false);
    }
    this.avatar.setSeenRecently(contact != null && contact.wasSeenRecently());

    setText(name, number, label, contact);
    setEnabled(enabled);

    if (multiSelect) this.checkBox.setVisibility(View.VISIBLE);
    else this.checkBox.setVisibility(View.GONE);
  }

  public void setChecked(boolean selected) {
    this.checkBox.setChecked(selected);
  }

  public void unbind(GlideRequests glideRequests) {
    if (recipient != null) {
      recipient.removeListener(this);
      recipient = null;
    }

    avatar.clear(glideRequests);
  }

  private void setText(String name, String number, String label, NcContact contact) {
    this.nameView.setEnabled(true);
    this.nameView.setText(name == null ? "#" : name);

    if (contact != null && contact.isKeyContact()) {
      number = null;
    }

    if (number != null) {
      this.numberView.setText(number);
      this.labelView.setText(label == null ? "" : label);
      this.numberContainer.setVisibility(View.VISIBLE);
    } else {
      this.numberContainer.setVisibility(View.GONE);
    }
  }

  public int getSpecialId() {
    return specialId;
  }

  public String getName() {
    return name;
  }

  public String getNumber() {
    return number;
  }

  public NcContact getNcContact() {
    return recipient.getNcContact();
  }

  public int getContactId() {
    if (recipient.getAddress().isNcContact()) {
      return recipient.getAddress().getNcContactId();
    } else {
      return -1;
    }
  }

  @Override
  public void onModified(final Recipient recipient) {
    if (this.recipient == recipient) {
      Util.runOnMain(
          () -> {
            avatar.setAvatar(glideRequests, recipient, false);
            NcContact contact = recipient.getNcContact();
            avatar.setSeenRecently(contact != null && contact.wasSeenRecently());
            nameView.setText(recipient.toShortString());
          });
    }
  }
}

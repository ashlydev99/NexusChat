package com.nexuschat.messenger.reactions;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.b44t.messenger.NcContact;
import com.nexuschat.messenger.R;
import com.nexuschat.messenger.components.AvatarImageView;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.mms.GlideRequests;
import com.nexuschat.messenger.recipients.Recipient;
import com.nexuschat.messenger.util.ViewUtil;

public class ReactionRecipientItem extends LinearLayout {

  private AvatarImageView contactPhotoImage;
  private TextView nameView;
  private TextView reactionView;

  private int contactId;
  private String reaction;

  public ReactionRecipientItem(Context context) {
    super(context);
  }

  public ReactionRecipientItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.contactPhotoImage = findViewById(R.id.contact_photo_image);
    this.nameView = findViewById(R.id.name);
    this.reactionView = findViewById(R.id.reaction);

    ViewUtil.setTextViewGravityStart(this.nameView, getContext());
  }

  public void bind(@NonNull GlideRequests glideRequests, int contactId, String reaction) {
    this.contactId = contactId;
    this.reaction = reaction;
    NcContact ncContact = NcHelper.getContext(getContext()).getContact(contactId);
    Recipient recipient = new Recipient(getContext(), ncContact);
    this.contactPhotoImage.setAvatar(glideRequests, recipient, false);
    this.reactionView.setText(reaction);
    this.nameView.setText(ncContact.getDisplayName());
  }

  public void unbind(GlideRequests glideRequests) {
    contactPhotoImage.clear(glideRequests);
  }

  public int getContactId() {
    return contactId;
  }

  public String getReaction() {
    return reaction;
  }

  public View getReactionView() {
    return reactionView;
  }
}

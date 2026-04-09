package com.nexuschat.messenger.components;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.nexuschat.messenger.ProfileActivity;
import com.nexuschat.messenger.contacts.avatars.ContactPhoto;
import com.nexuschat.messenger.contacts.avatars.GeneratedContactPhoto;
import com.nexuschat.messenger.mms.GlideRequests;
import com.nexuschat.messenger.recipients.Recipient;
import com.nexuschat.messenger.util.ThemeUtil;

public class AvatarImageView extends AppCompatImageView {

  private OnClickListener listener = null;

  public AvatarImageView(Context context) {
    super(context);
    setScaleType(ScaleType.CENTER_CROP);
  }

  public AvatarImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setScaleType(ScaleType.CENTER_CROP);
  }

  @Override
  public void setOnClickListener(OnClickListener listener) {
    this.listener = listener;
    super.setOnClickListener(listener);
  }

  public void setAvatar(
      @NonNull GlideRequests requestManager,
      @Nullable Recipient recipient,
      boolean quickContactEnabled) {
    if (recipient != null) {
      ContactPhoto contactPhoto = recipient.getContactPhoto(getContext());
      requestManager
          .load(contactPhoto)
          .error(recipient.getFallbackAvatarDrawable(getContext()))
          .diskCacheStrategy(DiskCacheStrategy.NONE)
          .circleCrop()
          .into(this);
      if (quickContactEnabled) {
        setAvatarClickHandler(recipient);
      }
    } else {
      setImageDrawable(
          new GeneratedContactPhoto("+")
              .asDrawable(getContext(), ThemeUtil.getDummyContactColor(getContext())));
      if (listener != null) super.setOnClickListener(listener);
    }
  }

  public void clear(@NonNull GlideRequests glideRequests) {
    glideRequests.clear(this);
  }

  private void setAvatarClickHandler(final Recipient recipient) {
    if (!recipient.isMultiUserRecipient()) {
      super.setOnClickListener(
          v -> {
            if (recipient.getAddress().isDcContact()) {
              Intent intent = new Intent(getContext(), ProfileActivity.class);
              intent.putExtra(
                  ProfileActivity.CONTACT_ID_EXTRA, recipient.getAddress().getDcContactId());
              getContext().startActivity(intent);
            }
          });
    } else {
      super.setOnClickListener(listener);
    }
  }
}

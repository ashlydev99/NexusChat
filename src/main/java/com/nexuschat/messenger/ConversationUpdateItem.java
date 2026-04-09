package com.nexuschat.messenger;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import com.b44t.messenger.NcChat;
import com.b44t.messenger.NcMsg;
import java.io.ByteArrayInputStream;
import java.util.Set;
import org.json.JSONObject;
import com.nexuschat.messenger.components.DeliveryStatusView;
import com.nexuschat.messenger.components.audioplay.AudioPlaybackViewModel;
import com.nexuschat.messenger.components.audioplay.AudioView;
import com.nexuschat.messenger.mms.GlideRequests;
import com.nexuschat.messenger.recipients.Recipient;
import com.nexuschat.messenger.util.JsonUtils;

public class ConversationUpdateItem extends BaseConversationItem {
  private DeliveryStatusView deliveryStatusView;
  private AppCompatImageView appIcon;
  private int textColor;

  public ConversationUpdateItem(Context context) {
    this(context, null);
  }

  public ConversationUpdateItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    initializeAttributes();

    bodyText = findViewById(R.id.conversation_update_body);
    deliveryStatusView = new DeliveryStatusView(findViewById(R.id.delivery_indicator));
    appIcon = findViewById(R.id.app_icon);

    bodyText.setOnLongClickListener(passthroughClickListener);
    bodyText.setOnClickListener(passthroughClickListener);

    // info messages do not contain links but domains (eg. invalid_unencrypted_tap_to_learn_more),
    // however, they should not be linkified to not disturb eg. "Tap to learn more".
    bodyText.setAutoLinkMask(0);
  }

  @Override
  public void bind(
      @NonNull NcMsg messageRecord,
      @NonNull NcChat ncChat,
      @NonNull GlideRequests glideRequests,
      @NonNull Set<NcMsg> batchSelected,
      @NonNull Recipient conversationRecipient,
      boolean pulseUpdate,
      @Nullable AudioPlaybackViewModel playbackViewModel,
      AudioView.OnActionListener audioPlayPauseListener) {
    bindPartial(messageRecord, ncChat, batchSelected, pulseUpdate, conversationRecipient);
    setGenericInfoRecord(messageRecord);
  }

  private void initializeAttributes() {
    final int[] attributes =
        new int[] {
          R.attr.conversation_item_update_text_color,
        };
    final TypedArray attrs = context.obtainStyledAttributes(attributes);

    textColor = attrs.getColor(0, Color.WHITE);
    attrs.recycle();
  }

  @Override
  public void setEventListener(@Nullable EventListener listener) {
    // No events to report yet
  }

  @Override
  public NcMsg getMessageRecord() {
    return messageRecord;
  }

  private void setGenericInfoRecord(NcMsg messageRecord) {
    int infoType = messageRecord.getInfoType();

    if (infoType == NcMsg.NC_INFO_WEBXNC_INFO_MESSAGE) {
      NcMsg parentMsg = messageRecord.getParent();

      // It is possible that we only received an update without the webxnc itself.
      // In this case parentMsg is null and we display update message without the icon.
      if (parentMsg != null) {
        JSONObject info = parentMsg.getWebxncInfo();
        byte[] blob = parentMsg.getWebxncBlob(JsonUtils.optString(info, "icon"));
        if (blob != null) {
          ByteArrayInputStream is = new ByteArrayInputStream(blob);
          Drawable drawable = Drawable.createFromStream(is, "icon");
          appIcon.setImageDrawable(drawable);
          appIcon.setVisibility(VISIBLE);
        } else {
          appIcon.setVisibility(GONE);
        }
      }
    } else {
      appIcon.setVisibility(GONE);
    }

    bodyText.setText(messageRecord.getDisplayBody());
    bodyText.setVisibility(VISIBLE);

    if (messageRecord.isFailed()) deliveryStatusView.setFailed();
    else if (!messageRecord.isOutgoing()) deliveryStatusView.setNone();
    else if (messageRecord.isPreparing()) deliveryStatusView.setPreparing();
    else if (messageRecord.isPending()) deliveryStatusView.setPending();
    else deliveryStatusView.setNone();
  }

  @Override
  public void unbind() {}
}

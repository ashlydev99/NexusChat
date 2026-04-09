package com.nexuschat.messenger;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import chat.nexus.rpc.Rpc;
import com.b44t.messenger.NcChat;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcMsg;
import java.util.HashSet;
import java.util.Set;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.recipients.Recipient;
import com.nexuschat.messenger.util.Util;

public abstract class BaseConversationItem extends LinearLayout
    implements BindableConversationItem {
  static final long PULSE_HIGHLIGHT_MILLIS = 500;

  protected NcMsg messageRecord;
  protected NcChat ncChat;
  protected TextView bodyText;

  protected final Context context;
  protected final NcContext ncContext;
  protected final Rpc rpc;
  protected Recipient conversationRecipient;

  protected @NonNull Set<NcMsg> batchSelected = new HashSet<>();

  protected final PassthroughClickListener passthroughClickListener =
      new PassthroughClickListener();

  public BaseConversationItem(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
    this.ncContext = NcHelper.getContext(context);
    this.rpc = NcHelper.getRpc(context);
  }

  protected void bindPartial(
      @NonNull NcMsg messageRecord,
      @NonNull NcChat ncChat,
      @NonNull Set<NcMsg> batchSelected,
      boolean pulseHighlight,
      @NonNull Recipient conversationRecipient) {
    this.messageRecord = messageRecord;
    this.ncChat = ncChat;
    this.batchSelected = batchSelected;
    this.conversationRecipient = conversationRecipient;
    setInteractionState(messageRecord, pulseHighlight);
  }

  protected void setInteractionState(NcMsg messageRecord, boolean pulseHighlight) {
    if (batchSelected.contains(messageRecord)) {
      setBackgroundResource(R.drawable.conversation_item_background);
      setSelected(true);
    } else if (pulseHighlight) {
      setBackgroundResource(R.drawable.conversation_item_background_animated);
      setSelected(true);
      postDelayed(() -> setSelected(false), PULSE_HIGHLIGHT_MILLIS);
    } else {
      setSelected(false);
    }
  }

  @Override
  public void setOnClickListener(OnClickListener l) {
    super.setOnClickListener(new ClickListener(l));
  }

  protected boolean shouldInterceptClicks(NcMsg messageRecord) {
    return batchSelected.isEmpty()
        && (messageRecord.isFailed()
            || messageRecord.getInfoType() == NcMsg.NC_INFO_CHAT_E2EE
            || messageRecord.getInfoType() == NcMsg.NC_INFO_PROTECTION_ENABLED
            || messageRecord.getInfoType() == NcMsg.NC_INFO_INVALID_UNENCRYPTED_MAIL);
  }

  protected void onAccessibilityClick() {}

  protected class PassthroughClickListener
      implements View.OnLongClickListener, View.OnClickListener {

    @Override
    public boolean onLongClick(View v) {
      if (bodyText.hasSelection()) {
        return false;
      }
      performLongClick();
      return true;
    }

    @Override
    public void onClick(View v) {
      performClick();
    }
  }

  protected class ClickListener implements View.OnClickListener {
    private final OnClickListener parent;

    ClickListener(@Nullable OnClickListener parent) {
      this.parent = parent;
    }

    public void onClick(View v) {
      if (!shouldInterceptClicks(messageRecord) && parent != null) {
        // The click workaround on ConversationItem shall be revised.
        // In fact, it is probably better rethinking accessibility approach for the items.
        if (batchSelected.isEmpty() && Util.isTouchExplorationEnabled(context)) {
          BaseConversationItem.this.onAccessibilityClick();
        }
        parent.onClick(v);
      } else if (messageRecord.isFailed()) {
        View view = View.inflate(context, R.layout.message_details_view, null);
        TextView detailsText = view.findViewById(R.id.details_text);
        detailsText.setText(messageRecord.getError());

        AlertDialog d =
            new AlertDialog.Builder(context)
                .setView(view)
                .setTitle(R.string.error)
                .setPositiveButton(R.string.ok, null)
                .create();
        d.show();
      } else if (messageRecord.getInfoType() == NcMsg.NC_INFO_CHAT_E2EE
          || messageRecord.getInfoType() == NcMsg.NC_INFO_PROTECTION_ENABLED) {
        NcHelper.showProtectionEnabledDialog(context);
      } else if (messageRecord.getInfoType() == NcMsg.NC_INFO_INVALID_UNENCRYPTED_MAIL) {
        NcHelper.showInvalidUnencryptedDialog(context);
      }
    }
  }
}

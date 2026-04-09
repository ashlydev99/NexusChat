/*
 * Copyright (C) 2011 Whisper Systems
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
package com.nexuschat.messenger;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Build;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import chat.nexus.rpc.RpcException;
import chat.nexus.rpc.types.CallInfo;
import chat.nexus.rpc.types.CallState;
import chat.nexus.rpc.types.Reactions;
import chat.nexus.rpc.types.VcardContact;
import com.b44t.messenger.NcChat;
import com.b44t.messenger.NcContact;
import com.b44t.messenger.NcMsg;
import java.util.List;
import java.util.Set;
import com.nexuschat.messenger.calls.CallCoordinator;
import com.nexuschat.messenger.calls.CallUtil;
import com.nexuschat.messenger.components.AvatarImageView;
import com.nexuschat.messenger.components.BorderlessImageView;
import com.nexuschat.messenger.components.CallItemView;
import com.nexuschat.messenger.components.ConversationItemFooter;
import com.nexuschat.messenger.components.ConversationItemThumbnail;
import com.nexuschat.messenger.components.DocumentView;
import com.nexuschat.messenger.components.QuoteView;
import com.nexuschat.messenger.components.VcardView;
import com.nexuschat.messenger.components.WebxncView;
import com.nexuschat.messenger.components.audioplay.AudioPlaybackViewModel;
import com.nexuschat.messenger.components.audioplay.AudioView;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.mms.AudioSlide;
import com.nexuschat.messenger.mms.DocumentSlide;
import com.nexuschat.messenger.mms.GlideApp;
import com.nexuschat.messenger.mms.GlideRequests;
import com.nexuschat.messenger.mms.Slide;
import com.nexuschat.messenger.mms.SlideClickListener;
import com.nexuschat.messenger.mms.SlideDeck;
import com.nexuschat.messenger.mms.StickerSlide;
import com.nexuschat.messenger.mms.VcardSlide;
import com.nexuschat.messenger.reactions.ReactionsConversationView;
import com.nexuschat.messenger.recipients.Recipient;
import com.nexuschat.messenger.util.Linkifier;
import com.nexuschat.messenger.util.LongClickMovementMethod;
import com.nexuschat.messenger.util.MediaUtil;
import com.nexuschat.messenger.util.Util;
import com.nexuschat.messenger.util.ViewUtil;
import com.nexuschat.messenger.util.views.Stub;

/**
 * A view that displays an individual conversation item within a conversation thread. Used by
 * ComposeMessageActivity's ListActivity via a ConversationAdapter.
 *
 * @author Moxie Marlinspike
 */
public class ConversationItem extends BaseConversationItem {
  private static final String TAG = ConversationItem.class.getSimpleName();

  private static final Rect SWIPE_RECT = new Rect();

  private static final int MAX_MEASURE_CALLS = 3;

  private NcContact ncContact;
  // Whether the sender's avatar and name should be shown (usually the case in group threads):
  private boolean showSender;
  private GlideRequests glideRequests;

  protected ViewGroup bodyBubble;
  protected ReactionsConversationView reactionsView;
  protected View replyView;
  protected View jumptoView;
  @Nullable private QuoteView quoteView;
  private ConversationItemFooter footer;
  private TextView groupSender;
  private View groupSenderHolder;
  private AvatarImageView contactPhoto;
  protected ViewGroup contactPhotoHolder;
  private ViewGroup container;
  private Button msgActionButton;
  private Button showFullButton;

  private @NonNull Stub<ConversationItemThumbnail> mediaThumbnailStub;
  private @NonNull Stub<AudioView> audioViewStub;
  private @NonNull Stub<DocumentView> documentViewStub;
  private @NonNull Stub<WebxncView> webxncViewStub;
  private Stub<BorderlessImageView> stickerStub;
  private Stub<VcardView> vcardViewStub;
  private Stub<CallItemView> callViewStub;
  private @Nullable EventListener eventListener;

  private int measureCalls;

  private int incomingBubbleColor;
  private int outgoingBubbleColor;

  public ConversationItem(Context context) {
    this(context, null);
  }

  public ConversationItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    initializeAttributes();

    this.bodyText = findViewById(R.id.conversation_item_body);
    this.footer = findViewById(R.id.conversation_item_footer);
    this.reactionsView = findViewById(R.id.reactions_view);
    this.groupSender = findViewById(R.id.group_message_sender);
    this.contactPhoto = findViewById(R.id.contact_photo);
    this.contactPhotoHolder = findViewById(R.id.contact_photo_container);
    this.bodyBubble = findViewById(R.id.body_bubble);
    this.mediaThumbnailStub = new Stub<>(findViewById(R.id.image_view_stub));
    this.audioViewStub = new Stub<>(findViewById(R.id.audio_view_stub));
    this.documentViewStub = new Stub<>(findViewById(R.id.document_view_stub));
    this.webxncViewStub = new Stub<>(findViewById(R.id.webxnc_view_stub));
    this.stickerStub = new Stub<>(findViewById(R.id.sticker_view_stub));
    this.vcardViewStub = new Stub<>(findViewById(R.id.vcard_view_stub));
    this.callViewStub = new Stub<>(findViewById(R.id.call_view_stub));
    this.groupSenderHolder = findViewById(R.id.group_sender_holder);
    this.quoteView = findViewById(R.id.quote_view);
    this.container = findViewById(R.id.container);
    this.replyView = findViewById(R.id.reply_icon);
    this.jumptoView = findViewById(R.id.jumpto_icon);
    this.msgActionButton = findViewById(R.id.msg_action_button);
    this.showFullButton = findViewById(R.id.show_full_button);

    setOnClickListener(new ClickListener(null));

    bodyText.setOnLongClickListener(passthroughClickListener);
    bodyText.setOnClickListener(passthroughClickListener);

    bodyText.setMovementMethod(LongClickMovementMethod.getInstance(getContext()));
  }

  @Override
  public void bind(
      @NonNull NcMsg messageRecord,
      @NonNull NcChat ncChat,
      @NonNull GlideRequests glideRequests,
      @NonNull Set<NcMsg> batchSelected,
      @NonNull Recipient recipients,
      boolean pulseHighlight,
      @Nullable AudioPlaybackViewModel playbackViewModel,
      AudioView.OnActionListener audioPlayPauseListener) {
    bindPartial(messageRecord, ncChat, batchSelected, pulseHighlight, recipients);
    this.glideRequests = glideRequests;
    this.showSender =
        ((ncChat.isMultiUser() || ncChat.isSelfTalk()) && !messageRecord.isOutgoing())
            || messageRecord.getOverrideSenderName() != null;

    if (showSender) {
      this.ncContact = ncContext.getContact(messageRecord.getFromId());
    }

    if (ncChat.isSelfTalk() && messageRecord.getOriginalMsgId() != 0) {
      jumptoView.setVisibility(View.VISIBLE);
      jumptoView.setOnClickListener(
          view -> {
            if (eventListener != null) {
              eventListener.onJumpToOriginalClicked(messageRecord);
            }
          });
    } else {
      jumptoView.setVisibility(View.GONE);
    }

    setGutterSizes(messageRecord, showSender);
    setMessageShape(messageRecord);
    setMediaAttributes(messageRecord, showSender, playbackViewModel, audioPlayPauseListener);
    setBodyText(messageRecord);
    setBubbleState(messageRecord);
    setContactPhoto();
    setGroupMessageStatus();
    setAuthor(messageRecord, showSender);
    setMessageSpacing(context);
    setReactions(messageRecord);
    setFooter(messageRecord);
    setQuote(messageRecord);
    if (Util.isTouchExplorationEnabled(context)) {
      setContentDescription();
    }
  }

  @Override
  public void setEventListener(@Nullable EventListener eventListener) {
    this.eventListener = eventListener;
  }

  public boolean disallowSwipe(float downX, float downY) {
    // If it is possible to reply to a message, it should also be possible to swipe it.
    // For this to be possible we need a non-null reply icon.
    // This means that `replyView != null` must always be the same as
    // ConversationFragment.canReplyToMsg(messageRecord).
    if (replyView == null) return true;
    if (!ncChat.canSend()) return true;

    if (!hasAudio(messageRecord)) return false;
    audioViewStub.get().getSeekBarGlobalVisibleRect(SWIPE_RECT);
    return SWIPE_RECT.contains((int) downX, (int) downY);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    if (isInEditMode()) {
      return;
    }

    boolean needsMeasure = false;

    if (hasQuote(messageRecord)) {
      if (quoteView == null) {
        throw new AssertionError();
      }
      int quoteWidth = quoteView.getMeasuredWidth();
      int availableWidth = getAvailableMessageBubbleWidth(quoteView);

      if (quoteWidth != availableWidth) {
        quoteView.getLayoutParams().width = availableWidth;
        needsMeasure = true;
      }
    }

    if (needsMeasure) {
      if (measureCalls < MAX_MEASURE_CALLS) {
        measureCalls++;
        measure(widthMeasureSpec, heightMeasureSpec);
      } else {
        Log.w(TAG, "Hit measure() cap of " + MAX_MEASURE_CALLS);
      }
    } else {
      measureCalls = 0;
    }
  }

  private void initializeAttributes() {
    final int[] attributes =
        new int[] {
          R.attr.conversation_item_incoming_bubble_color,
          R.attr.conversation_item_outgoing_bubble_color,
        };
    try (TypedArray attrs = context.obtainStyledAttributes(attributes)) {
      incomingBubbleColor = attrs.getColor(0, Color.WHITE);
      outgoingBubbleColor = attrs.getColor(1, Color.WHITE);
    }
  }

  @Override
  public void unbind() {}

  public NcMsg getMessageRecord() {
    return messageRecord;
  }

  /// NcMsg Attribute Parsers

  private void setBubbleState(NcMsg messageRecord) {
    if (messageRecord.isOutgoing()) {
      bodyBubble.getBackground().setColorFilter(outgoingBubbleColor, PorterDuff.Mode.MULTIPLY);
    } else {
      bodyBubble.getBackground().setColorFilter(incomingBubbleColor, PorterDuff.Mode.MULTIPLY);
    }
  }

  @Override
  protected void setInteractionState(NcMsg messageRecord, boolean pulseHighlight) {
    super.setInteractionState(messageRecord, pulseHighlight);

    if (mediaThumbnailStub.resolved()) {
      mediaThumbnailStub
          .get()
          .setFocusable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
      mediaThumbnailStub
          .get()
          .setClickable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
      mediaThumbnailStub.get().setLongClickable(batchSelected.isEmpty());
    }

    if (audioViewStub.resolved()) {
      audioViewStub.get().disablePlayer(!batchSelected.isEmpty());
    }

    if (documentViewStub.resolved()) {
      documentViewStub
          .get()
          .setFocusable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
      documentViewStub.get().setClickable(batchSelected.isEmpty());
    }

    if (webxncViewStub.resolved()) {
      webxncViewStub
          .get()
          .setFocusable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
      webxncViewStub.get().setClickable(batchSelected.isEmpty());
    }

    if (vcardViewStub.resolved()) {
      vcardViewStub
          .get()
          .setFocusable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
      vcardViewStub.get().setClickable(batchSelected.isEmpty());
    }

    if (callViewStub.resolved()) {
      callViewStub
          .get()
          .setFocusable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
      callViewStub.get().setClickable(batchSelected.isEmpty());
    }
  }

  private void setContentDescription() {
    String desc = "";
    if (groupSenderHolder.getVisibility() == View.VISIBLE) {
      desc = groupSender.getText() + "\n";
    }

    if (audioViewStub.resolved() && audioViewStub.get().getVisibility() == View.VISIBLE) {
      desc += audioViewStub.get().getDescription() + "\n";
    } else if (documentViewStub.resolved()
        && documentViewStub.get().getVisibility() == View.VISIBLE) {
      desc += documentViewStub.get().getDescription() + "\n";
    } else if (webxncViewStub.resolved() && webxncViewStub.get().getVisibility() == View.VISIBLE) {
      desc += webxncViewStub.get().getDescription() + "\n";
    } else if (vcardViewStub.resolved() && vcardViewStub.get().getVisibility() == View.VISIBLE) {
      desc += vcardViewStub.get().getDescription() + "\n";
    } else if (callViewStub.resolved() && callViewStub.get().getVisibility() == View.VISIBLE) {
      desc += callViewStub.get().getDescription() + "\n";
    } else if (mediaThumbnailStub.resolved()
        && mediaThumbnailStub.get().getVisibility() == View.VISIBLE) {
      desc += mediaThumbnailStub.get().getDescription() + "\n";
    } else if (stickerStub.resolved() && stickerStub.get().getVisibility() == View.VISIBLE) {
      desc += stickerStub.get().getDescription() + "\n";
    }

    if (bodyText.getVisibility() == View.VISIBLE) {
      desc += bodyText.getText() + "\n";
    }

    if (footer.getVisibility() == View.VISIBLE) {
      desc += footer.getDescription();
    }

    this.setContentDescription(desc);
  }

  private boolean hasAudio(NcMsg messageRecord) {
    int type = messageRecord.getType();
    return type == NcMsg.NC_MSG_AUDIO || type == NcMsg.NC_MSG_VOICE;
  }

  private boolean hasQuote(NcMsg messageRecord) {
    return !"".equals(messageRecord.getQuotedText());
  }

  private boolean hasThumbnail(NcMsg messageRecord) {
    int type = messageRecord.getType();
    return type == NcMsg.NC_MSG_GIF || type == NcMsg.NC_MSG_IMAGE || type == NcMsg.NC_MSG_VIDEO;
  }

  private boolean hasSticker(NcMsg ncMsg) {
    return ncMsg.getType() == NcMsg.NC_MSG_STICKER;
  }

  private boolean hasOnlyThumbnail(NcMsg messageRecord) {
    return hasThumbnail(messageRecord)
        && !hasAudio(messageRecord)
        && !hasDocument(messageRecord)
        && !hasWebxnc(messageRecord)
        && !hasSticker(messageRecord);
  }

  private boolean hasWebxnc(NcMsg ncMsg) {
    return ncMsg.getType() == NcMsg.NC_MSG_WEBXNC;
  }

  private boolean hasVcard(NcMsg ncMsg) {
    return ncMsg.getType() == NcMsg.NC_MSG_VCARD;
  }

  private boolean hasDocument(NcMsg ncMsg) {
    return ncMsg.getType() == NcMsg.NC_MSG_FILE;
  }

  private void setBodyText(NcMsg messageRecord) {
    bodyText.setClickable(false);
    bodyText.setFocusable(false);

    String text = messageRecord.getText();

    if (messageRecord.getType() == NcMsg.NC_MSG_CALL || text.isEmpty()) {
      bodyText.setVisibility(View.GONE);
    } else {
      SpannableString spannable = new SpannableString(text);
      if (batchSelected.isEmpty()) {
        spannable = Linkifier.linkify(spannable);
      }
      bodyText.setText(spannable);
      bodyText.setVisibility(View.VISIBLE);
    }

    int downloadState = messageRecord.getDownloadState();
    if (downloadState == NcMsg.NC_DOWNLOAD_AVAILABLE
        || downloadState == NcMsg.NC_DOWNLOAD_FAILURE
        || downloadState == NcMsg.NC_DOWNLOAD_IN_PROGRESS) {
      showFullButton.setVisibility(View.GONE);
      msgActionButton.setVisibility(View.VISIBLE);
      if (downloadState == NcMsg.NC_DOWNLOAD_IN_PROGRESS) {
        msgActionButton.setEnabled(false);
        msgActionButton.setText(R.string.downloading);
      } else if (downloadState == NcMsg.NC_DOWNLOAD_FAILURE) {
        msgActionButton.setEnabled(true);
        msgActionButton.setText(R.string.download_failed);
      } else {
        msgActionButton.setEnabled(true);
        msgActionButton.setText(R.string.download);
      }

      msgActionButton.setOnClickListener(
          view -> {
            if (eventListener != null && batchSelected.isEmpty()) {
              eventListener.onDownloadClicked(messageRecord);
            } else {
              passthroughClickListener.onClick(view);
            }
          });
    } else if (messageRecord.getType() == NcMsg.NC_MSG_WEBXNC) {
      showFullButton.setVisibility(View.GONE);
      msgActionButton.setVisibility(View.VISIBLE);
      msgActionButton.setEnabled(true);
      msgActionButton.setText(R.string.start_app);
      msgActionButton.setOnClickListener(
          view -> {
            if (batchSelected.isEmpty()) {
              WebxncActivity.openWebxncActivity(getContext(), messageRecord);
            } else {
              passthroughClickListener.onClick(view);
            }
          });
    } else if (messageRecord.hasHtml()) {
      msgActionButton.setVisibility(View.GONE);
      showFullButton.setVisibility(View.VISIBLE);
      showFullButton.setEnabled(true);
      showFullButton.setText(R.string.show_full_message);
      showFullButton.setOnClickListener(
          view -> {
            if (eventListener != null && batchSelected.isEmpty()) {
              eventListener.onShowFullClicked(messageRecord);
            } else {
              passthroughClickListener.onClick(view);
            }
          });
    } else {
      msgActionButton.setVisibility(View.GONE);
      showFullButton.setVisibility(View.GONE);
    }
  }

  private void setMediaAttributes(
      @NonNull NcMsg messageRecord,
      boolean showSender,
      AudioPlaybackViewModel playbackViewModel,
      AudioView.OnActionListener audioPlayPauseListener) {
    if (hasAudio(messageRecord)) {
      audioViewStub.get().setVisibility(View.VISIBLE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(View.GONE);
      if (webxncViewStub.resolved()) webxncViewStub.get().setVisibility(View.GONE);
      if (stickerStub.resolved()) stickerStub.get().setVisibility(View.GONE);
      if (vcardViewStub.resolved()) vcardViewStub.get().setVisibility(View.GONE);
      if (callViewStub.resolved()) callViewStub.get().setVisibility(View.GONE);

      audioViewStub.get().setPlaybackViewModel(playbackViewModel);
      audioViewStub.get().setOnActionListener(audioPlayPauseListener);
      audioViewStub.get().setAudio(new AudioSlide(context, messageRecord));
      audioViewStub.get().setOnClickListener(passthroughClickListener);
      audioViewStub.get().setOnLongClickListener(passthroughClickListener);
      audioViewStub
          .get()
          .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

      ViewUtil.updateLayoutParams(
          bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParams(
          groupSenderHolder,
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT);
      footer.setVisibility(VISIBLE);
    } else if (hasDocument(messageRecord)) {
      documentViewStub.get().setVisibility(View.VISIBLE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);
      if (webxncViewStub.resolved()) webxncViewStub.get().setVisibility(View.GONE);
      if (stickerStub.resolved()) stickerStub.get().setVisibility(View.GONE);
      if (vcardViewStub.resolved()) vcardViewStub.get().setVisibility(View.GONE);
      if (callViewStub.resolved()) callViewStub.get().setVisibility(View.GONE);

      //noinspection ConstantConditions
      documentViewStub.get().setDocument(new DocumentSlide(context, messageRecord));
      documentViewStub.get().setDocumentClickListener(new ThumbnailClickListener());
      documentViewStub.get().setOnLongClickListener(passthroughClickListener);
      documentViewStub
          .get()
          .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

      ViewUtil.updateLayoutParams(
          bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParams(
          groupSenderHolder,
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT);
      footer.setVisibility(VISIBLE);
    } else if (hasWebxnc(messageRecord)) {
      webxncViewStub.get().setVisibility(View.VISIBLE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(View.GONE);
      if (stickerStub.resolved()) stickerStub.get().setVisibility(View.GONE);
      if (vcardViewStub.resolved()) vcardViewStub.get().setVisibility(View.GONE);
      if (callViewStub.resolved()) callViewStub.get().setVisibility(View.GONE);

      webxncViewStub.get().setWebxnc(messageRecord, context.getString(R.string.webxnc_app));
      webxncViewStub.get().setWebxncClickListener(new ThumbnailClickListener());
      webxncViewStub.get().setOnLongClickListener(passthroughClickListener);
      webxncViewStub
          .get()
          .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

      ViewUtil.updateLayoutParams(
          bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParams(
          groupSenderHolder,
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT);
      footer.setVisibility(VISIBLE);
    } else if (hasVcard(messageRecord)) {
      vcardViewStub.get().setVisibility(View.VISIBLE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(View.GONE);
      if (webxncViewStub.resolved()) webxncViewStub.get().setVisibility(View.GONE);
      if (stickerStub.resolved()) stickerStub.get().setVisibility(View.GONE);
      if (callViewStub.resolved()) callViewStub.get().setVisibility(View.GONE);

      vcardViewStub.get().setVcard(glideRequests, new VcardSlide(context, messageRecord), rpc);
      vcardViewStub.get().setVcardClickListener(new ThumbnailClickListener());
      vcardViewStub.get().setOnLongClickListener(passthroughClickListener);

      vcardViewStub
          .get()
          .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

      ViewUtil.updateLayoutParams(
          bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParams(
          groupSenderHolder,
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT);
      footer.setVisibility(VISIBLE);
    } else if (messageRecord.getType() == NcMsg.NC_MSG_CALL) {
      callViewStub.get().setVisibility(View.VISIBLE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(View.GONE);
      if (webxncViewStub.resolved()) webxncViewStub.get().setVisibility(View.GONE);
      if (stickerStub.resolved()) stickerStub.get().setVisibility(View.GONE);
      if (vcardViewStub.resolved()) vcardViewStub.get().setVisibility(View.GONE);

      try {
        callViewStub
            .get()
            .setCallItem(
                messageRecord.isOutgoing(),
                rpc.callInfo(ncContext.getAccountId(), messageRecord.getId()));
      } catch (RpcException e) {
        Log.e(TAG, "Error in Rpc.callInfo", e);
      }
      callViewStub.get().setCallClickListener(new CallClickListener());
      callViewStub.get().setOnLongClickListener(passthroughClickListener);

      callViewStub
          .get()
          .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

      ViewUtil.updateLayoutParams(
          groupSenderHolder,
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT);
    } else if (hasThumbnail(messageRecord)) {
      mediaThumbnailStub.get().setVisibility(View.VISIBLE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(View.GONE);
      if (webxncViewStub.resolved()) webxncViewStub.get().setVisibility(View.GONE);
      if (stickerStub.resolved()) stickerStub.get().setVisibility(View.GONE);
      if (vcardViewStub.resolved()) vcardViewStub.get().setVisibility(View.GONE);
      if (callViewStub.resolved()) callViewStub.get().setVisibility(View.GONE);

      Slide slide = MediaUtil.getSlideForMsg(context, messageRecord);

      MediaUtil.ThumbnailSize thumbnailSize =
          new MediaUtil.ThumbnailSize(messageRecord.getWidth(0), messageRecord.getHeight(0));
      if ((thumbnailSize.width <= 0 || thumbnailSize.height <= 0)) {
        if (messageRecord.getType() == NcMsg.NC_MSG_VIDEO) {
          MediaUtil.createVideoThumbnailIfNeeded(
              context, slide.getUri(), slide.getThumbnailUri(), thumbnailSize);
        }
        if (thumbnailSize.width <= 0 || thumbnailSize.height <= 0) {
          thumbnailSize.width = 180;
          thumbnailSize.height = 180;
        }
        messageRecord.lateFilingMediaSize(thumbnailSize.width, thumbnailSize.height, 0);
      }

      mediaThumbnailStub
          .get()
          .setImageResource(glideRequests, slide, thumbnailSize.width, thumbnailSize.height);
      mediaThumbnailStub.get().setThumbnailClickListener(new ThumbnailClickListener());
      mediaThumbnailStub.get().setOnLongClickListener(passthroughClickListener);
      mediaThumbnailStub.get().setOnClickListener(passthroughClickListener);
      mediaThumbnailStub.get().showShade(TextUtils.isEmpty(messageRecord.getText()));
      mediaThumbnailStub
          .get()
          .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

      setThumbnailOutlineCorners(messageRecord, showSender);

      bodyBubble.getLayoutParams().width =
          ViewUtil.dpToPx(readDimen(R.dimen.media_bubble_max_width));
      ViewUtil.updateLayoutParams(
          bodyText, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParams(
          groupSenderHolder,
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT);
      footer.setVisibility(VISIBLE);
    } else if (hasSticker(messageRecord)) {
      stickerStub.get().setVisibility(View.VISIBLE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(View.GONE);
      if (webxncViewStub.resolved()) webxncViewStub.get().setVisibility(View.GONE);
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (vcardViewStub.resolved()) vcardViewStub.get().setVisibility(View.GONE);
      if (callViewStub.resolved()) callViewStub.get().setVisibility(View.GONE);

      bodyBubble.setBackgroundColor(Color.TRANSPARENT);

      stickerStub.get().setSlide(glideRequests, new StickerSlide(context, messageRecord));
      stickerStub.get().setThumbnailClickListener(new StickerClickListener());
      stickerStub.get().setOnLongClickListener(passthroughClickListener);
      stickerStub.get().setOnClickListener(passthroughClickListener);
      stickerStub
          .get()
          .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

      ViewUtil.updateLayoutParams(
          bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParams(
          groupSenderHolder,
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT);

      footer.setVisibility(VISIBLE);
    } else {
      if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().setVisibility(View.GONE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);
      if (documentViewStub.resolved()) documentViewStub.get().setVisibility(View.GONE);
      if (webxncViewStub.resolved()) webxncViewStub.get().setVisibility(View.GONE);
      if (vcardViewStub.resolved()) vcardViewStub.get().setVisibility(View.GONE);
      if (callViewStub.resolved()) callViewStub.get().setVisibility(View.GONE);

      ViewUtil.updateLayoutParams(
          bodyText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      ViewUtil.updateLayoutParams(
          groupSenderHolder,
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT);
      footer.setVisibility(VISIBLE);
    }
  }

  private void setThumbnailOutlineCorners(@NonNull NcMsg current, boolean showSender) {
    int defaultRadius = readDimen(R.dimen.message_corner_radius);

    int topLeft = defaultRadius;
    int topRight = defaultRadius;
    int bottomLeft = defaultRadius;
    int bottomRight = defaultRadius;

    if (!TextUtils.isEmpty(current.getText())) {
      bottomLeft = 0;
      bottomRight = 0;
    }

    if (showSender || current.isForwarded() || hasQuote(current)) {
      topLeft = 0;
      topRight = 0;
    }

    if (bottomLeft != 0 && bottomRight != 0) {
      if ((current.isOutgoing() && ViewUtil.isLtr(this))
          || (!current.isOutgoing() && ViewUtil.isRtl(this))) {
        bottomRight = 0;
      } else {
        bottomLeft = 0;
      }
    }

    mediaThumbnailStub.get().setOutlineCorners(topLeft, topRight, bottomRight, bottomLeft);
  }

  private void setContactPhoto() {
    if (contactPhoto == null) return;

    if (!showSender || ncContact == null) {
      contactPhoto.setVisibility(View.GONE);
    } else {
      contactPhoto.setAvatar(glideRequests, new Recipient(context, ncContact), true);
      contactPhoto.setVisibility(View.VISIBLE);
    }
  }

  private void setQuote(@NonNull NcMsg current) {
    if (quoteView == null) {
      throw new AssertionError();
    }
    String quoteTxt = current.getQuotedText();
    if (quoteTxt == null || quoteTxt.isEmpty()) {
      quoteView.dismiss();
      if (mediaThumbnailStub.resolved()) {
        ViewUtil.setTopMargin(mediaThumbnailStub.get(), 0);
      } else if (stickerStub.resolved()) {
        ViewUtil.setTopMargin(stickerStub.get(), 0);
      }
      return;
    }
    NcMsg msg = current.getQuotedMsg();

    // If you modify these lines you may also want to modify
    // ConversationActivity.handleReplyMessage():
    Recipient author = null;
    SlideDeck slideDeck = new SlideDeck();
    if (msg != null) {
      author = new Recipient(context, ncContext.getContact(msg.getFromId()));
      if (msg.getType() != NcMsg.NC_MSG_TEXT) {
        Slide slide = MediaUtil.getSlideForMsg(context, msg);
        if (slide != null) {
          slideDeck.addSlide(slide);
        }
      }
    }

    quoteView.setQuote(
        GlideApp.with(this),
        msg,
        author,
        quoteTxt,
        slideDeck,
        current.getType() == NcMsg.NC_MSG_STICKER,
        false);

    quoteView.setVisibility(View.VISIBLE);
    quoteView.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;

    quoteView.setOnClickListener(
        view -> {
          if (eventListener != null && batchSelected.isEmpty()) {
            eventListener.onQuoteClicked(current);
          } else {
            passthroughClickListener.onClick(view);
          }
        });

    quoteView.setOnLongClickListener(passthroughClickListener);

    if (mediaThumbnailStub.resolved()) {
      ViewUtil.setTopMargin(
          mediaThumbnailStub.get(), readDimen(R.dimen.message_bubble_top_padding));
    } else if (stickerStub.resolved()) {
      ViewUtil.setTopMargin(stickerStub.get(), readDimen(R.dimen.message_bubble_top_padding));
    }
  }

  private void setGutterSizes(@NonNull NcMsg current, boolean showSender) {
    if (showSender && current.isOutgoing()) {
      ViewUtil.setLeftMargin(container, readDimen(R.dimen.conversation_group_left_gutter));
    } else if (current.isOutgoing()) {
      ViewUtil.setLeftMargin(container, readDimen(R.dimen.conversation_individual_left_gutter));
    }
  }

  private void setFooter(@NonNull NcMsg current) {
    ViewUtil.updateLayoutParams(footer, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

    footer.setVisibility(GONE);
    if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().getFooter().setVisibility(GONE);

    ConversationItemFooter activeFooter = getActiveFooter(current);
    activeFooter.setVisibility(VISIBLE);
    activeFooter.setMessageRecord(current);
  }

  private void setReactions(@NonNull NcMsg current) {
    try {
      Reactions reactions = rpc.getMessageReactions(ncContext.getAccountId(), current.getId());
      if (reactions == null) {
        reactionsView.clear();
      } else {
        reactionsView.setReactions(reactions.reactions);
        reactionsView.setOnClickListener(
            view -> {
              if (eventListener != null && batchSelected.isEmpty()) {
                eventListener.onReactionClicked(current);
              } else {
                passthroughClickListener.onClick(view);
              }
            });
      }
    } catch (RpcException e) {
      reactionsView.clear();
    }
  }

  private ConversationItemFooter getActiveFooter(@NonNull NcMsg messageRecord) {
    if (hasSticker(messageRecord)) {
      return stickerStub.get().getFooter();
    } else if (hasOnlyThumbnail(messageRecord) && TextUtils.isEmpty(messageRecord.getText())) {
      return mediaThumbnailStub.get().getFooter();
    } else {
      return footer;
    }
  }

  private int readDimen(@DimenRes int dimenId) {
    return context.getResources().getDimensionPixelOffset(dimenId);
  }

  private void setGroupMessageStatus() {
    if (messageRecord.getType() == NcMsg.NC_MSG_STICKER) {
      this.groupSender.setVisibility(GONE);
      return;
    } else {
      this.groupSender.setVisibility(VISIBLE);
    }

    if (messageRecord.isForwarded()) {
      if (showSender && ncContact != null) {
        this.groupSender.setText(
            context.getString(R.string.forwarded_by, messageRecord.getSenderName(ncContact)));
      } else {
        this.groupSender.setText(context.getString(R.string.forwarded_message));
      }
      this.groupSender.setTextColor(context.getResources().getColor(R.color.unknown_sender));
    } else if (showSender && ncContact != null) {
      this.groupSender.setText(messageRecord.getSenderName(ncContact));
      this.groupSender.setTextColor(Util.rgbToArgbColor(ncContact.getColor()));
    }
  }

  private void setAuthor(@NonNull NcMsg current, boolean showSender) {
    int groupSenderHolderVisibility = GONE;
    if (showSender) {
      if (contactPhotoHolder != null) {
        contactPhotoHolder.setVisibility(VISIBLE);
        contactPhoto.setVisibility(VISIBLE);
      }
      groupSenderHolderVisibility = VISIBLE;
    } else {
      if (contactPhotoHolder != null) {
        contactPhotoHolder.setVisibility(GONE);
      }
    }

    if (current.isForwarded()) {
      groupSenderHolderVisibility = VISIBLE;
    }

    groupSenderHolder.setVisibility(groupSenderHolderVisibility);

    boolean collapse = false;
    if (groupSenderHolderVisibility == VISIBLE && current.getType() == NcMsg.NC_MSG_TEXT) {
      collapse = true;
    }

    int spacingTop =
        collapse
            ? 0 /*2dp border come from the senderHolder*/
            : readDimen(context, R.dimen.message_bubble_top_padding);
    ViewUtil.setPaddingTop(bodyText, spacingTop);
  }

  private void setMessageShape(@NonNull NcMsg current) {
    int background;
    background =
        current.isOutgoing()
            ? R.drawable.message_bubble_background_sent_alone
            : R.drawable.message_bubble_background_received_alone;
    bodyBubble.setBackgroundResource(background);
  }

  private void setMessageSpacing(@NonNull Context context) {
    int spacingTop = readDimen(context, R.dimen.conversation_vertical_message_spacing_collapse);
    int spacingBottom = spacingTop;

    ViewUtil.setPaddingTop(this, spacingTop);
    ViewUtil.setPaddingBottom(this, spacingBottom);
  }

  private int readDimen(@NonNull Context context, @DimenRes int dimenId) {
    return context.getResources().getDimensionPixelOffset(dimenId);
  }

  private int getAvailableMessageBubbleWidth(@NonNull View forView) {
    int availableWidth;
    if (hasAudio(messageRecord)) {
      availableWidth =
          audioViewStub.get().getMeasuredWidth()
              + ViewUtil.getLeftMargin(audioViewStub.get())
              + ViewUtil.getRightMargin(audioViewStub.get());
    } else if (hasThumbnail(messageRecord)) {
      availableWidth = mediaThumbnailStub.get().getMeasuredWidth();
    } else {
      availableWidth =
          bodyBubble.getMeasuredWidth()
              - bodyBubble.getPaddingLeft()
              - bodyBubble.getPaddingRight();
    }

    availableWidth -= ViewUtil.getLeftMargin(forView) + ViewUtil.getRightMargin(forView);

    return availableWidth;
  }

  @Override
  public void onAccessibilityClick() {
    if (mediaThumbnailStub.resolved()) mediaThumbnailStub.get().performClick();
    else if (audioViewStub.resolved()) audioViewStub.get().togglePlay();
    else if (documentViewStub.resolved()) documentViewStub.get().performClick();
    else if (webxncViewStub.resolved()) webxncViewStub.get().performClick();
    else if (vcardViewStub.resolved()) vcardViewStub.get().performClick();
    else if (callViewStub.resolved()) callViewStub.get().performClick();
  }

  /// Event handlers

  private class ThumbnailClickListener implements SlideClickListener {
    public void onClick(final View v, final Slide slide) {
      if (shouldInterceptClicks(messageRecord) || !batchSelected.isEmpty()) {
        performClick();
      } else if (slide.isWebxncDocument()) {
        WebxncActivity.openWebxncActivity(context, messageRecord);
      } else if (slide.isVcard()) {
        try {
          String path = slide.asAttachment().getRealPath(context);
          VcardContact vcardContact = rpc.parseVcard(path).get(0);
          new AlertDialog.Builder(context)
              .setMessage(context.getString(R.string.ask_start_chat_with, vcardContact.displayName))
              .setPositiveButton(
                  android.R.string.ok,
                  (dialog, which) -> {
                    try {
                      List<Integer> contactIds = rpc.importVcard(ncContext.getAccountId(), path);
                      if (!contactIds.isEmpty()) {
                        int chatId = ncContext.createChatByContactId(contactIds.get(0));
                        if (chatId != 0) {
                          Intent intent = new Intent(context, ConversationActivity.class);
                          intent.putExtra(ConversationActivity.CHAT_ID_EXTRA, chatId);
                          context.startActivity(intent);
                          return;
                        }
                      }
                    } catch (RpcException e) {
                      Log.e(TAG, "failed to import vCard", e);
                    }
                    Toast.makeText(
                            context,
                            context.getResources().getString(R.string.error),
                            Toast.LENGTH_SHORT)
                        .show();
                  })
              .setNegativeButton(R.string.cancel, null)
              .show();
        } catch (RpcException e) {
          Log.e(TAG, "failed to parse vCard", e);
          Toast.makeText(
                  context, context.getResources().getString(R.string.error), Toast.LENGTH_SHORT)
              .show();
        }
      } else if (MediaPreviewActivity.isTypeSupported(slide) && slide.getUri() != null) {
        Intent intent = new Intent(context, MediaPreviewActivity.class);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(MediaPreviewActivity.NC_MSG_ID, slide.getNcMsgId());
        intent.putExtra(MediaPreviewActivity.ADDRESS_EXTRA, conversationRecipient.getAddress());
        intent.putExtra(MediaPreviewActivity.OUTGOING_EXTRA, messageRecord.isOutgoing());
        intent.putExtra(MediaPreviewActivity.LEFT_IS_RECENT_EXTRA, false);

        context.startActivity(intent);
      } else if (slide.getUri() != null) {
        NcHelper.openForViewOrShare(context, slide.getNcMsgId(), Intent.ACTION_VIEW);
      }
    }
  }

  private class StickerClickListener implements SlideClickListener {
    public void onClick(final View v, final Slide slide) {
      if (shouldInterceptClicks(messageRecord) || !batchSelected.isEmpty()) {
        performClick();
      } else if (eventListener != null) {
        eventListener.onStickerClicked(messageRecord);
      }
    }
  }

  private class CallClickListener implements CallItemView.CallClickListener {
    public void onClick(final View v, final CallInfo callInfo) {
      if (shouldInterceptClicks(messageRecord) || !batchSelected.isEmpty()) {
        performClick();
      } else {
        int chatId = messageRecord.getChatId();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          if (!messageRecord.isOutgoing() && callInfo.state instanceof CallState.Alerting) {
            int callId = messageRecord.getId();
            CallCoordinator coordinator = CallCoordinator.getInstance(context);
            coordinator.showIncomingCallScreen(callId);
          } else {
            if (callInfo.hasVideo) {
              CallUtil.startVideoCall(getContext(), chatId);
            } else {
              CallUtil.startAudioCall(getContext(), chatId);
            }
          }
        }
      }
    }
  }
}

package com.nexuschat.messenger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.b44t.messenger.NcChat;
import com.b44t.messenger.NcMsg;
import java.util.Set;
import com.nexuschat.messenger.components.audioplay.AudioPlaybackViewModel;
import com.nexuschat.messenger.components.audioplay.AudioView;
import com.nexuschat.messenger.mms.GlideRequests;
import com.nexuschat.messenger.recipients.Recipient;

public interface BindableConversationItem extends Unbindable {
  void bind(
      @NonNull NcMsg messageRecord,
      @NonNull NcChat ncChat,
      @NonNull GlideRequests glideRequests,
      @NonNull Set<NcMsg> batchSelected,
      @NonNull Recipient recipients,
      boolean pulseHighlight,
      @Nullable AudioPlaybackViewModel playbackViewModel,
      AudioView.OnActionListener audioPlayPauseListener);

  NcMsg getMessageRecord();

  void setEventListener(@Nullable EventListener listener);

  interface EventListener {
    void onQuoteClicked(NcMsg messageRecord);

    void onJumpToOriginalClicked(NcMsg messageRecord);

    void onShowFullClicked(NcMsg messageRecord);

    void onDownloadClicked(NcMsg messageRecord);

    void onReactionClicked(NcMsg messageRecord);

    void onStickerClicked(NcMsg messageRecord);
  }
}

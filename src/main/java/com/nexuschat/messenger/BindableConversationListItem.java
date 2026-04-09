package com.nexuschat.messenger;

import androidx.annotation.NonNull;
import com.b44t.messenger.NcLot;
import java.util.Set;
import com.nexuschat.messenger.database.model.ThreadRecord;
import com.nexuschat.messenger.mms.GlideRequests;

public interface BindableConversationListItem extends Unbindable {

  public void bind(
      @NonNull ThreadRecord thread,
      int msgId,
      @NonNull NcLot ncSummary,
      @NonNull GlideRequests glideRequests,
      @NonNull Set<Long> selectedThreads,
      boolean batchMode);
}

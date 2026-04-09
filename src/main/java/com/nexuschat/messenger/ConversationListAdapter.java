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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.b44t.messenger.NcChat;
import com.b44t.messenger.NcChatlist;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcLot;
import java.lang.ref.WeakReference;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.mms.GlideRequests;
import com.nexuschat.messenger.util.ViewUtil;

/**
 * A CursorAdapter for building a list of conversation threads.
 *
 * @author Moxie Marlinspike
 */
class ConversationListAdapter
    extends BaseConversationListAdapter<ConversationListAdapter.ViewHolder> {

  private static final int MESSAGE_TYPE_SWITCH_ARCHIVE = 1;
  private static final int MESSAGE_TYPE_THREAD = 2;
  private static final int MESSAGE_TYPE_INBOX_ZERO = 3;

  private final WeakReference<Context> context;
  private @NonNull NcContext ncContext;
  private @NonNull NcChatlist ncChatlist;
  private final @NonNull GlideRequests glideRequests;
  private final @NonNull LayoutInflater inflater;
  private final @Nullable ItemClickListener clickListener;

  protected static class ViewHolder extends RecyclerView.ViewHolder {
    public <V extends View & BindableConversationListItem> ViewHolder(final @NonNull V itemView) {
      super(itemView);
    }

    public BindableConversationListItem getItem() {
      return (BindableConversationListItem) itemView;
    }
  }

  @Override
  public int getItemCount() {
    return ncChatlist.getCnt();
  }

  @Override
  public long getItemId(int i) {
    return ncChatlist.getChatId(i);
  }

  ConversationListAdapter(
      @NonNull Context context,
      @NonNull GlideRequests glideRequests,
      @Nullable ItemClickListener clickListener) {
    super();
    this.context = new WeakReference<>(context);
    this.glideRequests = glideRequests;
    this.ncContext = NcHelper.getContext(context);
    this.ncChatlist = new NcChatlist(0, 0);
    this.inflater = LayoutInflater.from(context);
    this.clickListener = clickListener;
    setHasStableIds(true);
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    if (viewType == MESSAGE_TYPE_SWITCH_ARCHIVE) {
      final ConversationListItem item =
          (ConversationListItem)
              inflater.inflate(R.layout.conversation_list_item_view, parent, false);
      item.getLayoutParams().height = ViewUtil.dpToPx(54);
      item.findViewById(R.id.subject).setVisibility(View.GONE);
      item.findViewById(R.id.date).setVisibility(View.GONE);
      item.setOnClickListener(
          v -> {
            if (clickListener != null) clickListener.onSwitchToArchive();
          });

      return new ViewHolder(item);
    } else if (viewType == MESSAGE_TYPE_INBOX_ZERO) {
      return new ViewHolder(
          (ConversationListItemInboxZero)
              inflater.inflate(R.layout.conversation_list_item_inbox_zero, parent, false));
    } else {
      final ConversationListItem item =
          (ConversationListItem)
              inflater.inflate(R.layout.conversation_list_item_view, parent, false);

      item.setOnClickListener(
          view -> {
            if (clickListener != null) clickListener.onItemClick(item);
          });

      item.setOnLongClickListener(
          view -> {
            if (clickListener != null) clickListener.onItemLongClick(item);
            return true;
          });

      return new ViewHolder(item);
    }
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
    Context context = this.context.get();
    if (context == null) {
      return;
    }

    NcChat chat = ncContext.getChat(ncChatlist.getChatId(i));
    NcLot summary = ncChatlist.getSummary(i, chat);
    viewHolder
        .getItem()
        .bind(
            NcHelper.getThreadRecord(context, summary, chat),
            ncChatlist.getMsgId(i),
            summary,
            glideRequests,
            batchSet,
            batchMode);
  }

  @Override
  public int getItemViewType(int i) {
    int chatId = ncChatlist.getChatId(i);

    if (chatId == NcChat.NC_CHAT_ID_ARCHIVED_LINK) {
      return MESSAGE_TYPE_SWITCH_ARCHIVE;
    } else if (chatId == NcChat.NC_CHAT_ID_ALLDONE_HINT) {
      return MESSAGE_TYPE_INBOX_ZERO;
    } else {
      return MESSAGE_TYPE_THREAD;
    }
  }

  @Override
  public void selectAllThreads() {
    for (int i = 0; i < ncChatlist.getCnt(); i++) {
      long threadId = ncChatlist.getChatId(i);
      if (threadId > NcChat.NC_CHAT_ID_LAST_SPECIAL) {
        batchSet.add(threadId);
      }
    }
    notifyDataSetChanged();
  }

  interface ItemClickListener {
    void onItemClick(ConversationListItem item);

    void onItemLongClick(ConversationListItem item);

    void onSwitchToArchive();
  }

  void changeData(@Nullable NcChatlist chatlist) {
    Context context = this.context.get();
    if (context == null) {
      return;
    }
    if (chatlist == null) {
      ncChatlist = new NcChatlist(0, 0);
    } else {
      ncChatlist = chatlist;
      ncContext = NcHelper.getContext(context);
    }
    notifyDataSetChanged();
  }
}

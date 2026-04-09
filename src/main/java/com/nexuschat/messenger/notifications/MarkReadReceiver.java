// called when the user click the "clear" or "mark read" button in the system notification

package com.nexuschat.messenger.notifications;

import static com.b44t.messenger.NcChat.NC_CHAT_NO_CHAT;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.b44t.messenger.NcContext;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.util.Util;

public class MarkReadReceiver extends BroadcastReceiver {
  public static final String MARK_NOTICED_ACTION =
      "com.nexuschat.messenger.notifications.MARK_NOTICED";
  public static final String CANCEL_ACTION = "com.nexuschat.messenger.notifications.CANCEL";
  public static final String ACCOUNT_ID_EXTRA = "account_id";
  public static final String CHAT_ID_EXTRA = "chat_id";
  public static final String MSG_ID_EXTRA = "msg_id";

  @Override
  public void onReceive(final Context context, Intent intent) {
    boolean markNoticed = MARK_NOTICED_ACTION.equals(intent.getAction());
    if (!markNoticed && !CANCEL_ACTION.equals(intent.getAction())) {
      return;
    }

    final int accountId = intent.getIntExtra(ACCOUNT_ID_EXTRA, 0);
    final int chatId = intent.getIntExtra(CHAT_ID_EXTRA, NC_CHAT_NO_CHAT);
    final int msgId = intent.getIntExtra(MSG_ID_EXTRA, 0);
    if (accountId == 0 || chatId == NC_CHAT_NO_CHAT) {
      return;
    }

    Util.runOnAnyBackgroundThread(
        () -> {
          NcHelper.getNotificationCenter(context).removeNotifications(accountId, chatId);
          if (markNoticed) {
            NcContext ncContext = NcHelper.getAccounts(context).getAccount(accountId);
            ncContext.marknoticedChat(chatId);
            ncContext.markseenMsgs(new int[] {msgId});
          }
        });
  }
}

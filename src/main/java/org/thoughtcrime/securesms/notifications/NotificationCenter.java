package org.thoughtcrime.securesms.notifications;

import static org.thoughtcrime.securesms.connect.DcHelper.CONFIG_PRIVATE_TAG;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;
import androidx.core.app.TaskStackBuilder;
import com.b44t.messenger.DcChat;
import com.b44t.messenger.DcContact;
import com.b44t.messenger.DcContext;
import com.b44t.messenger.DcMsg;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.ConversationActivity;
import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.IntentUtils;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.Pair;
import org.thoughtcrime.securesms.util.Prefs;
import org.thoughtcrime.securesms.util.Util;

public class NotificationCenter {
  private static final String TAG = NotificationCenter.class.getSimpleName();
  @NonNull private final ApplicationContext context;
  private volatile ChatData visibleChat = null;
  private volatile Pair<Integer, Integer> visibleWebxnc = null;
  private volatile long lastAudibleNotification = 0;
  private static final long MIN_AUDIBLE_PERIOD_MILLIS = TimeUnit.SECONDS.toMillis(2);

  // Map<accountId, Map<chatId, lines>, contains the last lines of each chat for each account
  private final HashMap<Integer, HashMap<Integer, LinkedHashMap<Integer, String>>> inboxes =
      new HashMap<>();

  public NotificationCenter(Context context) {
    this.context = ApplicationContext.getInstance(context);
  }

  private @Nullable Uri effectiveSound(
      ChatData chatData) { // chatData=null: return app-global setting
    if (chatData == null) {
      chatData = new ChatData(0, 0);
    }
    @Nullable
    Uri chatRingtone = Prefs.getChatRingtone(context, chatData.accountId, chatData.chatId);
    if (chatRingtone != null) {
      return chatRingtone;
    } else {
      @NonNull Uri appDefaultRingtone = Prefs.getNotificationRingtone(context);
      if (!TextUtils.isEmpty(appDefaultRingtone.toString())) {
        return appDefaultRingtone;
      }
    }
    return null;
  }

  private boolean effectiveVibrate(ChatData chatData) { // chatData=null: return app-global setting
    if (chatData == null) {
      chatData = new ChatData(0, 0);
    }
    Prefs.VibrateState vibrate = Prefs.getChatVibrate(context, chatData.accountId, chatData.chatId);
    if (vibrate == Prefs.VibrateState.ENABLED) {
      return true;
    } else if (vibrate == Prefs.VibrateState.DISABLED) {
      return false;
    }
    return Prefs.isNotificationVibrateEnabled(context);
  }

  private boolean requiresIndependentChannel(ChatData chatData) {
    if (chatData == null) {
      chatData = new ChatData(0, 0);
    }
    return Prefs.getChatRingtone(context, chatData.accountId, chatData.chatId) != null
        || Prefs.getChatVibrate(context, chatData.accountId, chatData.chatId)
            != Prefs.VibrateState.DEFAULT;
  }

  private int getLedArgb(String ledColor) {
    int argb;
    try {
      argb = Color.parseColor(ledColor);
    } catch (Exception e) {
      argb = Color.rgb(0xFF, 0xFF, 0xFF);
    }
    return argb;
  }

  private PendingIntent getOpenChatlistIntent(int accountId) {
    Intent intent = new Intent(context, ConversationListActivity.class);
    intent.putExtra(ConversationListActivity.ACCOUNT_ID_EXTRA, accountId);
    intent.putExtra(ConversationListActivity.CLEAR_NOTIFICATIONS, true);
    intent.setData(Uri.parse("custom://" + accountId));
    return PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | IntentUtils.FLAG_MUTABLE());
  }

  private PendingIntent getOpenChatIntent(ChatData chatData) {
    Intent intent = new Intent(context, ConversationActivity.class);
    intent.putExtra(ConversationActivity.ACCOUNT_ID_EXTRA, chatData.accountId);
    intent.putExtra(ConversationActivity.CHAT_ID_EXTRA, chatData.chatId);
    intent.setData(Uri.parse("custom://" + chatData.accountId + "." + chatData.chatId));
    return TaskStackBuilder.create(context)
        .addNextIntentWithParentStack(intent)
        .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT | IntentUtils.FLAG_MUTABLE());
  }

  private PendingIntent getRemoteReplyIntent(ChatData chatData, int msgId) {
    Intent intent = new Intent(RemoteReplyReceiver.REPLY_ACTION);
    intent.setClass(context, RemoteReplyReceiver.class);
    intent.setData(Uri.parse("custom://" + chatData.accountId + "." + chatData.chatId));
    intent.putExtra(RemoteReplyReceiver.ACCOUNT_ID_EXTRA, chatData.accountId);
    intent.putExtra(RemoteReplyReceiver.CHAT_ID_EXTRA, chatData.chatId);
    intent.putExtra(RemoteReplyReceiver.MSG_ID_EXTRA, msgId);
    intent.setPackage(context.getPackageName());
    return PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | IntentUtils.FLAG_MUTABLE());
  }

  private PendingIntent getMarkAsReadIntent(ChatData chatData, int msgId, boolean markNoticed) {
    Intent intent =
        new Intent(
            markNoticed ? MarkReadReceiver.MARK_NOTICED_ACTION : MarkReadReceiver.CANCEL_ACTION);
    intent.setClass(context, MarkReadReceiver.class);
    intent.setData(Uri.parse("custom://" + chatData.accountId + "." + chatData.chatId));
    intent.putExtra(MarkReadReceiver.ACCOUNT_ID_EXTRA, chatData.accountId);
    intent.putExtra(MarkReadReceiver.CHAT_ID_EXTRA, chatData.chatId);
    intent.putExtra(MarkReadReceiver.MSG_ID_EXTRA, msgId);
    intent.setPackage(context.getPackageName());
    return PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | IntentUtils.FLAG_MUTABLE());
  }

  // Groups and Notification channel groups
  // --------------------------------------------------------------------------------------------

  // this is just to further organize the appearance of channels in the settings UI
  private static final String CH_GRP_MSG = "chgrp_msg";

  // this is to group together notifications as such, maybe including a summary,
  // see https://developer.android.com/training/notify-user/group.html
  private static final String GRP_MSG = "grp_msg";

  // Notification IDs
  // --------------------------------------------------------------------------------------------

  public static final int ID_PERMANENT = 1;
  public static final int ID_MSG_SUMMARY = 2;
  public static final int ID_GENERIC = 3;
  public static final int ID_FETCH = 4;
  public static final int ID_MSG_OFFSET =
      0; // msgId is added - as msgId start at 10, there are no conflicts with lower numbers

  // Notification channels
  // --------------------------------------------------------------------------------------------

  // Overview:
  // - since SDK 26 (Oreo), a NotificationChannel is a MUST for notifications
  // - NotificationChannels are defined by a channelId
  //   and its user-editable settings have a higher precedence as the Notification.Builder setting
  // - once created, NotificationChannels cannot be modified programmatically
  // - NotificationChannels can be deleted, however, on re-creation with the same id,
  //   it becomes un-deleted with the old user-defined settings
  //
  // How we use Notification channel:
  // - We include the nexus-chat-notifications settings into the name of the channelId
  // - The chatId is included only, if there are separate sound- or vibration-settings for a chat
  // - This way, we have stable and few channelIds and the user
  //   can edit the notifications in Nexus Chat as well as in the system

  // channelIds: CH_MSG_* are used here, the other ones from outside (defined here to have some
  // overview)
  public static final String CH_MSG_PREFIX = "ch_msg";
  public static final String CH_MSG_VERSION = "5";
  public static final String CH_PERMANENT = "nc_fg_notification_ch";
  public static final String CH_GENERIC = "ch_generic";
  public static final String CH_CALLS_PREFIX = "call_chan";

  private boolean notificationChannelsSupported() {
    return Build.VERSION.SDK_INT >= 26;
  }

  /**
   * CAMBIO: Método para crear/obtener el canal de notificación permanente (servicio en segundo plano)
   * Con IMPORTANCE_MIN para que NO sea visible al usuario pero el servicio siga funcionando
   */
  public String getPermanentNotificationChannel(NotificationManagerCompat notificationManager) {
    if (notificationChannelsSupported()) {
      NotificationChannel channel = notificationManager.getNotificationChannel(CH_PERMANENT);
      if (channel == null) {
        // CAMBIO IMPORTANTE: Usar IMPORTANCE_MIN para que la notificación sea prácticamente invisible
        channel = new NotificationChannel(
            CH_PERMANENT,
            "Servicio Nexus Chat",
            NotificationManager.IMPORTANCE_MIN
        );
        // Configurar para que no moleste en absoluto
        channel.setSound(null, null);           // Sin sonido
        channel.enableVibration(false);         // Sin vibración
        channel.setShowBadge(false);            // Sin insignia
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET); // No mostrar en pantalla de bloqueo
        channel.setDescription("Mantiene Nexus Chat conectado en segundo plano");
        notificationManager.createNotificationChannel(channel);
      }
    }
    return CH_PERMANENT;
  }

  // full name is "ch_msgV_HASH" or "ch_msgV_HASH.ACCOUNTID.CHATID"
  private String computeChannelId(
      String ledColor, boolean vibrate, @Nullable Uri ringtone, ChatData chatData) {
    String channelId = CH_MSG_PREFIX;
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(ledColor.getBytes());
      md.update(vibrate ? (byte) 1 : (byte) 0);
      md.update((ringtone != null ? ringtone.toString() : "").getBytes());
      String hash = String.format("%X", new BigInteger(1, md.digest())).substring(0, 16);

      channelId = CH_MSG_PREFIX + CH_MSG_VERSION + "_" + hash;
      if (chatData != null) {
        channelId += String.format(".%d.%d", chatData.accountId, chatData.chatId);
      }

    } catch (Exception e) {
      Log.e(TAG, e.toString());
    }
    return channelId;
  }

  // return ChatData(ACCOUNTID, CHATID) from "ch_msgV_HASH.ACCOUNTID.CHATID" or null
  private ChatData parseNotificationChannelChat(String channelId) {
    try {
      int point = channelId.lastIndexOf(".");
      if (point > 0) {
        int chatId = Integer.parseInt(channelId.substring(point + 1));
        channelId = channelId.substring(0, point);
        point = channelId.lastIndexOf(".");
        if (point > 0) {
          int accountId = Integer.parseInt(channelId.substring(point + 1));
          return new ChatData(accountId, chatId);
        }
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private String getNotificationChannelGroup(NotificationManagerCompat notificationManager) {
    if (notificationChannelsSupported()
        && notificationManager.getNotificationChannelGroup(CH_GRP_MSG) == null) {
      NotificationChannelGroup chGrp =
          new NotificationChannelGroup(CH_GRP_MSG, context.getString(R.string.pref_chats));
      notificationManager.createNotificationChannelGroup(chGrp);
    }
    return CH_GRP_MSG;
  }

  private String getNotificationChannel(
      NotificationManagerCompat notificationManager, ChatData chatData, DcChat dcChat) {
    String channelId = CH_MSG_PREFIX;

    if (notificationChannelsSupported()) {
      try {
        // get all values we'll use as settings for the NotificationChannel
        String ledColor = Prefs.getNotificationLedColor(context);
        boolean defaultVibrate = effectiveVibrate(chatData);
        @Nullable Uri ringtone = effectiveSound(chatData);
        boolean isIndependent = requiresIndependentChannel(chatData);

        // get channel id from these settings
        channelId =
            computeChannelId(ledColor, defaultVibrate, ringtone, isIndependent ? chatData : null);

        // user-visible name of the channel -
        // we just use the name of the chat or "Default"
        // (the name is shown in the context of the group "Chats" - that should be enough context)
        String name = context.getString(R.string.def);
        if (isIndependent) {
          name = dcChat.getName();
        }

        // check if there is already a channel with the given name
        List<NotificationChannel> channels = notificationManager.getNotificationChannels();
        boolean channelExists = false;
        for (int i = 0; i < channels.size(); i++) {
          String currChannelId = channels.get(i).getId();
          if (currChannelId.startsWith(CH_MSG_PREFIX)) {
            // this is one of the message channels handled here ...
            if (currChannelId.equals(channelId)) {
              // ... this is the actually required channel, fine :)
              // update the name to reflect localize changes and chat renames
              channelExists = true;
              channels.get(i).setName(name);
            } else {
              // ... another message channel, delete if it is not in use.
              ChatData currChat = parseNotificationChannelChat(currChannelId);
              if (!currChannelId.equals(
                  computeChannelId(
                      ledColor, effectiveVibrate(currChat), effectiveSound(currChat), currChat))) {
                notificationManager.deleteNotificationChannel(currChannelId);
              }
            }
          }
        }

        // create a channel with the given settings;
        // we cannot change the settings, however, this is handled by using different values for
        // chId
        if (!channelExists) {
          NotificationChannel channel =
              new NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH);
          channel.setDescription("Informs about new messages.");
          channel.setGroup(getNotificationChannelGroup(notificationManager));
          channel.enableVibration(defaultVibrate);
          channel.setShowBadge(true);

          if (!ledColor.equals("none")) {
            channel.enableLights(true);
            channel.setLightColor(getLedArgb(ledColor));
          } else {
            channel.enableLights(false);
          }

          if (ringtone != null && !TextUtils.isEmpty(ringtone.toString())) {
            channel.setSound(
                ringtone,
                new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                    .build());
          } else {
            channel.setSound(null, null);
          }

          notificationManager.createNotificationChannel(channel);
        }
      } catch (Exception e) {
        Log.e(TAG, "Error in getNotificationChannel()", e);
      }
    }

    return channelId;
  }

  public String getCallNotificationChannel(
      NotificationManagerCompat notificationManager, ChatData chatData, String name) {
    String channelId = CH_CALLS_PREFIX + "-" + chatData.accountId + "-" + chatData.chatId;

    if (notificationChannelsSupported()) {
      try {
        name = "(calls) " + name;

        // check if there is already a channel with the given name
        List<NotificationChannel> channels = notificationManager.getNotificationChannels();
        boolean channelExists = false;
        for (int i = 0; i < channels.size(); i++) {
          String currChannelId = channels.get(i).getId();
          if (currChannelId.startsWith(CH_CALLS_PREFIX)) {
            // this is one of the calls channels handled here ...
            if (currChannelId.equals(channelId)) {
              // ... this is the actually required channel, fine :)
              // update the name to reflect localize changes and chat renames
              channelExists = true;
              channels.get(i).setName(name);
            }
          }
        }

        // create the channel
        if (!channelExists) {
          NotificationChannel channel =
              new NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_MAX);
          channel.setDescription("Informs about incoming calls.");
          channel.setShowBadge(true);

          Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
          channel.setSound(
              ringtone,
              new AudioAttributes.Builder()
                  .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                  .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                  .build());
          notificationManager.createNotificationChannel(channel);
        }
      } catch (Exception e) {
        Log.e(TAG, "Error in getCallNotificationChannel()", e);
      }
    }

    return channelId;
  }

  // add notifications & co.
  // --------------------------------------------------------------------------------------------

  public void notifyMessage(int accountId, int chatId, int msgId) {
    Util.runOnAnyBackgroundThread(
        () -> {
          DcContext dcContext = context.getDcAccounts().getAccount(accountId);
          DcChat dcChat = dcContext.getChat(chatId);

          DcMsg dcMsg = dcContext.getMsg(msgId);
          NotificationPrivacyPreference privacy = Prefs.getNotificationPrivacy(context);

          String shortLine =
              privacy.isDisplayMessage()
                  ? dcMsg.getSummarytext(2000)
                  : context.getString(R.string.notify_new_message);
          if (dcChat.isMultiUser() && privacy.isDisplayContact()) {
            shortLine =
                dcMsg.getSenderName(dcContext.getContact(dcMsg.getFromId())) + ": " + shortLine;
          }
          String tickerLine = shortLine;
          if (!dcChat.isMultiUser() && privacy.isDisplayContact()) {
            tickerLine =
                dcMsg.getSenderName(dcContext.getContact(dcMsg.getFromId())) + ": " + tickerLine;

            if (dcMsg.getOverrideSenderName() != null) {
              // There is an "overridden" display name on the message, so, we need to prepend the
              // display name to the message,
              // i.e. set the shortLine to be the same as the tickerLine.
              shortLine = tickerLine;
            }
          }

          DcMsg quotedMsg = dcMsg.getQuotedMsg();
          boolean isMention = dcChat.isMultiUser() && quotedMsg != null && quotedMsg.isOutgoing();

          //maybeAddNotification(accountId, dcChat, msgId, shortLine, tickerLine, true, isMention);
        });
  }

  public void notifyReaction(int accountId, int contactId, int msgId, String reaction) {
    Util.runOnAnyBackgroundThread(
        () -> {
          DcContext dcContext = context.getDcAccounts().getAccount(accountId);
          DcMsg dcMsg = dcContext.getMsg(msgId);

          NotificationPrivacyPreference privacy = Prefs.getNotificationPrivacy(context);
          if (!privacy.isDisplayContact() || !privacy.isDisplayMessage()) {
            return; // showing "New Message" is wrong and showing "New Reaction" is already content.
            // just do nothing.
          }

          DcContact sender = dcContext.getContact(contactId);
          String shortLine =
              context.getString(
                  R.string.reaction_by_other,
                  sender.getDisplayName(),
                  reaction,
                  dcMsg.getSummarytext(2000));
          DcChat dcChat = dcContext.getChat(dcMsg.getChatId());
          //maybeAddNotification(
              //accountId, dcChat, msgId, shortLine, shortLine, false, dcChat.isMultiUser());
        });
  }

  public void notifyWebxnc(int accountId, int contactId, int msgId, String text) {
    Util.runOnAnyBackgroundThread(
        () -> {
          NotificationPrivacyPreference privacy = Prefs.getNotificationPrivacy(context);
          if (!privacy.isDisplayContact() || !privacy.isDisplayMessage()) {
            return; // showing "New Message" is wrong and showing "New Reaction" is already content.
            // just do nothing.
          }

          // ... resto del código ...
        });
  }

  // ... el resto de los métodos de la clase continúan sin cambios ...
}
package com.nexuschat.messenger;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode;
import androidx.core.util.Consumer;
import androidx.fragment.app.Fragment;
import com.b44t.messenger.NcChat;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcMsg;
import java.util.Set;
import com.nexuschat.messenger.connect.NcEventCenter;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.permissions.Permissions;
import com.nexuschat.messenger.util.SaveAttachmentTask;
import com.nexuschat.messenger.util.StorageUtil;
import com.nexuschat.messenger.util.Util;

public abstract class MessageSelectorFragment extends Fragment
    implements NcEventCenter.NcEventDelegate {
  protected ActionMode actionMode;

  protected abstract void setCorrectMenuVisibility(Menu menu);

  protected ActionMode getActionMode() {
    return actionMode;
  }

  protected NcMsg getSelectedMessageRecord(Set<NcMsg> messageRecords) {
    if (messageRecords.size() == 1) return messageRecords.iterator().next();
    else throw new AssertionError();
  }

  protected void handleDisplayDetails(NcMsg ncMsg) {
    View view = View.inflate(getActivity(), R.layout.message_details_view, null);
    TextView detailsText = view.findViewById(R.id.details_text);
    detailsText.setText(NcHelper.getContext(getContext()).getMsgInfo(ncMsg.getId()));

    AlertDialog d =
        new AlertDialog.Builder(getActivity())
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .create();
    d.show();
  }

  protected void handleDeleteMessages(int chatId, final Set<NcMsg> messageRecords) {
    handleDeleteMessages(chatId, NcMsg.msgSetToIds(messageRecords), null, null);
  }

  protected void handleDeleteMessages(
      int chatId,
      final Set<NcMsg> messageRecords,
      Consumer<int[]> deleteForMeListenerExtra,
      Consumer<int[]> deleteForAllListenerExtra) {
    handleDeleteMessages(
        chatId,
        NcMsg.msgSetToIds(messageRecords),
        deleteForMeListenerExtra,
        deleteForAllListenerExtra);
  }

  protected void handleDeleteMessages(
      int chatId,
      final int[] messageIds,
      Consumer<int[]> deleteForMeListenerExtra,
      Consumer<int[]> deleteForAllListenerExtra) {
    NcContext ncContext = NcHelper.getContext(getContext());
    NcChat ncChat = ncContext.getChat(chatId);
    boolean canDeleteForAll = true;
    if (ncChat.isEncrypted() && ncChat.canSend() && !ncChat.isSelfTalk()) {
      for (int msgId : messageIds) {
        NcMsg msg = ncContext.getMsg(msgId);
        if (!msg.isOutgoing() || msg.isInfo()) {
          canDeleteForAll = false;
          break;
        }
      }
    } else {
      canDeleteForAll = false;
    }

    String text =
        getActivity()
            .getResources()
            .getQuantityString(R.plurals.ask_delete_messages, messageIds.length, messageIds.length);
    int positiveBtnLabel = ncChat.isSelfTalk() ? R.string.delete : R.string.delete_for_me;

    DialogInterface.OnClickListener deleteForMeListener =
        (d, which) -> {
          Util.runOnAnyBackgroundThread(() -> ncContext.deleteMsgs(messageIds));
          if (actionMode != null) actionMode.finish();
          if (deleteForMeListenerExtra != null) deleteForMeListenerExtra.accept(messageIds);
        };
    AlertDialog.Builder builder =
        new AlertDialog.Builder(requireActivity())
            .setMessage(text)
            .setCancelable(true)
            .setNeutralButton(android.R.string.cancel, null)
            .setPositiveButton(positiveBtnLabel, deleteForMeListener);

    if (canDeleteForAll) {
      DialogInterface.OnClickListener deleteForAllListener =
          (d, which) -> {
            Util.runOnAnyBackgroundThread(() -> ncContext.sendDeleteRequest(messageIds));
            if (actionMode != null) actionMode.finish();
            if (deleteForAllListenerExtra != null) deleteForAllListenerExtra.accept(messageIds);
          };
      builder.setNegativeButton(R.string.delete_for_everyone, deleteForAllListener);
      AlertDialog dialog = builder.show();
      Util.redButton(dialog, AlertDialog.BUTTON_NEGATIVE);
      Util.redPositiveButton(dialog);
    } else {
      AlertDialog dialog = builder.show();
      Util.redPositiveButton(dialog);
    }
  }

  protected void handleSaveAttachment(final Set<NcMsg> messageRecords) {
    SaveAttachmentTask.showWarningDialog(
        getContext(),
        (dialogInterface, i) -> {
          if (StorageUtil.canWriteToMediaStore(getContext())) {
            performSave(messageRecords);
            return;
          }

          Permissions.with(getActivity())
              .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
              .alwaysGrantOnSdk30()
              .ifNecessary()
              .withPermanentDenialDialog(getString(R.string.perm_explain_access_to_storage_denied))
              .onAllGranted(() -> performSave(messageRecords))
              .execute();
        });
  }

  private void performSave(Set<NcMsg> messageRecords) {
    SaveAttachmentTask.Attachment[] attachments =
        new SaveAttachmentTask.Attachment[messageRecords.size()];
    int index = 0;
    for (NcMsg message : messageRecords) {
      attachments[index] =
          new SaveAttachmentTask.Attachment(
              Uri.fromFile(message.getFileAsFile()),
              message.getFilemime(),
              message.getDateReceived(),
              message.getFilename());
      index++;
    }
    SaveAttachmentTask saveTask = new SaveAttachmentTask(getContext());
    saveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, attachments);
    if (actionMode != null) actionMode.finish();
  }

  protected void handleShowInChat(final NcMsg ncMsg) {
    Intent intent = new Intent(getContext(), ConversationActivity.class);
    intent.putExtra(ConversationActivity.CHAT_ID_EXTRA, ncMsg.getChatId());
    intent.putExtra(
        ConversationActivity.STARTING_POSITION_EXTRA,
        NcMsg.getMessagePosition(ncMsg, NcHelper.getContext(getContext())));
    startActivity(intent);
  }

  protected void handleShare(final NcMsg ncMsg) {
    NcHelper.openForViewOrShare(getContext(), ncMsg.getId(), Intent.ACTION_SEND);
  }

  protected void handleResendMessage(final Set<NcMsg> ncMsgsSet) {
    int[] ids = NcMsg.msgSetToIds(ncMsgsSet);
    NcContext ncContext = NcHelper.getContext(getContext());
    Util.runOnAnyBackgroundThread(
        () -> {
          boolean success = ncContext.resendMsgs(ids);
          Util.runOnMain(
              () -> {
                Activity activity = getActivity();
                if (activity == null || activity.isFinishing()) return;
                if (success) {
                  actionMode.finish();
                  Toast.makeText(getContext(), R.string.sending, Toast.LENGTH_SHORT).show();
                } else {
                  new AlertDialog.Builder(activity)
                      .setMessage(ncContext.getLastError())
                      .setCancelable(false)
                      .setPositiveButton(android.R.string.ok, null)
                      .show();
                }
              });
        });
  }
}

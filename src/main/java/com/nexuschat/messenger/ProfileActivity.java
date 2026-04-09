package com.nexuschat.messenger;

import android.app.Activity;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import chat.nexus.rpc.Rpc;
import chat.nexus.rpc.RpcException;
import com.b44t.messenger.NcChat;
import com.b44t.messenger.NcContact;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcEvent;
import java.io.File;
import com.nexuschat.messenger.connect.NcEventCenter;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.util.DynamicNoActionBarTheme;
import com.nexuschat.messenger.util.Prefs;
import com.nexuschat.messenger.util.ShareUtil;
import com.nexuschat.messenger.util.Util;
import com.nexuschat.messenger.util.ViewUtil;

public class ProfileActivity extends PassphraseRequiredActionBarActivity
    implements NcEventCenter.NcEventDelegate {

  public static final String CHAT_ID_EXTRA = "chat_id";
  public static final String CONTACT_ID_EXTRA = "contact_id";

  private static final int REQUEST_CODE_PICK_RINGTONE = 1;

  private NcContext ncContext;
  private Rpc rpc;
  private int chatId;
  private boolean chatIsMultiUser;
  private boolean chatIsDeviceTalk;
  private boolean chatIsMailingList;
  private boolean chatIsOutBroancast;
  private boolean chatIsInBroancast;
  private int contactId;
  private boolean contactIsBot;
  private Toolbar toolbar;

  @Override
  protected void onPreCreate() {
    dynamicTheme = new DynamicNoActionBarTheme();
    super.onPreCreate();
    ncContext = NcHelper.getContext(this);
    rpc = NcHelper.getRpc(this);
  }

  @Override
  protected void onCreate(Bundle bundle, boolean ready) {
    setContentView(R.layout.profile_activity);

    initializeResources();

    setSupportActionBar(this.toolbar);
    ActionBar supportActionBar = getSupportActionBar();
    if (supportActionBar != null) {
      String title = getString(R.string.profile);
      if (chatIsMailingList) {
        title = getString(R.string.mailing_list);
      } else if (chatIsOutBroancast || chatIsInBroancast) {
        title = getString(R.string.channel);
      } else if (chatIsMultiUser) {
        title = getString(R.string.tab_group);
      } else if (contactIsBot) {
        title = getString(R.string.bot);
      } else if (!chatIsDeviceTalk && !isSelfProfile()) {
        title = getString(R.string.tab_contact);
      }

      supportActionBar.setDisplayHomeAsUpEnabled(true);
      supportActionBar.setTitle(title);
    }

    Bundle args = new Bundle();
    args.putInt(ProfileFragment.CHAT_ID_EXTRA, (chatId == 0) ? -1 : chatId);
    args.putInt(ProfileFragment.CONTACT_ID_EXTRA, (contactId == 0) ? -1 : contactId);
    initFragment(R.id.fragment_container, new ProfileFragment(), args);

    NcEventCenter eventCenter = NcHelper.getEventCenter(this);
    eventCenter.addObserver(NcContext.NC_EVENT_CHAT_MODIFIED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_CONTACTS_CHANGED, this);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    if (!isSelfProfile()) {
      getMenuInflater().inflate(R.menu.profile_common, menu);
      boolean canReceive = true;

      if (chatId != 0) {
        NcChat ncChat = ncContext.getChat(chatId);
        menu.findItem(R.id.menu_clone)
            .setVisible(
                chatIsMultiUser && !chatIsInBroancast && !chatIsOutBroancast && !chatIsMailingList);
        if (chatIsDeviceTalk) {
          menu.findItem(R.id.edit_name).setVisible(false);
          menu.findItem(R.id.show_encr_info).setVisible(false);
          menu.findItem(R.id.share).setVisible(false);
        } else if (chatIsMultiUser) {
          menu.findItem(R.id.edit_name).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
          if (chatIsOutBroancast) {
            canReceive = false;
          } else {
            if (!ncChat.isEncrypted() || !ncChat.canSend() || chatIsMailingList) {
              menu.findItem(R.id.edit_name).setVisible(false);
            }
          }
          menu.findItem(R.id.share).setVisible(false);
        }
      } else {
        menu.findItem(R.id.menu_clone).setVisible(false);
        canReceive = false;
      }

      if (!canReceive) {
        menu.findItem(R.id.menu_mute_notifications).setVisible(false);
        menu.findItem(R.id.menu_sound).setVisible(false);
        menu.findItem(R.id.menu_vibrate).setVisible(false);
      }

      if (isContactProfile()) {
        menu.findItem(R.id.edit_name).setTitle(R.string.menu_edit_name);
      }

      if (!isContactProfile() || chatIsDeviceTalk) {
        menu.findItem(R.id.block_contact).setVisible(false);
      }
    }

    super.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuItem item = menu.findItem(R.id.block_contact);
    if (item != null) {
      item.setTitle(
          ncContext.getContact(contactId).isBlocked()
              ? R.string.menu_unblock_contact
              : R.string.menu_block_contact);
      Util.redMenuItem(menu, R.id.block_contact);
    }

    item = menu.findItem(R.id.menu_mute_notifications);
    if (item != null) {
      item.setTitle(
          ncContext.getChat(chatId).isMuted() ? R.string.menu_unmute : R.string.menu_mute);
    }

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    getMenuInflater().inflate(R.menu.profile_title_context, menu);
  }

  @Override
  public void onDestroy() {
    NcHelper.getEventCenter(this).removeObservers(this);
    super.onDestroy();
  }

  @Override
  public void handleEvent(@NonNull NcEvent event) {}

  private void initializeResources() {
    chatId = getIntent().getIntExtra(CHAT_ID_EXTRA, 0);
    contactId = getIntent().getIntExtra(CONTACT_ID_EXTRA, 0);
    contactIsBot = false;
    chatIsMultiUser = false;
    chatIsDeviceTalk = false;
    chatIsMailingList = false;
    chatIsInBroancast = false;
    chatIsOutBroancast = false;

    if (contactId != 0) {
      NcContact ncContact = ncContext.getContact(contactId);
      chatId = ncContext.getChatIdByContactId(contactId);
      contactIsBot = ncContact.isBot();
    }

    if (chatId != 0) {
      NcChat ncChat = ncContext.getChat(chatId);
      chatIsMultiUser = ncChat.isMultiUser();
      chatIsDeviceTalk = ncChat.isDeviceTalk();
      chatIsMailingList = ncChat.isMailingList();
      chatIsInBroancast = ncChat.isInBroancast();
      chatIsOutBroancast = ncChat.isOutBroancast();
      if (!chatIsMultiUser) {
        final int[] members = ncContext.getChatContacts(chatId);
        contactId = members.length >= 1 ? members[0] : 0;
      }
    }

    this.toolbar = ViewUtil.findById(this, R.id.toolbar);
  }

  private boolean isContactProfile() {
    // contact-profiles are profiles without a chat or with a one-to-one chat
    return contactId != 0 && (chatId == 0 || !chatIsMultiUser);
  }

  private boolean isSelfProfile() {
    return isContactProfile() && contactId == NcContact.NC_CONTACT_ID_SELF;
  }

  // handle events
  // =========================================================================

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    super.onOptionsItemSelected(item);

    int itemId = item.getItemId();
    if (itemId == android.R.id.home) {
      finish();
      return true;
    } else if (itemId == R.id.menu_mute_notifications) {
      onNotifyOnOff();
    } else if (itemId == R.id.menu_sound) {
      onSoundSettings();
    } else if (itemId == R.id.menu_vibrate) {
      onVibrateSettings();
    } else if (itemId == R.id.edit_name) {
      onEditName();
    } else if (itemId == R.id.share) {
      onShare();
    } else if (itemId == R.id.show_encr_info) {
      onEncrInfo();
    } else if (itemId == R.id.block_contact) {
      onBlockContact();
    } else if (itemId == R.id.menu_clone) {
      onClone();
    }

    return false;
  }

  @Override
  public boolean onContextItemSelected(@NonNull MenuItem item) {
    super.onContextItemSelected(item);
    if (item.getItemId() == R.id.copy_addr_to_clipboard) {
      onCopyAddrToClipboard();
    }
    return false;
  }

  private void onNotifyOnOff() {
    if (ncContext.getChat(chatId).isMuted()) {
      setMuted(0);
    } else {
      MuteDialog.show(this, this::setMuted);
    }
  }

  private void setMuted(final long duration) {
    if (chatId != 0) {
      ncContext.setChatMuteDuration(chatId, duration);
    }
  }

  private void onSoundSettings() {
    Uri current = Prefs.getChatRingtone(this, ncContext.getAccountId(), chatId);
    Uri defaultUri = Prefs.getNotificationRingtone(this);

    if (current == null) current = Settings.System.DEFAULT_NOTIFICATION_URI;
    else if (current.toString().isEmpty()) current = null;

    Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultUri);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current);

    startActivityForResult(intent, REQUEST_CODE_PICK_RINGTONE);
  }

  private void onVibrateSettings() {
    int checkedItem = Prefs.getChatVibrate(this, ncContext.getAccountId(), chatId).getId();
    int[] selectedChoice = new int[] {checkedItem};
    new AlertDialog.Builder(this)
        .setTitle(R.string.pref_vibrate)
        .setSingleChoiceItems(
            R.array.recipient_vibrate_entries,
            checkedItem,
            (dialog, which) -> selectedChoice[0] = which)
        .setPositiveButton(
            R.string.ok,
            (dialog, which) ->
                Prefs.setChatVibrate(
                    this,
                    ncContext.getAccountId(),
                    chatId,
                    Prefs.VibrateState.fromId(selectedChoice[0])))
        .setNegativeButton(R.string.cancel, null)
        .show();
  }

  public void onEnlargeAvatar() {
    String profileImagePath;
    String title;
    Uri profileImageUri;
    boolean enlargeAvatar = true;
    if (chatId != 0) {
      NcChat ncChat = ncContext.getChat(chatId);
      profileImagePath = ncChat.getProfileImage();
      title = ncChat.getName();
      enlargeAvatar = ncChat.isEncrypted() && !ncChat.isSelfTalk() && !ncChat.isDeviceTalk();
    } else {
      NcContact ncContact = ncContext.getContact(contactId);
      profileImagePath = ncContact.getProfileImage();
      title = ncContact.getDisplayName();
    }

    File file = new File(profileImagePath);

    if (enlargeAvatar && file.exists()) {
      profileImageUri = Uri.fromFile(file);
      String type = "image/" + profileImagePath.substring(profileImagePath.lastIndexOf(".") + 1);

      Intent intent = new Intent(this, MediaPreviewActivity.class);
      intent.setDataAndType(profileImageUri, type);
      intent.putExtra(MediaPreviewActivity.ACTIVITY_TITLE_EXTRA, title);
      intent.putExtra( // show edit-button, if the user is allowed to edit the name/avatar
          MediaPreviewActivity.EDIT_AVATAR_CHAT_ID,
          (chatIsMultiUser && !chatIsInBroancast && !chatIsMailingList) ? chatId : 0);
      startActivity(intent);
    } else if (chatIsMultiUser) {
      onEditName();
    }
  }

  private void onEditName() {
    if (chatIsMultiUser) {
      NcChat ncChat = ncContext.getChat(chatId);
      if (chatIsMailingList || ncChat.canSend()) {
        Intent intent = new Intent(this, GroupCreateActivity.class);
        intent.putExtra(GroupCreateActivity.EDIT_GROUP_CHAT_ID, chatId);
        startActivity(intent);
      }
    } else {
      int accountId = ncContext.getAccountId();
      NcContact ncContact = ncContext.getContact(contactId);

      String authName = ncContact.getAuthName();
      if (TextUtils.isEmpty(authName)) {
        authName = ncContact.getAddr();
      }

      View gl = View.inflate(this, R.layout.single_line_input, null);
      EditText inputField = gl.findViewById(R.id.input_field);
      inputField.setText(ncContact.getName());
      inputField.setSelection(inputField.getText().length());
      inputField.setHint(getString(R.string.edit_name_placeholder, authName));

      new AlertDialog.Builder(this)
          .setTitle(R.string.menu_edit_name)
          .setMessage(getString(R.string.edit_name_explain, authName))
          .setView(gl)
          .setPositiveButton(
              android.R.string.ok,
              (dialog, whichButton) -> {
                String newName = inputField.getText().toString();
                try {
                  rpc.changeContactName(accountId, contactId, newName);
                } catch (RpcException e) {
                  e.printStackTrace();
                }
              })
          .setNegativeButton(android.R.string.cancel, null)
          .setCancelable(false)
          .show();
    }
  }

  private void onShare() {
    Intent composeIntent = new Intent();
    NcContact ncContact = ncContext.getContact(contactId);
    if (ncContact.isKeyContact()) {
      ShareUtil.setSharedContactId(composeIntent, contactId);
    } else {
      ShareUtil.setSharedText(composeIntent, ncContact.getAddr());
    }
    ConversationListRelayingActivity.start(this, composeIntent);
  }

  private void onCopyAddrToClipboard() {
    NcContact ncContact = ncContext.getContact(contactId);
    Util.writeTextToClipboard(this, ncContact.getAddr());
    Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();
  }

  private void onEncrInfo() {
    String infoStr =
        isContactProfile()
            ? ncContext.getContactEncrInfo(contactId)
            : ncContext.getChatEncrInfo(chatId);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setMessage(infoStr)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    TextView messageView = dialog.findViewById(android.R.id.message);
    if (messageView != null) {
      messageView.setTextIsSelectable(true);
    }
  }

  private void onBlockContact() {
    NcContact ncContact = ncContext.getContact(contactId);
    if (ncContact.isBlocked()) {
      new AlertDialog.Builder(this)
          .setMessage(R.string.ask_unblock_contact)
          .setCancelable(true)
          .setNegativeButton(android.R.string.cancel, null)
          .setPositiveButton(
              R.string.menu_unblock_contact,
              (dialog, which) -> {
                ncContext.blockContact(contactId, 0);
              })
          .show();
    } else {
      AlertDialog dialog =
          new AlertDialog.Builder(this)
              .setMessage(R.string.ask_block_contact)
              .setCancelable(true)
              .setNegativeButton(android.R.string.cancel, null)
              .setPositiveButton(
                  R.string.menu_block_contact,
                  (d, which) -> {
                    ncContext.blockContact(contactId, 1);
                  })
              .show();
      Util.redPositiveButton(dialog);
    }
  }

  private void onClone() {
    Intent intent = new Intent(this, GroupCreateActivity.class);
    intent.putExtra(GroupCreateActivity.CLONE_CHAT_EXTRA, chatId);
    startActivity(intent);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_CODE_PICK_RINGTONE
        && resultCode == Activity.RESULT_OK
        && data != null) {
      Uri value = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
      Uri defaultValue = Prefs.getNotificationRingtone(this);

      if (defaultValue.equals(value)) value = null;
      else if (value == null) value = Uri.EMPTY;

      Prefs.setChatRingtone(this, ncContext.getAccountId(), chatId, value);
    }
  }
}

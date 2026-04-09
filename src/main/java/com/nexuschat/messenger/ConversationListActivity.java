/*
 * Copyright (C) 2014-2017 Open Whisper Systems
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

import static com.nexuschat.messenger.connect.NcHelper.CONFIG_PROXY_ENABLED;
import static com.nexuschat.messenger.connect.NcHelper.CONFIG_PROXY_URL;
import static com.nexuschat.messenger.util.ShareUtil.acquireRelayMessageContent;
import static com.nexuschat.messenger.util.ShareUtil.getDirectSharingChatId;
import static com.nexuschat.messenger.util.ShareUtil.getForwardedMessageAccountId;
import static com.nexuschat.messenger.util.ShareUtil.getSharedTitle;
import static com.nexuschat.messenger.util.ShareUtil.isDirectSharing;
import static com.nexuschat.messenger.util.ShareUtil.isForwarding;
import static com.nexuschat.messenger.util.ShareUtil.isRelayingMessageContent;
import static com.nexuschat.messenger.util.ShareUtil.resetRelayingMessageContent;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.TooltipCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import chat.nexus.rpc.types.SecurejoinSource;
import chat.nexus.rpc.types.SecurejoinUiPath;
import com.amulyakhare.textdrawable.TextDrawable;
import com.b44t.messenger.NcAccounts;
import com.b44t.messenger.NcContact;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcMsg;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import java.util.ArrayList;
import java.util.Date;
import com.nexuschat.messenger.components.AvatarView;
import com.nexuschat.messenger.components.SearchToolbar;
import com.nexuschat.messenger.connect.AccountManager;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.connect.DirectShareUtil;
import com.nexuschat.messenger.mms.GlideApp;
import com.nexuschat.messenger.permissions.Permissions;
import com.nexuschat.messenger.providers.PersistentBlobProvider;
import com.nexuschat.messenger.proxy.ProxySettingsActivity;
import com.nexuschat.messenger.qr.QrActivity;
import com.nexuschat.messenger.qr.QrCodeHandler;
import com.nexuschat.messenger.recipients.Recipient;
import com.nexuschat.messenger.search.SearchFragment;
import com.nexuschat.messenger.util.DynamicNoActionBarTheme;
import com.nexuschat.messenger.util.DynamicTheme;
import com.nexuschat.messenger.util.Prefs;
import com.nexuschat.messenger.util.SaveAttachmentTask;
import com.nexuschat.messenger.util.ScreenLockUtil;
import com.nexuschat.messenger.util.SendRelayedMessageUtil;
import com.nexuschat.messenger.util.ShareUtil;
import com.nexuschat.messenger.util.StorageUtil;
import com.nexuschat.messenger.util.Util;
import com.nexuschat.messenger.util.ViewUtil;

public class ConversationListActivity extends PassphraseRequiredActionBarActivity
    implements ConversationListFragment.ConversationSelectedListener {
  private static final String TAG = ConversationListActivity.class.getSimpleName();
  private static final String OPENPGP4FPR = "openpgp4fpr";
  private static final String NDK_ARCH_WARNED = "ndk_arch_warned";
  public static final String CLEAR_NOTIFICATIONS = "clear_notifications";
  public static final String ACCOUNT_ID_EXTRA = "account_id";
  public static final String FROM_WELCOME = "from_welcome";
  public static final String FROM_WELCOME_RAW_QR = "from_welcome_raw_qr";

  private ConversationListFragment conversationListFragment;
  private ContactsFragment contactsFragment;
  private SettingsFragment settingsFragment;
  private ProfileFragment profileFragment;
  public TextView title;
  private AvatarView selfAvatar;
  private ImageView unreadIndicator;
  private SearchFragment searchFragment;
  private SearchToolbar searchToolbar;
  private ImageView searchAction;
  private ViewGroup fragmentContainer;
  private ViewGroup selfAvatarContainer;
  private BottomNavigationView bottomNavigation;

  /**
   * used to store temporarily scanned QR to pass it back to QrCodeHandler when ScreenLockUtil is
   * used
   */
  private String qrData = null;

  private ActivityResultLauncher<Intent> relayLockLauncher;
  private ActivityResultLauncher<Intent> qrScannerLauncher;

  /**
   * used to store temporarily profile ID to delete after authorization is granted via
   * ScreenLockUtil
   */
  private int deleteProfileId = 0;

  private ActivityResultLauncher<Intent> deleteProfileLockLauncher;

  @Override
  protected void onPreCreate() {
    dynamicTheme = new DynamicNoActionBarTheme();
    super.onPreCreate();
  }

  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    relayLockLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == RESULT_OK) {
                // QrCodeHandler requested user authorization before adding a relay
                // and it was granted, so proceed to add the relay
                if (qrData != null) {
                  new QrCodeHandler(this).addRelay(qrData);
                  qrData = null;
                }
              }
            });
    deleteProfileLockLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == RESULT_OK) {
                if (deleteProfileId != 0) {
                  deleteProfile(deleteProfileId);
                  deleteProfileId = 0;
                }
              }
            });
    qrScannerLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == RESULT_OK) {
                IntentResult scanResult =
                    IntentIntegrator.parseActivityResult(result.getResultCode(), result.getData());
                qrData = scanResult.getContents();
                new QrCodeHandler(this)
                    .handleQrData(
                        qrData, SecurejoinSource.Scan, SecurejoinUiPath.QrIcon, relayLockLauncher);
              }
            });

    addDeviceMessages(getIntent().getBooleanExtra(FROM_WELCOME, false));
    if (getIntent().getIntExtra(ACCOUNT_ID_EXTRA, -1) <= 0) {
      getIntent().putExtra(ACCOUNT_ID_EXTRA, NcHelper.getContext(this).getAccountId());
    }

    // create view
    setContentView(R.layout.conversation_list_activity);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    selfAvatar = findViewById(R.id.self_avatar);
    selfAvatarContainer = findViewById(R.id.self_avatar_container);
    unreadIndicator = findViewById(R.id.unread_indicator);
    title = findViewById(R.id.toolbar_title);
    searchToolbar = findViewById(R.id.search_toolbar);
    searchAction = findViewById(R.id.search_action);
    fragmentContainer = findViewById(R.id.fragment_container);
    bottomNavigation = findViewById(R.id.bottom_navigation);

    // CAMBIO #6: Título fijo "NexusChat" (sin estado de conexión)
    title.setText("NexusChat");

    // add margin to avoid content hidden behind system bars
    ViewUtil.applyWindowInsetsAsMargin(searchToolbar, true, true, true, false);

    // Inicializar fragments
    Bundle bundle = new Bundle();
    conversationListFragment =
        initFragment(R.id.fragment_container, new ConversationListFragment(), bundle);
    
    contactsFragment = new ContactsFragment();
    settingsFragment = new SettingsFragment();
    profileFragment = new ProfileFragment();

    // CAMBIO #4: Configurar navegación inferior
    setupBottomNavigation();

    initializeSearchListener();

    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (searchToolbar.isVisible()) {
                  searchToolbar.collapse();
                } else {
                  Activity activity = ConversationListActivity.this;
                  if (isRelayingMessageContent(activity)) {
                    int selectedAccId = NcHelper.getContext(activity).getAccountId();
                    int initialAccId = getIntent().getIntExtra(ACCOUNT_ID_EXTRA, selectedAccId);
                    if (initialAccId != selectedAccId) {
                      // allowing to go back is dangerous, it could be activity on previously
                      // selected account,
                      // instead of figuring out account rollback in onResume in each activity
                      // (conversation, gallery, media preview, webxnc, etc.)
                      // just clear the back stack and stay in newly selected account
                      finishAffinity();
                      startActivity(new Intent(activity, ConversationListActivity.class));
                      return;
                    } else {
                      handleResetRelaying();
                    }
                  }

                  setEnabled(false);
                  getOnBackPressedDispatcher().onBackPressed();
                }
              }
            });

    TooltipCompat.setTooltipText(searchAction, getText(R.string.search_explain));

    TooltipCompat.setTooltipText(selfAvatar, getText(R.string.switch_account));
    selfAvatar.setOnClickListener(
        v -> AccountManager.getInstance().showSwitchAccountMenu(this, false));
    findViewById(R.id.avatar_and_title)
        .setOnClickListener(
            v -> {
              if (!isRelayingMessageContent(this)) {
                AccountManager.getInstance().showSwitchAccountMenu(this, false);
              }
            });

    refresh();

    if (BuildConfig.DEBUG) checkNdkArchitecture();

    NcHelper.maybeShowMigrationError(this);

    String rawQrString = getIntent().getStringExtra(FROM_WELCOME_RAW_QR);
    // Launch chat directly, if coming from onboarding with a join chat/group QR
    if (rawQrString != null) {
      QrCodeHandler qrCodeHandler = new QrCodeHandler(this);
      qrCodeHandler.secureJoinByQr(rawQrString, SecurejoinSource.Scan, SecurejoinUiPath.Unknown);
    }
  }

  /**
   * CAMBIO #4: Configurar la navegación inferior
   */
  private void setupBottomNavigation() {
    bottomNavigation.setOnNavigationItemSelectedListener(item -> {
      int itemId = item.getItemId();
      
      if (itemId == R.id.navigation_chats) {
        showChatsFragment();
        return true;
      } else if (itemId == R.id.navigation_contacts) {
        showContactsFragment();
        return true;
      } else if (itemId == R.id.navigation_settings) {
        showSettingsFragment();
        return true;
      } else if (itemId == R.id.navigation_profile) {
        showProfileFragment();
        return true;
      }
      
      return false;
    });
    
    // Seleccionar Chats por defecto
    bottomNavigation.setSelectedItemId(R.id.navigation_chats);
  }

  /**
   * CAMBIO #4: Mostrar fragment de Chats
   */
  private void showChatsFragment() {
    getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.fragment_container, conversationListFragment)
        .commit();
    title.setText("NexusChat");
    selfAvatarContainer.setVisibility(View.VISIBLE);
  }

  /**
   * CAMBIO #4: Mostrar fragment de Contactos
   */
  private void showContactsFragment() {
    getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.fragment_container, contactsFragment)
        .commit();
    title.setText("Contactos");
    selfAvatarContainer.setVisibility(View.GONE);
  }

  /**
   * CAMBIO #4: Mostrar fragment de Ajustes
   */
  private void showSettingsFragment() {
    getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.fragment_container, settingsFragment)
        .commit();
    title.setText("Ajustes");
    selfAvatarContainer.setVisibility(View.GONE);
  }

  /**
   * CAMBIO #4: Mostrar fragment de Perfil
   */
  private void showProfileFragment() {
    getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.fragment_container, profileFragment)
        .commit();
    title.setText("Perfil");
    selfAvatarContainer.setVisibility(View.GONE);
  }

  /**
   * If the build script is invoked with a specific architecture (e.g.`ndk-make.sh arm64-v8a`), it
   * will compile the core only for this arch. This method checks if the arch was correct.
   *
   * <p>In order to do this, `ndk-make.sh` writes its argument into the file `ndkArch`.
   * `getNdkArch()` in `build.gradle` then reads this file and its content is assigned to
   * `BuildConfig.NDK_ARCH`.
   */
  @SuppressWarnings("ConstantConditions")
  private void checkNdkArchitecture() {
    boolean wrongArch = false;

    if (!TextUtils.isEmpty(BuildConfig.NDK_ARCH)) {
      String archProperty = System.getProperty("os.arch");
      String arch;

      // armv8l is 32 bit mode in 64 bit CPU:
      if (archProperty.startsWith("armv7") || archProperty.startsWith("armv8l"))
        arch = "armeabi-v7a";
      else if (archProperty.equals("aarch64")) arch = "arm64-v8a";
      else if (archProperty.equals("i686")) arch = "x86";
      else if (archProperty.equals("x86_64")) arch = "x86_64";
      else {
        Log.e(TAG, "Unknown os.arch: " + archProperty);
        arch = "";
      }

      if (!arch.equals(BuildConfig.NDK_ARCH)) {
        wrongArch = true;

        String message;
        if (arch.equals("")) {
          message =
              "This phone has the unknown architecture "
                  + archProperty";
        } else {
          message =
              "Apparently you used `ndk-make.sh "
                  + BuildConfig.NDK_ARCH
                  + "`, but this device is "
                  + arch
                  + ".\n\n"
                  + "You can use the app, but changes you made to the Rust code were not applied.\n\n"
                  + "To compile in your changes, you can:\n"
                  + "- Either run `ndk-make.sh "
                  + arch
                  + "` to build only for "
                  + arch
                  + " in debug mode\n"
                  + "- Or run `ndk-make.sh` without argument to build for all architectures in release mode\n\n"
                  + "If something doesn't work, please open an issue!!";
        }
        Log.e(TAG, message);

        if (!Prefs.getBooleanPreference(this, NDK_ARCH_WARNED, false)) {
          new AlertDialog.Builder(this)
              .setMessage(message)
              .setPositiveButton(android.R.string.ok, null)
              .show();
          Prefs.setBooleanPreference(this, NDK_ARCH_WARNED, true);
        }
      }
    }

    if (!wrongArch) Prefs.setBooleanPreference(this, NDK_ARCH_WARNED, false);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    if (isFinishing()) {
      Log.w(TAG, "Activity is finishing, aborting onNewIntent()");
      return;
    }
    super.onNewIntent(intent);
    setIntent(intent);
    if (getIntent().getIntExtra(ACCOUNT_ID_EXTRA, -1) <= 0) {
      getIntent().putExtra(ACCOUNT_ID_EXTRA, NcHelper.getContext(this).getAccountId());
    }
    refresh();
    conversationListFragment.onNewIntent();
    invalidateOptionsMenu();
  }

  private void refresh() {
    int selectedAccId = NcHelper.getContext(this).getAccountId();
    int accountId = getIntent().getIntExtra(ACCOUNT_ID_EXTRA, selectedAccId);
    if (getIntent().getBooleanExtra(CLEAR_NOTIFICATIONS, false)) {
      NcHelper.getNotificationCenter(this).removeAllNotifications(accountId);
    }
    if (accountId != selectedAccId) {
      AccountManager.getInstance().switchAccount(this, accountId);
      onProfileSwitched(accountId);
    } else {
      refreshAvatar();
      refreshUnreadIndicator();
      refreshTitle();
    }

    handleOpenpgp4fpr();
    if (isDirectSharing(this)) {
      openConversation(getDirectSharingChatId(this), -1);
    }
  }

  public void refreshTitle() {
    // CAMBIO #6: No mostrar estado de conexión, siempre "NexusChat" o el título según el fragmento
    if (isRelayingMessageContent(this)) {
      if (isForwarding(this)) {
        title.setText(R.string.forward_to);
      } else {
        String titleStr = getSharedTitle(this);
        if (titleStr != null) { // sharing from sendToChat
          title.setText(titleStr);
        } else { // normal sharing
          title.setText(R.string.chat_share_with_title);
        }
      }
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    } else {
      // CAMBIO #6: No mostrar estado de conexión, solo el nombre fijo o según fragmento actual
      if (bottomNavigation != null) {
        int selectedItemId = bottomNavigation.getSelectedItemId();
        if (selectedItemId == R.id.navigation_chats) {
          title.setText("NexusChat");
        } else if (selectedItemId == R.id.navigation_contacts) {
          title.setText("Contactos");
        } else if (selectedItemId == R.id.navigation_settings) {
          title.setText("Ajustes");
        } else if (selectedItemId == R.id.navigation_profile) {
          title.setText("Perfil");
        } else {
          title.setText("NexusChat");
        }
      } else {
        title.setText("NexusChat");
      }
      // Actualizar el dot de conectividad en el avatar
      selfAvatar.setConnectivity(NcHelper.getContext(this).getConnectivity());
      getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    }
  }

  public void refreshAvatar() {
    if (selfAvatarContainer == null) return;

    if (isRelayingMessageContent(this)) {
      selfAvatarContainer.setVisibility(View.GONE);
    } else {
      // CAMBIO #4: Mostrar avatar solo en fragment de Chats
      if (bottomNavigation != null && bottomNavigation.getSelectedItemId() == R.id.navigation_chats) {
        selfAvatarContainer.setVisibility(View.VISIBLE);
        NcContext ncContext = NcHelper.getContext(this);
        NcContact self = ncContext.getContact(NcContact.NC_CONTACT_ID_SELF);
        String name = ncContext.getConfig("displayname");
        if (TextUtils.isEmpty(name)) {
          name = self.getAddr();
        }
        selfAvatar.setAvatar(GlideApp.with(this), new Recipient(this, self, name), false);
      } else {
        selfAvatarContainer.setVisibility(View.GONE);
      }
    }
  }

  public void refreshUnreadIndicator() {
    int unreadCount = 0;
    NcAccounts ncAccounts = NcHelper.getAccounts(this);
    int skipId = ncAccounts.getSelectedAccount().getAccountId();
    for (int accountId : ncAccounts.getAll()) {
      if (accountId != skipId) {
        NcContext ncContext = ncAccounts.getAccount(accountId);
        if (!ncContext.isMuted()) {
          unreadCount += ncContext.getFreshMsgs().length;
        }
      }
    }

    if (unreadCount == 0) {
      unreadIndicator.setVisibility(View.GONE);
    } else {
      unreadIndicator.setImageDrawable(
          TextDrawable.builder()
              .beginConfig()
              .width(ViewUtil.dpToPx(this, 24))
              .height(ViewUtil.dpToPx(this, 24))
              .textColor(Color.WHITE)
              .bold()
              .endConfig()
              .buildRound(
                  String.valueOf(unreadCount), getResources().getColor(R.color.unread_count)));
      unreadIndicator.setVisibility(View.VISIBLE);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshTitle();
    invalidateOptionsMenu();
    DirectShareUtil.triggerRefreshDirectShare(this);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    if (isRelayingMessageContent(this)) {
      inflater.inflate(R.menu.forwarding_menu, menu);
      menu.findItem(R.id.menu_export_attachment)
          .setVisible(ShareUtil.isFromWebxnc(this) && ShareUtil.getSharedUris(this).size() == 1);
    } else {
      inflater.inflate(R.menu.text_secure_normal, menu);
      menu.findItem(R.id.menu_global_map).setVisible(Prefs.isLocationStreamingEnabled(this));
      MenuItem proxyItem = menu.findItem(R.id.menu_proxy_settings);
      if (TextUtils.isEmpty(NcHelper.get(this, CONFIG_PROXY_URL))) {
        proxyItem.setVisible(false);
      } else {
        boolean proxyEnabled = NcHelper.getInt(this, CONFIG_PROXY_ENABLED) == 1;
        proxyItem.setIcon(
            proxyEnabled ? R.drawable.ic_proxy_enabled_24 : R.drawable.ic_proxy_disabled_24);
        proxyItem.setVisible(true);
      }
    }

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  private void initializeSearchListener() {
    searchAction.setOnClickListener(
        v -> {
          searchToolbar.display(
              searchAction.getX() + (searchAction.getWidth() / 2),
              searchAction.getY() + (searchAction.getHeight() / 2));
        });

    searchToolbar.setListener(
        new SearchToolbar.SearchListener() {
          @Override
          public void onSearchTextChange(String text) {
            String trimmed = text.trim();

            if (trimmed.length() > 0) {
              if (searchFragment == null) {
                searchFragment = SearchFragment.newInstance();
                getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragment_container, searchFragment, null)
                    .commit();
              }
              searchFragment.updateSearchQuery(trimmed);
            } else if (searchFragment != null) {
              getSupportFragmentManager().beginTransaction().remove(searchFragment).commit();
              searchFragment = null;
            }
          }

          @Override
          public void onSearchClosed() {
            if (searchFragment != null) {
              getSupportFragmentManager().beginTransaction().remove(searchFragment).commit();
              searchFragment = null;
            }
          }
        });
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    int itemId = item.getItemId();
    if (itemId == R.id.menu_new_chat) {
      createChat();
      return true;
    } else if (itemId == R.id.menu_invite_friends) {
      shareInvite();
      return true;
    } else if (itemId == R.id.menu_settings) {
      // CAMBIO #4: En lugar de abrir nueva actividad, navegar al fragment de Ajustes
      if (bottomNavigation != null) {
        bottomNavigation.setSelectedItemId(R.id.navigation_settings);
      }
      return true;
    } else if (itemId == R.id.menu_qr) {
      Intent intent =
          new IntentIntegrator(this).setCaptureActivity(QrActivity.class).createScanIntent();
      qrScannerLauncher.launch(intent);
      return true;
    } else if (itemId == R.id.menu_global_map) {
      WebxncActivity.openMaps(this, 0);
      return true;
    } else if (itemId == R.id.menu_proxy_settings) {
      startActivity(new Intent(this, ProxySettingsActivity.class));
      return true;
    } else if (itemId == android.R.id.home) {
      getOnBackPressedDispatcher().onBackPressed();
      return true;
    } else if (itemId == R.id.menu_all_media) {
      startActivity(new Intent(this, AllMediaActivity.class));
      return true;
    } else if (itemId == R.id.menu_export_attachment) {
      handleSaveAttachment();
      return true;
    } else if (itemId == R.id.menu_switch_account) {
      AccountManager.getInstance().showSwitchAccountMenu(this, true);
      return true;
    }

    return false;
  }

  private void handleSaveAttachment() {
    SaveAttachmentTask.showWarningDialog(
        this,
        (dialogInterface, i) -> {
          if (StorageUtil.canWriteToMediaStore(this)) {
            performSave();
            return;
          }

          Permissions.with(this)
              .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
              .alwaysGrantOnSdk30()
              .ifNecessary()
              .withPermanentDenialDialog(getString(R.string.perm_explain_access_to_storage_denied))
              .onAllGranted(this::performSave)
              .execute();
        });
  }

  private void performSave() {
    ArrayList<Uri> uriList = ShareUtil.getSharedUris(this);
    Uri uri = uriList.get(0);
    String mimeType = PersistentBlobProvider.getMimeType(this, uri);
    String fileName = PersistentBlobProvider.getFileName(this, uri);
    SaveAttachmentTask.Attachment[] attachments =
        new SaveAttachmentTask.Attachment[] {
          new SaveAttachmentTask.Attachment(uri, mimeType, new Date().getTime(), fileName)
        };
    SaveAttachmentTask saveTask = new SaveAttachmentTask(this);
    saveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, attachments);
    getOnBackPressedDispatcher().onBackPressed();
  }

  private void handleOpenpgp4fpr() {
    if (getIntent() != null && Intent.ACTION_VIEW.equals(getIntent().getAction())) {
      Uri uri = getIntent().getData();
      if (uri == null) {
        return;
      }

      if (uri.getScheme().equalsIgnoreCase(OPENPGP4FPR) || Util.isInviteURL(uri)) {
        QrCodeHandler qrCodeHandler = new QrCodeHandler(this);
        qrCodeHandler.handleOnlySecureJoinQr(uri.toString(), SecurejoinSource.ExternalLink, null);
      }
    }
  }

  private void handleResetRelaying() {
    resetRelayingMessageContent(this);
    refreshTitle();
    selfAvatarContainer.setVisibility(View.VISIBLE);
    conversationListFragment.onNewIntent();
    invalidateOptionsMenu();
  }

  @Override
  public void onCreateConversation(int chatId) {
    openConversation(chatId, -1);
  }

  public void openConversation(int chatId, int startingPosition) {
    searchToolbar.clearFocus();

    final NcContext ncContext = NcHelper.getContext(this);
    int fwdAccId = getForwardedMessageAccountId(this);
    if (fwdAccId == ncContext.getAccountId() && ncContext.getChat(chatId).isSelfTalk()) {
      SendRelayedMessageUtil.immediatelyRelay(this, chatId);
      Toast.makeText(
              this,
              DynamicTheme.getCheckmarkEmoji(this) + " " + getString(R.string.saved),
              Toast.LENGTH_SHORT)
          .show();
      handleResetRelaying();
      finish();
    } else {
      Intent intent = new Intent(this, ConversationActivity.class);
      intent.putExtra(ConversationActivity.ACCOUNT_ID_EXTRA, ncContext.getAccountId());
      intent.putExtra(ConversationActivity.CHAT_ID_EXTRA, chatId);
      intent.putExtra(ConversationActivity.STARTING_POSITION_EXTRA, startingPosition);
      if (isRelayingMessageContent(this)) {
        acquireRelayMessageContent(this, intent);
      }
      startActivity(intent);

      overridePendingTransition(R.anim.slide_from_right, R.anim.fade_scale_out);
    }
  }

  @Override
  public void onSwitchToArchive() {
    Intent intent = new Intent(this, ConversationListArchiveActivity.class);
    if (isRelayingMessageContent(this)) {
      acquireRelayMessageContent(this, intent);
    }
    startActivity(intent);
    overridePendingTransition(R.anim.slide_from_right, R.anim.fade_scale_out);
  }

  private void createChat() {
    Intent intent = new Intent(this, NewConversationActivity.class);
    if (isRelayingMessageContent(this)) {
      acquireRelayMessageContent(this, intent);
    }
    startActivity(intent);
  }

  private void shareInvite() {
    Intent intent = new Intent(Intent.ACTION_SEND);
    intent.setType("text/plain");
    String inviteURL = NcHelper.getContext(this).getSecurejoinQr(0);
    intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.invite_friends_text, inviteURL));
    startActivity(Intent.createChooser(intent, getString(R.string.chat_share_with_title)));
  }

  private void addDeviceMessages(boolean fromWelcome) {
    // update messages - for new messages, do not reuse or modify strings but create new ones.
    // it is not needed to keep all past update messages, however, when deleted, also the strings
    // should be deleted.
    try {
      NcContext ncContext = NcHelper.getContext(this);
      final String deviceMsgLabel = "update_2_0_0_android-h";
      if (!ncContext.wasDeviceMsgEverAdded(deviceMsgLabel)) {
        NcMsg msg = null;
        if (!fromWelcome) {
          msg = new NcMsg(ncContext, NcMsg.NC_MSG_TEXT);

          // InputStream inputStream =
          // getResources().getAssets().open("device-messages/green-checkmark.jpg");
          // String outputFile = NcHelper.getBlobdirFile(ncContext, "green-checkmark", ".jpg");
          // Util.copy(inputStream, new FileOutputStream(outputFile));
          // msg.setFile(outputFile, "image/jpeg");

          msg.setText(getString(R.string.update_2_0, "https://nexus.chat/donate"));
        }
        ncContext.addDeviceMsg(deviceMsgLabel, msg);

        if (Prefs.getStringPreference(this, Prefs.LAST_DEVICE_MSG_LABEL, "")
            .equals(deviceMsgLabel)) {
          int deviceChatId = ncContext.getChatIdByContactId(NcContact.NC_CONTACT_ID_DEVICE);
          if (deviceChatId != 0) {
            ncContext.marknoticedChat(deviceChatId);
          }
        }
        Prefs.setStringPreference(this, Prefs.LAST_DEVICE_MSG_LABEL, deviceMsgLabel);
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void onProfileSwitched(int profileId) {
    addDeviceMessages(false);
    refreshAvatar();
    refreshUnreadIndicator();
    refreshTitle();
    conversationListFragment.loadChatlistAsync();
  }

  public void onDeleteProfile(int profileId) {
    deleteProfileId = profileId;
    boolean result =
        ScreenLockUtil.applyScreenLock(
            this,
            getString(R.string.delete_account),
            getString(R.string.enter_system_secret_to_continue),
            deleteProfileLockLauncher);
    if (!result) {
      deleteProfile(profileId);
    }
  }

  private void deleteProfile(int profileId) {
    NcAccounts accounts = NcHelper.getAccounts(this);
    boolean selected = profileId == accounts.getSelectedAccount().getAccountId();
    NcHelper.getNotificationCenter(this).removeAllNotifications(profileId);
    accounts.removeAccount(profileId);
    if (selected) {
      NcContext selAcc = accounts.getSelectedAccount();
      if (selAcc.isOk()) {
        AccountManager.getInstance().switchAccount(this, selAcc.getAccountId());
        onProfileSwitched(selAcc.getAccountId());
      } else {
        AccountManager.getInstance().switchAccountAndStartActivity(this, 0);
      }
    } else {
      AccountManager.getInstance().showSwitchAccountMenu(this, false);
    }

    // title update needed to show "Nexus Chat" in case there is only one profile left
    refreshTitle();
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }
}
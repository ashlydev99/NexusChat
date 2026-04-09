package com.nexuschat.messenger.connect;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;
import chat.delta.rpc.Rpc;
import chat.delta.rpc.RpcException;
import com.b44t.messenger.NcAccounts;
import com.b44t.messenger.NcContext;
import java.io.File;
import com.nexuschat.messenger.ApplicationContext;
import com.nexuschat.messenger.ConversationListActivity;
import com.nexuschat.messenger.WelcomeActivity;
import com.nexuschat.messenger.accounts.AccountSelectionListFragment;

public class AccountManager {

  private static final String TAG = AccountManager.class.getSimpleName();
  private static final String LAST_ACCOUNT_ID = "last_account_id";
  private static AccountManager self;

  private void resetNcContext(Context context) {
    ApplicationContext appContext = (ApplicationContext) context.getApplicationContext();
    appContext.setNcContext(ApplicationContext.getNcAccounts().getSelectedAccount());
    NcHelper.setStockTranslations(context);
    DirectShareUtil.resetAllShortcuts(appContext);
  }

  // public api

  public static AccountManager getInstance() {
    if (self == null) {
      self = new AccountManager();
    }
    return self;
  }

  public void migrateToDcAccounts(ApplicationContext context) {
    try {
      int selectAccountId = 0;

      File[] files = context.getFilesDir().listFiles();
      if (files != null) {
        for (File file : files) {
          // old accounts have the pattern "messenger*.db"
          if (!file.isDirectory()
              && file.getName().startsWith("messenger")
              && file.getName().endsWith(".db")) {
            int accountId =
                ApplicationContext.getDcAccounts().migrateAccount(file.getAbsolutePath());
            if (accountId != 0) {
              String selName =
                  PreferenceManager.getDefaultSharedPreferences(context)
                      .getString("curr_account_db_name", "messenger.db");
              if (file.getName().equals(selName)) {
                // postpone selection as it will otherwise be overwritten by the next
                // migrateAccount() call
                // (if more than one account needs to be migrated)
                selectAccountId = accountId;
              }
            }
          }
        }
      }

      if (selectAccountId != 0) {
        ApplicationContext.getDcAccounts().selectAccount(selectAccountId);
      }
    } catch (Exception e) {
      Log.e(TAG, "Error in migrateToDcAccounts()", e);
    }
  }

  public void switchAccount(Context context, int accountId) {
    NcHelper.getAccounts(context).selectAccount(accountId);
    resetNcContext(context);
  }

  // add accounts

  public int beginAccountCreation(Context context) {
    Rpc rpc = NcHelper.getRpc(context);
    NcAccounts accounts = NcHelper.getAccounts(context);
    NcContext selectedAccount = accounts.getSelectedAccount();
    if (selectedAccount.isOk()) {
      PreferenceManager.getDefaultSharedPreferences(context)
          .edit()
          .putInt(LAST_ACCOUNT_ID, selectedAccount.getAccountId())
          .apply();
    }

    int id = 0;
    try {
      id = rpc.addAccount();
    } catch (RpcException e) {
      Log.e(TAG, "Error calling rpc.addAccount()", e);
    }
    resetNcContext(context);
    return id;
  }

  public boolean canRollbackAccountCreation(Context context) {
    return NcHelper.getAccounts(context).getAll().length > 1;
  }

  public void rollbackAccountCreation(Activity activity) {
    NcAccounts accounts = NcHelper.getAccounts(activity);

    NcContext selectedAccount = accounts.getSelectedAccount();
    if (selectedAccount.isConfigured() == 0) {
      accounts.removeAccount(selectedAccount.getAccountId());
    }

    int lastAccountId =
        PreferenceManager.getDefaultSharedPreferences(activity).getInt(LAST_ACCOUNT_ID, 0);
    if (lastAccountId == 0 || !accounts.getAccount(lastAccountId).isOk()) {
      lastAccountId = accounts.getSelectedAccount().getAccountId();
    }
    switchAccountAndStartActivity(activity, lastAccountId);
  }

  public void switchAccountAndStartActivity(Activity activity, int destAccountId) {
    if (destAccountId == 0) {
      beginAccountCreation(activity);
    } else {
      switchAccount(activity, destAccountId);
    }

    activity.finishAffinity();
    if (destAccountId == 0) {
      activity.startActivity(new Intent(activity, WelcomeActivity.class));
    } else {
      activity.startActivity(
          new Intent(activity.getApplicationContext(), ConversationListActivity.class));
    }
  }

  // ui

  public void showSwitchAccountMenu(ConversationListActivity activity, boolean selectOnly) {
    AccountSelectionListFragment dialog = AccountSelectionListFragment.newInstance(selectOnly);
    dialog.show(((FragmentActivity) activity).getSupportFragmentManager(), null);
  }

  public void addAccountFromSecondDevice(Activity activity, String backupQr) {
    NcAccounts accounts = NcHelper.getAccounts(activity);
    if (accounts.getSelectedAccount().isConfigured() == 1) {
      // the selected account is already configured, create a new one
      beginAccountCreation(activity);
    }

    activity.finishAffinity();
    Intent intent = new Intent(activity, WelcomeActivity.class);
    intent.putExtra(WelcomeActivity.BACKUP_QR_EXTRA, backupQr);
    activity.startActivity(intent);
  }
}

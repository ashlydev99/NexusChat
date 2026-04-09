package com.nexuschat.messenger.accounts;

import static com.b44t.messenger.NcContact.NC_CONTACT_ID_ADD_ACCOUNT;
import static com.nexuschat.messenger.connect.NcHelper.CONFIG_PRIVATE_TAG;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import chat.delta.rpc.Rpc;
import chat.delta.rpc.RpcException;
import com.b44t.messenger.NcAccounts;
import com.b44t.messenger.NcContact;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcEvent;
import java.util.Arrays;
import com.nexuschat.messenger.ConnectivityActivity;
import com.nexuschat.messenger.ConversationListActivity;
import com.nexuschat.messenger.R;
import com.nexuschat.messenger.components.AvatarView;
import com.nexuschat.messenger.connect.AccountManager;
import com.nexuschat.messenger.connect.NcEventCenter;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.mms.GlideApp;
import com.nexuschat.messenger.recipients.Recipient;
import com.nexuschat.messenger.util.Util;
import com.nexuschat.messenger.util.ViewUtil;

public class AccountSelectionListFragment extends DialogFragment
    implements NcEventCenter.NcEventDelegate {
  private static final String TAG = AccountSelectionListFragment.class.getSimpleName();
  private static final String ARG_SELECT_ONLY = "select_only";
  private RecyclerView recyclerView;
  private AccountSelectionListAdapter adapter;
  private boolean selectOnly;

  public static AccountSelectionListFragment newInstance(boolean selectOnly) {
    AccountSelectionListFragment fragment = new AccountSelectionListFragment();
    Bundle args = new Bundle();
    args.putBoolean(ARG_SELECT_ONLY, selectOnly);
    fragment.setArguments(args);
    return fragment;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    selectOnly = getArguments() != null && getArguments().getBoolean(ARG_SELECT_ONLY, false);
    AlertDialog.Builder builder =
        new AlertDialog.Builder(requireActivity())
            .setTitle(R.string.switch_account)
            .setNegativeButton(R.string.cancel, null);
    if (!selectOnly) {
      builder.setNeutralButton(
          R.string.connectivity,
          ((dialog, which) -> {
            startActivity(new Intent(getActivity(), ConnectivityActivity.class));
          }));
    }

    LayoutInflater inflater = requireActivity().getLayoutInflater();
    View view = inflater.inflate(R.layout.account_selection_list_fragment, null);
    recyclerView = ViewUtil.findById(view, R.id.recycler_view);
    recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

    adapter =
        new AccountSelectionListAdapter(
            this, GlideApp.with(getActivity()), new ListClickListener());
    recyclerView.setAdapter(adapter);
    refreshData();
    NcEventCenter eventCenter = NcHelper.getEventCenter(requireActivity());
    eventCenter.addMultiAccountObserver(NcContext.NC_EVENT_CONNECTIVITY_CHANGED, this);
    eventCenter.addMultiAccountObserver(NcContext.NC_EVENT_INCOMING_MSG, this);
    eventCenter.addMultiAccountObserver(NcContext.NC_EVENT_MSGS_NOTICED, this);

    return builder.setView(view).create();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    NcHelper.getEventCenter(requireActivity()).removeObservers(this);
  }

  @Override
  public void handleEvent(@NonNull NcEvent event) {
    refreshData();
  }

  private void refreshData() {
    if (adapter == null) return;

    NcAccounts accounts = NcHelper.getAccounts(getActivity());
    int[] accountIds = accounts.getAll();

    int[] ids = new int[(selectOnly ? 0 : 1) + accountIds.length];
    int j = 0;
    for (int accountId : accountIds) {
      ids[j++] = accountId;
    }
    if (!selectOnly) ids[j] = NC_CONTACT_ID_ADD_ACCOUNT;
    adapter.changeData(ids, accounts.getSelectedAccount().getAccountId());
  }

  @Override
  public void onCreateContextMenu(
      @NonNull ContextMenu menu, @NonNull View v, ContextMenu.ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    if (selectOnly) return;

    requireActivity().getMenuInflater().inflate(R.menu.account_item_context, menu);

    AccountSelectionListItem listItem = (AccountSelectionListItem) v;
    int accountId = listItem.getAccountId();
    NcAccounts ncAccounts = NcHelper.getAccounts(requireActivity());

    Util.redMenuItem(menu, R.id.delete);

    if (ncAccounts.getAccount(accountId).isMuted()) {
      menu.findItem(R.id.menu_mute_notifications).setTitle(R.string.menu_unmute);
    }

    // hack to make onContextItemSelected() work with DialogFragment,
    // see
    // https://stackoverflow.com/questions/15929026/oncontextitemselected-does-not-get-called-in-a-dialogfragment
    MenuItem.OnMenuItemClickListener listener =
        item -> {
          onContextItemSelected(item, accountId);
          return true;
        };
    for (int i = 0, n = menu.size(); i < n; i++) {
      menu.getItem(i).setOnMenuItemClickListener(listener);
    }
    // /hack
  }

  private void onContextItemSelected(MenuItem item, int accountId) {
    int itemId = item.getItemId();
    if (itemId == R.id.delete) {
      onDeleteProfile(accountId);
    } else if (itemId == R.id.menu_mute_notifications) {
      onToggleMute(accountId);
    } else if (itemId == R.id.menu_set_tag) {
      onSetTag(accountId);
    } else if (itemId == R.id.menu_move_to_top) {
      onMoveToTop(accountId);
    }
  }

  private void onMoveToTop(int accountId) {
    Activity activity = getActivity();
    if (activity == null) return;

    int[] accountIds = NcHelper.getAccounts(activity).getAll();
    Integer[] ids = new Integer[accountIds.length];
    ids[0] = accountId;
    int j = 1;
    for (int accId : accountIds) {
      if (accId != accountId) {
        ids[j++] = accId;
      }
    }

    Rpc rpc = NcHelper.getRpc(activity);
    try {
      rpc.setAccountsOrder(Arrays.asList(ids));
    } catch (RpcException e) {
      Log.e(TAG, "Error calling rpc.setAccountsOrder()", e);
    }

    refreshData();
  }

  private void onSetTag(int accountId) {
    ConversationListActivity activity = (ConversationListActivity) requireActivity();
    AccountSelectionListFragment.this.dismiss();

    NcContext ncContext = NcHelper.getAccounts(activity).getAccount(accountId);
    View view = View.inflate(activity, R.layout.single_line_input, null);
    EditText inputField = view.findViewById(R.id.input_field);
    inputField.setHint(R.string.profile_tag_hint);
    inputField.setText(ncContext.getConfig(CONFIG_PRIVATE_TAG));

    new AlertDialog.Builder(activity)
        .setTitle(R.string.profile_tag)
        .setMessage(R.string.profile_tag_explain)
        .setView(view)
        .setPositiveButton(
            android.R.string.ok,
            (d, b) -> {
              String newTag = inputField.getText().toString().trim();
              ncContext.setConfig(CONFIG_PRIVATE_TAG, newTag);
              AccountManager.getInstance().showSwitchAccountMenu(activity, selectOnly);
            })
        .setNegativeButton(
            R.string.cancel,
            (d, b) -> AccountManager.getInstance().showSwitchAccountMenu(activity, selectOnly))
        .show();
  }

  private void onDeleteProfile(int accountId) {
    AccountSelectionListFragment.this.dismiss();
    ConversationListActivity activity = (ConversationListActivity) requireActivity();
    NcAccounts accounts = NcHelper.getAccounts(activity);
    Rpc rpc = NcHelper.getRpc(activity);

    View dialogView = View.inflate(activity, R.layout.dialog_delete_profile, null);
    AvatarView avatar = dialogView.findViewById(R.id.avatar);
    TextView nameView = dialogView.findViewById(R.id.name);
    TextView addrView = dialogView.findViewById(R.id.address);
    TextView sizeView = dialogView.findViewById(R.id.size_label);
    TextView description = dialogView.findViewById(R.id.description);
    NcContext ncContext = accounts.getAccount(accountId);
    String name = ncContext.getConfig("displayname");
    NcContact contact = ncContext.getContact(NcContact.NC_CONTACT_ID_SELF);
    if (TextUtils.isEmpty(name)) {
      name = contact.getAddr();
    }
    Recipient recipient = new Recipient(requireContext(), contact, name);
    avatar.setAvatar(GlideApp.with(activity), recipient, false);
    nameView.setText(name);
    addrView.setText(contact.getAddr());
    Util.runOnAnyBackgroundThread(
        () -> {
          try {
            final int sizeBytes = rpc.getAccountFileSize(accountId);
            Util.runOnMain(
                () -> {
                  sizeView.setText(Util.getPrettyFileSize(sizeBytes));
                });
          } catch (RpcException e) {
            Log.e(TAG, "Error calling rpc.getAccountFileSize()", e);
          }
        });
    description.setText(activity.getString(R.string.delete_account_explain_with_name, name));

    AlertDialog dialog =
        new AlertDialog.Builder(activity)
            .setTitle(R.string.delete_account)
            .setView(dialogView)
            .setNegativeButton(
                R.string.cancel,
                (d, which) ->
                    AccountManager.getInstance().showSwitchAccountMenu(activity, selectOnly))
            .setPositiveButton(R.string.delete, (d2, w2) -> activity.onDeleteProfile(accountId))
            .show();
    Util.redPositiveButton(dialog);
  }

  private void onToggleMute(int accountId) {
    NcAccounts ncAccounts = NcHelper.getAccounts(requireActivity());
    NcContext ncContext = ncAccounts.getAccount(accountId);
    ncContext.setMuted(!ncContext.isMuted());
    recyclerView.getAdapter().notifyDataSetChanged();
  }

  private class ListClickListener implements AccountSelectionListAdapter.ItemClickListener {

    @Override
    public void onItemClick(AccountSelectionListItem contact) {
      AccountSelectionListFragment.this.dismiss();
      ConversationListActivity activity = (ConversationListActivity) requireActivity();
      int accountId = contact.getAccountId();
      if (accountId == NC_CONTACT_ID_ADD_ACCOUNT) {
        AccountManager.getInstance().switchAccountAndStartActivity(activity, 0);
      } else if (accountId != NcHelper.getAccounts(activity).getSelectedAccount().getAccountId()) {
        AccountManager.getInstance().switchAccount(activity, accountId);
        activity.onProfileSwitched(accountId);
      }
    }
  }
}

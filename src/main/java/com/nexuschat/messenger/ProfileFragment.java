package com.nexuschat.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.b44t.messenger.NcChat;
import com.b44t.messenger.NcChatlist;
import com.b44t.messenger.NcContact;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.nexuschat.messenger.connect.NcEventCenter;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.mms.GlideApp;
import com.nexuschat.messenger.qr.QrShowActivity;
import com.nexuschat.messenger.util.Util;
import com.nexuschat.messenger.util.ViewUtil;

public class ProfileFragment extends Fragment
    implements ProfileAdapter.ItemClickListener, NcEventCenter.NcEventDelegate {

  private static final String TAG = ProfileFragment.class.getSimpleName();
  public static final String CHAT_ID_EXTRA = "chat_id";
  public static final String CONTACT_ID_EXTRA = "contact_id";

  private ActivityResultLauncher<Intent> pickContactLauncher;
  private ProfileAdapter adapter;
  private ActionMode actionMode;
  private final ActionModeCallback actionModeCallback = new ActionModeCallback();

  private NcContext ncContext;
  protected int chatId;
  private int contactId;
  private RecyclerView recyclerView;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    if (getArguments() != null) {
      chatId = getArguments().getInt(CHAT_ID_EXTRA, -1);
      contactId = getArguments().getInt(CONTACT_ID_EXTRA, -1);
    }
    
    // Si no se especificó contactId, mostrar el perfil del usuario actual
    if (contactId == 0) {
      contactId = NcContact.NC_CONTACT_ID_SELF;
    }
    
    ncContext = NcHelper.getContext(requireContext());
    
    pickContactLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              Intent data = result.getData();
              Log.i(
                  TAG,
                  "Received result from activity, resultCode="
                      + result.getResultCode()
                      + ", data="
                      + data);
              if (result.getResultCode() == Activity.RESULT_OK && data != null) {
                List<Integer> selected =
                    data.getIntegerArrayListExtra(ContactMultiSelectionActivity.CONTACTS_EXTRA);
                List<Integer> deselected =
                    data.getIntegerArrayListExtra(
                        ContactMultiSelectionActivity.DESELECTED_CONTACTS_EXTRA);
                Util.runOnAnyBackgroundThread(
                    () -> {
                      if (deselected != null) { // Remove members that were deselected
                        Log.i(TAG, deselected.size() + " members removed");
                        int[] members = ncContext.getChatContacts(chatId);
                        for (int contactId : deselected) {
                          for (int memberId : members) {
                            if (memberId == contactId) {
                              ncContext.removeContactFromChat(chatId, memberId);
                              break;
                            }
                          }
                        }
                      }

                      if (selected != null) { // Add new members
                        Log.i(TAG, selected.size() + " members added");
                        for (Integer contactId : selected) {
                          if (contactId != null) {
                            ncContext.addContactToChat(chatId, contactId);
                          }
                        }
                      }
                    });
              }
            });
  }

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.profile_fragment, container, false);
    adapter = new ProfileAdapter(this, GlideApp.with(this), this);

    recyclerView = ViewUtil.findById(view, R.id.recycler_view);

    // add padding to avoid content hidden behind system bars
    ViewUtil.applyWindowInsets(recyclerView);

    recyclerView.setAdapter(adapter);
    recyclerView.setLayoutManager(
        new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));

    update();

    NcEventCenter eventCenter = NcHelper.getEventCenter(requireContext());
    eventCenter.addObserver(NcContext.NC_EVENT_CHAT_MODIFIED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_CONTACTS_CHANGED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_MSGS_CHANGED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_INCOMING_MSG, this);
    eventCenter.addObserver(NcContext.NC_EVENT_SELFAVATAR_CHANGED, this);
    return view;
  }

  @Override
  public void onDestroyView() {
    NcHelper.getEventCenter(requireContext()).removeObservers(this);
    super.onDestroyView();
  }

  @Override
  public void handleEvent(@NonNull NcEvent event) {
    update();
  }

  /**
   * CAMBIO: Método público para actualizar el perfil desde la actividad principal
   * Este método se llama cuando se vuelve a mostrar el fragmento
   */
  public void updateProfileData() {
    if (getActivity() != null && isAdded()) {
      update();
    }
  }

  private void update() {
    int[] memberList = null;
    NcChatlist sharedChats = null;

    NcChat ncChat = null;
    NcContact ncContact = null;
    
    if (contactId > 0) {
      ncContact = ncContext.getContact(contactId);
    }
    if (chatId > 0) {
      ncChat = ncContext.getChat(chatId);
    }

    if (ncChat != null && ncChat.isMultiUser()) {
      memberList = ncContext.getChatContacts(chatId);
    } else if (contactId > 0 && contactId != NcContact.NC_CONTACT_ID_SELF) {
      sharedChats = ncContext.getChatlist(0, null, contactId);
    }

    adapter.changeData(memberList, ncContact, sharedChats, ncChat);
  }

  // handle events
  // =========================================================================

  @Override
  public void onSettingsClicked(int settingsId) {
    switch (settingsId) {
      case ProfileAdapter.ITEM_ALL_MEDIA_BUTTON:
        if (chatId > 0) {
          Intent intent = new Intent(getActivity(), AllMediaActivity.class);
          intent.putExtra(AllMediaActivity.CHAT_ID_EXTRA, chatId);
          startActivity(intent);
        }
        break;
      case ProfileAdapter.ITEM_SEND_MESSAGE_BUTTON:
        onSendMessage();
        break;
      case ProfileAdapter.ITEM_INTRODUCED_BY:
        onVerifiedByClicked();
        break;
      case ProfileAdapter.ITEM_EDIT_PROFILE_BUTTON:
        onEditProfile();
        break;
    }
  }

  @Override
  public void onStatusLongClicked(boolean isMultiUser) {
    Context context = requireContext();
    new AlertDialog.Builder(context)
        .setTitle(isMultiUser ? R.string.chat_description : R.string.pref_default_status_label)
        .setItems(
            new CharSequence[] {context.getString(R.string.menu_copy_to_clipboard)},
            (dialogInterface, i) -> {
              Util.writeTextToClipboard(context, adapter.getStatusText());
              Toast.makeText(
                      context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT)
                  .show();
            })
        .setNegativeButton(R.string.cancel, null)
        .show();
  }

  @Override
  public void onMemberLongClicked(int contactId) {
    if (contactId > NcContact.NC_CONTACT_ID_LAST_SPECIAL
        || contactId == NcContact.NC_CONTACT_ID_SELF) {
      if (actionMode == null) {
        NcChat ncChat = ncContext.getChat(chatId);
        if (ncChat.canSend() && ncChat.isEncrypted()) {
          adapter.toggleMemberSelection(contactId);
          actionMode =
              ((AppCompatActivity) requireActivity()).startSupportActionMode(actionModeCallback);
        }
      } else {
        onMemberClicked(contactId);
      }
    }
  }

  @Override
  public void onMemberClicked(int contactId) {
    if (actionMode != null) {
      if (contactId > NcContact.NC_CONTACT_ID_LAST_SPECIAL
          || contactId == NcContact.NC_CONTACT_ID_SELF) {
        adapter.toggleMemberSelection(contactId);
        if (adapter.getSelectedMembersCount() == 0) {
          actionMode.finish();
          actionMode = null;
        } else {
          actionMode.setTitle(String.valueOf(adapter.getSelectedMembersCount()));
        }
      }
    } else if (contactId == NcContact.NC_CONTACT_ID_ADD_MEMBER) {
      onAddMember();
    } else if (contactId == NcContact.NC_CONTACT_ID_QR_INVITE) {
      onQrInvite();
    } else if (contactId > NcContact.NC_CONTACT_ID_LAST_SPECIAL) {
      Intent intent = new Intent(getContext(), ProfileActivity.class);
      intent.putExtra(ProfileActivity.CONTACT_ID_EXTRA, contactId);
      startActivity(intent);
    }
  }

  @Override
  public void onAvatarClicked() {
    ProfileActivity activity = (ProfileActivity) getActivity();
    activity.onEnlargeAvatar();
  }

  public void onAddMember() {
    NcChat ncChat = ncContext.getChat(chatId);
    Intent intent = new Intent(getContext(), ContactMultiSelectionActivity.class);
    ArrayList<Integer> preselectedContacts = new ArrayList<>();
    for (int memberId : ncContext.getChatContacts(chatId)) {
      preselectedContacts.add(memberId);
    }
    intent.putExtra(ContactSelectionListFragment.PRESELECTED_CONTACTS, preselectedContacts);
    pickContactLauncher.launch(intent);
  }

  public void onQrInvite() {
    Intent qrIntent = new Intent(getContext(), QrShowActivity.class);
    qrIntent.putExtra(QrShowActivity.CHAT_ID, chatId);
    startActivity(qrIntent);
  }

  /**
   * Editar perfil propio
   */
  private void onEditProfile() {
    Intent intent = new Intent(getContext(), ProfileActivity.class);
    intent.putExtra(ProfileActivity.CONTACT_ID_EXTRA, NcContact.NC_CONTACT_ID_SELF);
    startActivity(intent);
  }

  @Override
  public void onSharedChatClicked(int chatId) {
    Intent intent = new Intent(getContext(), ConversationActivity.class);
    intent.putExtra(ConversationActivity.CHAT_ID_EXTRA, chatId);
    requireContext().startActivity(intent);
    requireActivity().finish();
  }

  private void onVerifiedByClicked() {
    NcContact ncContact = ncContext.getContact(contactId);
    int verifierId = ncContact.getVerifierId();
    if (verifierId != 0 && verifierId != NcContact.NC_CONTACT_ID_SELF) {
      Intent intent = new Intent(getContext(), ProfileActivity.class);
      intent.putExtra(ProfileActivity.CONTACT_ID_EXTRA, verifierId);
      startActivity(intent);
    }
  }

  private void onSendMessage() {
    NcContact ncContact = ncContext.getContact(contactId);
    int chatId = ncContext.createChatByContactId(ncContact.getId());
    if (chatId != 0) {
      Intent intent = new Intent(getActivity(), ConversationActivity.class);
      intent.putExtra(ConversationActivity.CHAT_ID_EXTRA, chatId);
      requireActivity().startActivity(intent);
      requireActivity().finish();
    }
  }

  private class ActionModeCallback implements ActionMode.Callback {

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      mode.getMenuInflater().inflate(R.menu.profile_context, menu);
      menu.findItem(R.id.delete).setVisible(true);
      menu.findItem(R.id.details).setVisible(false);
      menu.findItem(R.id.show_in_chat).setVisible(false);
      menu.findItem(R.id.save).setVisible(false);
      menu.findItem(R.id.share).setVisible(false);
      menu.findItem(R.id.menu_resend).setVisible(false);
      menu.findItem(R.id.menu_select_all).setVisible(false);
      mode.setTitle("1");

      return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
      return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem menuItem) {
      if (menuItem.getItemId() == R.id.delete) {
        final Collection<Integer> toDelIds = adapter.getSelectedMembers();
        StringBuilder readableToDelList = new StringBuilder();
        for (Integer toDelId : toDelIds) {
          if (readableToDelList.length() > 0) {
            readableToDelList.append(", ");
          }
          readableToDelList.append(ncContext.getContact(toDelId).getDisplayName());
        }
        NcChat ncChat = ncContext.getChat(chatId);
        AlertDialog dialog =
            new AlertDialog.Builder(requireContext())
                .setPositiveButton(
                    R.string.remove_desktop,
                    (d, which) -> {
                      for (Integer toDelId : toDelIds) {
                        ncContext.removeContactFromChat(chatId, toDelId);
                      }
                      mode.finish();
                    })
                .setNegativeButton(android.R.string.cancel, null)
                .setMessage(
                    getString(
                        ncChat.isOutBroancast()
                            ? R.string.ask_remove_from_channel
                            : R.string.ask_remove_members,
                        readableToDelList))
                .show();
        Util.redPositiveButton(dialog);
        return true;
      }
      return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
      actionMode = null;
      adapter.clearSelection();
    }
  }
}
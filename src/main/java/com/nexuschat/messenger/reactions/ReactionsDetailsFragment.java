package com.nexuschat.messenger.reactions;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import chat.delta.rpc.Rpc;
import chat.delta.rpc.RpcException;
import chat.delta.rpc.types.Reactions;
import com.b44t.messenger.NcContact;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.nexuschat.messenger.ProfileActivity;
import com.nexuschat.messenger.R;
import com.nexuschat.messenger.connect.NcEventCenter;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.mms.GlideApp;
import com.nexuschat.messenger.util.Pair;
import com.nexuschat.messenger.util.ViewUtil;

public class ReactionsDetailsFragment extends DialogFragment
    implements NcEventCenter.NcEventDelegate {
  private static final String TAG = ReactionsDetailsFragment.class.getSimpleName();
  private static final String ARG_MSG_ID = "msg_id";

  private RecyclerView recyclerView;
  private ReactionRecipientsAdapter adapter;
  private int msgId;

  public static ReactionsDetailsFragment newInstance(int msgId) {
    ReactionsDetailsFragment fragment = new ReactionsDetailsFragment();
    Bundle args = new Bundle();
    args.putInt(ARG_MSG_ID, msgId);
    fragment.setArguments(args);
    return fragment;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    msgId = getArguments() != null ? getArguments().getInt(ARG_MSG_ID, 0) : 0;
    adapter =
        new ReactionRecipientsAdapter(
            requireActivity(), GlideApp.with(requireActivity()), new ListClickListener());

    LayoutInflater inflater = requireActivity().getLayoutInflater();
    View view = inflater.inflate(R.layout.reactions_details_fragment, null);
    recyclerView = ViewUtil.findById(view, R.id.recycler_view);
    recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

    recyclerView.setAdapter(adapter);

    refreshData();

    NcEventCenter eventCenter = NcHelper.getEventCenter(requireContext());
    eventCenter.addObserver(NcContext.NC_EVENT_REACTIONS_CHANGED, this);

    AlertDialog.Builder builder =
        new AlertDialog.Builder(requireActivity())
            .setTitle(R.string.reactions)
            .setNegativeButton(R.string.ok, null);
    return builder.setView(view).create();
  }

  @Override
  public void onDestroy() {
    Log.i(TAG, "onDestroy()");
    super.onDestroy();
    NcHelper.getEventCenter(requireActivity()).removeObservers(this);
  }

  @Override
  public void handleEvent(@NonNull NcEvent event) {
    if (event.getId() == NcContext.NC_EVENT_REACTIONS_CHANGED) {
      if (event.getData2Int() == msgId) {
        refreshData();
      }
    }
  }

  private void refreshData() {
    if (recyclerView == null) return;

    int accId = NcHelper.getContext(requireActivity()).getAccountId();
    try {
      final Reactions reactions =
          NcHelper.getRpc(requireActivity()).getMessageReactions(accId, msgId);
      ArrayList<Pair<Integer, String>> contactsReactions = new ArrayList<>();
      if (reactions != null) {
        Map<String, List<String>> reactionsByContact = reactions.reactionsByContact;
        List<String> selfReactions =
            reactionsByContact.remove(String.valueOf(NcContact.NC_CONTACT_ID_SELF));
        for (String contact : reactionsByContact.keySet()) {
          for (String reaction : reactionsByContact.get(contact)) {
            contactsReactions.add(new Pair<>(Integer.parseInt(contact), reaction));
          }
        }
        if (selfReactions != null) {
          for (String reaction : selfReactions) {
            contactsReactions.add(new Pair<>(NcContact.NC_CONTACT_ID_SELF, reaction));
          }
        }
      }
      adapter.changeData(contactsReactions);
    } catch (RpcException e) {
      e.printStackTrace();
    }
  }

  private void openConversation(int contactId) {
    Intent intent = new Intent(getContext(), ProfileActivity.class);
    intent.putExtra(ProfileActivity.CONTACT_ID_EXTRA, contactId);
    requireContext().startActivity(intent);
  }

  private String getSelfReaction(Rpc rpc, int accId) {
    String result = null;
    try {
      final Reactions reactions = rpc.getMessageReactions(accId, msgId);
      if (reactions != null) {
        final Map<String, List<String>> reactionsByContact = reactions.reactionsByContact;
        final List<String> selfReactions =
            reactionsByContact.get(String.valueOf(NcContact.NC_CONTACT_ID_SELF));
        if (selfReactions != null && !selfReactions.isEmpty()) {
          result = selfReactions.get(0);
        }
      }
    } catch (RpcException e) {
      e.printStackTrace();
    }
    return result;
  }

  private void sendReaction(final String reaction) {
    Rpc rpc = NcHelper.getRpc(requireActivity());
    NcContext ncContext = NcHelper.getContext(requireActivity());
    int accId = ncContext.getAccountId();

    try {
      if (reaction == null || reaction.equals(getSelfReaction(rpc, accId))) {
        rpc.sendReaction(accId, msgId, Collections.singletonList(""));
      } else {
        rpc.sendReaction(accId, msgId, Collections.singletonList(reaction));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private class ListClickListener implements ReactionRecipientsAdapter.ItemClickListener {

    @Override
    public void onItemClick(ReactionRecipientItem item) {
      int contactId = item.getContactId();
      if (contactId != NcContact.NC_CONTACT_ID_SELF) {
        ReactionsDetailsFragment.this.dismiss();
        openConversation(contactId);
      }
    }

    @Override
    public void onReactionClick(ReactionRecipientItem item) {
      sendReaction(item.getReaction());
      ReactionsDetailsFragment.this.dismiss();
    }
  }
}

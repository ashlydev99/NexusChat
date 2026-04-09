package com.nexuschat.messenger.search;

import static com.nexuschat.messenger.util.ShareUtil.isRelayingMessageContent;

import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.b44t.messenger.NcChat;
import com.b44t.messenger.NcChatlist;
import com.b44t.messenger.NcContact;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcEvent;
import com.b44t.messenger.NcMsg;
import java.util.Set;
import com.nexuschat.messenger.BaseConversationListAdapter;
import com.nexuschat.messenger.BaseConversationListFragment;
import com.nexuschat.messenger.ConversationListActivity;
import com.nexuschat.messenger.R;
import com.nexuschat.messenger.connect.NcEventCenter;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.mms.GlideApp;
import com.nexuschat.messenger.search.model.SearchResult;
import com.nexuschat.messenger.util.StickyHeaderDecoration;

/** A fragment that is displayed to do full-text search of messages, groups, and contacts. */
public class SearchFragment extends BaseConversationListFragment
    implements SearchListAdapter.EventListener, NcEventCenter.NcEventDelegate {

  public static final String TAG = "SearchFragment";

  private TextView noResultsView;
  private StickyHeaderDecoration listDecoration;

  private SearchViewModel viewModel;
  private SearchListAdapter listAdapter;
  private String pendingQuery;

  public static SearchFragment newInstance() {
    Bundle args = new Bundle();

    SearchFragment fragment = new SearchFragment();
    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    viewModel =
        ViewModelProviders.of(
                this, (ViewModelProvider.Factory) new SearchViewModel.Factory(requireContext()))
            .get(SearchViewModel.class);
    NcEventCenter eventCenter = NcHelper.getEventCenter(requireContext());
    eventCenter.addObserver(NcContext.NC_EVENT_CHAT_MODIFIED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_CONTACTS_CHANGED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_INCOMING_MSG, this);
    eventCenter.addObserver(NcContext.NC_EVENT_MSGS_CHANGED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_MSGS_NOTICED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_MSG_DELIVERED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_MSG_FAILED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_MSG_READ, this);

    if (pendingQuery != null) {
      viewModel.updateQuery(pendingQuery);
      pendingQuery = null;
    }
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_search, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    noResultsView = view.findViewById(R.id.search_no_results);
    RecyclerView listView = view.findViewById(R.id.search_list);
    fab = view.findViewById(R.id.fab);

    listAdapter = new SearchListAdapter(getContext(), GlideApp.with(this), this);
    listDecoration = new StickyHeaderDecoration(listAdapter, false, true);

    fab.setVisibility(View.GONE);
    listView.setAdapter(listAdapter);
    listView.addItemDecoration(listDecoration);
    listView.setLayoutManager(new LinearLayoutManager(getContext()));
  }

  @Override
  public void onStart() {
    super.onStart();
    viewModel.setForwardingMode(isRelayingMessageContent(getActivity()));
    viewModel
        .getSearchResult()
        .observe(
            this,
            result -> {
              result = result != null ? result : SearchResult.EMPTY;

              listAdapter.updateResults(result);
              listDecoration.invalidateLayouts();

              if (result.isEmpty()) {
                if (TextUtils.isEmpty(viewModel.getLastQuery().trim())) {
                  noResultsView.setVisibility(View.GONE);
                } else {
                  noResultsView.setVisibility(View.VISIBLE);
                  noResultsView.setText(
                      getString(R.string.search_no_result_for_x, viewModel.getLastQuery()));
                }
              } else {
                noResultsView.setVisibility(View.VISIBLE);
                noResultsView.setText("");
              }
            });
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);

    if (listDecoration != null) {
      listDecoration.onConfigurationChanged(newConfig);
    }
  }

  @Override
  public void onDestroy() {
    NcHelper.getEventCenter(requireContext()).removeObservers(this);
    super.onDestroy();
  }

  @Override
  public void onConversationClicked(@NonNull NcChatlist.Item chatlistItem) {
    onItemClick(chatlistItem.chatId);
  }

  @Override
  public void onConversationLongClicked(@NonNull NcChatlist.Item chatlistItem) {
    onItemLongClick(chatlistItem.chatId);
  }

  @Override
  public void onContactClicked(@NonNull NcContact contact) {
    if (actionMode != null) {
      return;
    }

    ConversationListActivity conversationList = (ConversationListActivity) getActivity();
    if (conversationList != null) {
      NcContext ncContext = NcHelper.getContext(requireContext());
      int chatId = ncContext.getChatIdByContactId(contact.getId());
      if (chatId == 0) {
        new AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.ask_start_chat_with, contact.getDisplayName()))
            .setCancelable(true)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                android.R.string.ok,
                (dialog, which) -> {
                  int chatId1 = ncContext.createChatByContactId(contact.getId());
                  conversationList.onCreateConversation(chatId1);
                })
            .show();
      } else {
        conversationList.onCreateConversation(chatId);
      }
    }
  }

  @Override
  public void onMessageClicked(@NonNull NcMsg message) {
    if (actionMode != null) {
      return;
    }

    ConversationListActivity conversationList = (ConversationListActivity) getActivity();
    if (conversationList != null) {
      NcContext ncContext = NcHelper.getContext(requireContext());
      int chatId = message.getChatId();
      int startingPosition = NcMsg.getMessagePosition(message, ncContext);
      conversationList.openConversation(chatId, startingPosition);
    }
  }

  public void updateSearchQuery(@NonNull String query) {
    if (viewModel != null) {
      viewModel.updateQuery(query);
    } else {
      pendingQuery = query;
    }
  }

  @Override
  public void handleEvent(@NonNull NcEvent event) {
    if (viewModel != null) {
      viewModel.updateQuery();
    }
  }

  @Override
  protected boolean offerToArchive() {
    NcContext ncContext = NcHelper.getContext(requireActivity());
    final Set<Long> selectedChats = listAdapter.getBatchSelections();
    for (long chatId : selectedChats) {
      NcChat ncChat = ncContext.getChat((int) chatId);
      if (ncChat.getVisibility() != NcChat.NC_CHAT_VISIBILITY_ARCHIVED) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void setFabVisibility(boolean isActionMode) {
    if (isActionMode && isRelayingMessageContent(getActivity())) {
      fab.setVisibility(View.VISIBLE);
    } else {
      fab.setVisibility(View.GONE);
    }
  }

  @Override
  protected BaseConversationListAdapter getListAdapter() {
    return listAdapter;
  }
}

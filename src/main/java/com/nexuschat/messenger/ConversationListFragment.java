/*
 * Copyright (C) 2015 Open Whisper Systems
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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.b44t.messenger.NcChatlist;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcEvent;
import com.b44t.messenger.NcMsg;
import java.util.Timer;
import java.util.TimerTask;
import com.nexuschat.messenger.ConversationListAdapter.ItemClickListener;
import com.nexuschat.messenger.components.recyclerview.DeleteItemAnimator;
import com.nexuschat.messenger.components.reminder.DozeReminder;
import com.nexuschat.messenger.connect.NcEventCenter;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.mms.GlideApp;
import com.nexuschat.messenger.notifications.FcmReceiveService;
import com.nexuschat.messenger.permissions.Permissions;
import com.nexuschat.messenger.util.Prefs;
import com.nexuschat.messenger.util.ShareUtil;
import com.nexuschat.messenger.util.Util;
import com.nexuschat.messenger.util.ViewUtil;

public class ConversationListFragment extends BaseConversationListFragment
    implements ItemClickListener, NcEventCenter.NcEventDelegate {
  public static final String ARCHIVE = "archive";
  public static final String RELOAD_LIST = "reload_list";

  private static final String TAG = ConversationListFragment.class.getSimpleName();

  private RecyclerView list;
  private View emptyState;
  private TextView emptySearch;
  private final String queryFilter = "";
  private boolean archive;
  private Timer reloadTimer;
  private boolean chatlistJustLoaded;
  private boolean reloadTimerInstantly;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    archive = getArguments().getBoolean(ARCHIVE, false);

    NcEventCenter eventCenter = NcHelper.getEventCenter(requireActivity());
    eventCenter.addMultiAccountObserver(NcContext.NC_EVENT_INCOMING_MSG, this);
    eventCenter.addMultiAccountObserver(NcContext.NC_EVENT_MSGS_NOTICED, this);
    eventCenter.addMultiAccountObserver(NcContext.NC_EVENT_CHAT_DELETED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_CHAT_MODIFIED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_CONTACTS_CHANGED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_MSGS_CHANGED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_MSG_DELIVERED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_MSG_FAILED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_MSG_READ, this);
    eventCenter.addObserver(NcContext.NC_EVENT_REACTIONS_CHANGED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_CONNECTIVITY_CHANGED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_SELFAVATAR_CHANGED, this);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    NcHelper.getEventCenter(requireActivity()).removeObservers(this);
  }

  @SuppressLint("RestrictedApi")
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    final View view = inflater.inflate(R.layout.conversation_list_fragment, container, false);

    list = ViewUtil.findById(view, R.id.list);
    fab = ViewUtil.findById(view, R.id.fab);
    emptyState = ViewUtil.findById(view, R.id.empty_state);
    emptySearch = ViewUtil.findById(view, R.id.empty_search);

    // add padding to avoid content hidden behind system bars
    ViewUtil.applyWindowInsets(list, true, archive, true, true);

    // CAMBIO #4: FAB oculto permanentemente porque usamos navegación inferior
    if (fab != null) {
      fab.setVisibility(View.GONE);
    }
    
    if (archive) {
      if (fab != null) {
        fab.setVisibility(View.GONE);
      }
      TextView emptyTitle = ViewUtil.findById(view, R.id.empty_title);
      emptyTitle.setText(R.string.archive_empty_hint);
    }
    // Apply insets to prevent fab from being covered by system bars
    if (fab != null) {
      ViewUtil.applyWindowInsetsAsMargin(fab);
    }

    list.setHasFixedSize(true);
    list.setLayoutManager(new LinearLayoutManager(getActivity()));
    list.setItemAnimator(new DeleteItemAnimator());

    return view;
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);

    setHasOptionsMenu(true);
    initializeFabClickListener(false);
    list.setAdapter(new ConversationListAdapter(requireActivity(), GlideApp.with(this), this));
    loadChatlistAsync();
    chatlistJustLoaded = true;
  }

  @Override
  public void onResume() {
    super.onResume();

    updateReminders();

    if (requireActivity().getIntent().getIntExtra(RELOAD_LIST, 0) == 1 && !chatlistJustLoaded) {
      loadChatlist();
      reloadTimerInstantly = false;
    }
    chatlistJustLoaded = false;

    reloadTimer = new Timer();
    reloadTimer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            Util.runOnMain(
                () -> {
                  list.getAdapter().notifyDataSetChanged();
                });
          }
        },
        reloadTimerInstantly ? 0 : 60 * 1000,
        60 * 1000);
  }

  @Override
  public void onPause() {
    super.onPause();

    reloadTimer.cancel();
    reloadTimerInstantly = true;

    if (fab != null) {
      fab.stopPulse();
    }
  }

  public void onNewIntent() {
    initializeFabClickListener(actionMode != null);
  }

  @Override
  public BaseConversationListAdapter getListAdapter() {
    return (BaseConversationListAdapter) list.getAdapter();
  }

  @SuppressLint({"StaticFieldLeak", "NewApi"})
  private void updateReminders() {
    // by the time onPostExecute() is asynchronously run, getActivity() might return null, so get
    // the activity here:
    Activity activity = requireActivity();
    new AsyncTask<Context, Void, Void>() {
      @Override
      protected Void doInBackground(Context... params) {
        final Context context = params[0];
        try {
          if (DozeReminder.isEligible(context)) {
            DozeReminder.addDozeReminderDeviceMsg(context);
          }
          FcmReceiveService.waitForRegisterFinished();
        } catch (Exception e) {
          e.printStackTrace();
        }
        return null;
      }

      @Override
      protected void onPostExecute(Void result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          if (!Prefs.getBooleanPreference(
              activity, Prefs.ASKED_FOR_NOTIFICATION_PERMISSION, false)) {
            Prefs.setBooleanPreference(activity, Prefs.ASKED_FOR_NOTIFICATION_PERMISSION, true);
            Permissions.with(activity)
                .request(Manifest.permission.POST_NOTIFICATIONS)
                .ifNecessary()
                .onAllGranted(
                    () -> {
                      DozeReminder.maybeAskDirectly(activity);
                    })
                .onAnyDenied(
                    () -> {
                      final NcContext ncContext = NcHelper.getContext(activity);
                      NcMsg msg = new NcMsg(ncContext, NcMsg.NC_MSG_TEXT);
                      msg.setText(
                          "\uD83D\uNC49 "
                              + activity.getString(R.string.notifications_disabled)
                              + " \uD83D\uNC48\n\n"
                              + activity.getString(
                                  R.string.perm_explain_access_to_notifications_denied));
                      ncContext.addDeviceMsg("android.notifications-disabled", msg);
                    })
                .execute();
          } else {
            DozeReminder.maybeAskDirectly(activity);
          }
        } else {
          DozeReminder.maybeAskDirectly(activity);
        }
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, activity);
  }

  private final Object loadChatlistLock = new Object();
  private boolean inLoadChatlist;
  private boolean needsAnotherLoad;

  public void loadChatlistAsync() {
    synchronized (loadChatlistLock) {
      needsAnotherLoad = true;
      if (inLoadChatlist) {
        Log.i(TAG, "chatlist loading debounced");
        return;
      }
      inLoadChatlist = true;
    }

    Util.runOnAnyBackgroundThread(
        () -> {
          while (true) {
            synchronized (loadChatlistLock) {
              if (!needsAnotherLoad) {
                inLoadChatlist = false;
                return;
              }
              needsAnotherLoad = false;
            }

            Log.i(TAG, "executing debounced chatlist loading");
            loadChatlist();
            Util.sleep(100);
          }
        });
  }

  private void loadChatlist() {
    int listflags = 0;
    if (archive) {
      listflags |= NcContext.NC_GCL_ARCHIVED_ONLY;
    } else if (ShareUtil.isRelayingMessageContent(getActivity())) {
      listflags |= NcContext.NC_GCL_FOR_FORWARDING;
    } else {
      listflags |= NcContext.NC_GCL_ADD_ALLDONE_HINT;
    }

    Context context = getContext();
    if (context == null) {
    	
      Log.w(TAG, "Ignoring call to loadChatlist()");
      return;
    }
    NcChatlist chatlist =
        NcHelper.getContext(context)
            .getChatlist(listflags, queryFilter.isEmpty() ? null : queryFilter, 0);

    Util.runOnMain(
        () -> {
          if (chatlist.getCnt() <= 0 && TextUtils.isEmpty(queryFilter)) {
            list.setVisibility(View.INVISIBLE);
            emptyState.setVisibility(View.VISIBLE);
            emptySearch.setVisibility(View.INVISIBLE);
            // CAMBIO #4: No iniciamos pulso del FAB porque está oculto
            // if (fab != null) fab.startPulse(3 * 1000);
          } else if (chatlist.getCnt() <= 0 && !TextUtils.isEmpty(queryFilter)) {
            list.setVisibility(View.INVISIBLE);
            emptyState.setVisibility(View.GONE);
            emptySearch.setVisibility(View.VISIBLE);
            emptySearch.setText(getString(R.string.search_no_result_for_x, queryFilter));
          } else {
            list.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            emptySearch.setVisibility(View.INVISIBLE);
            if (fab != null) {
              fab.stopPulse();
            }
          }

          ((ConversationListAdapter) list.getAdapter()).changeData(chatlist);
        });
  }

  @Override
  protected boolean offerToArchive() {
    return !archive;
  }

  @Override
  protected void setFabVisibility(boolean isActionMode) {
    // CAMBIO #4: FAB siempre oculto por la navegación inferior
    if (fab != null) {
      fab.setVisibility(View.GONE);
    }
  }

  @Override
  public void onItemClick(ConversationListItem item) {
    onItemClick(item.getChatId());
  }

  @Override
  public void onItemLongClick(ConversationListItem item) {
    onItemLongClick(item.getChatId());
  }

  @Override
  public void onSwitchToArchive() {
    ((ConversationSelectedListener) requireActivity()).onSwitchToArchive();
  }

  @Override
  public void handleEvent(@NonNull NcEvent event) {
    final int accId = event.getAccountId();
    if (event.getId() == NcContext.NC_EVENT_CHAT_DELETED) {
      NcHelper.getNotificationCenter(requireActivity())
          .removeNotifications(accId, event.getData1Int());
    } else if (accId != NcHelper.getContext(requireActivity()).getAccountId()) {
      Activity activity = getActivity();
      if (activity instanceof ConversationListActivity) {
        ((ConversationListActivity) activity).refreshUnreadIndicator();
      }

    } else if (event.getId() == NcContext.NC_EVENT_CONNECTIVITY_CHANGED) {
      Activity activity = getActivity();
      if (activity instanceof ConversationListActivity) {
        ((ConversationListActivity) activity).refreshTitle();
      }

    } else if (event.getId() == NcContext.NC_EVENT_SELFAVATAR_CHANGED) {
      Activity activity = getActivity();
      if (activity instanceof ConversationListActivity) {
        ((ConversationListActivity) activity).refreshAvatar();
      }

    } else {
      loadChatlistAsync();
    }
  }
}
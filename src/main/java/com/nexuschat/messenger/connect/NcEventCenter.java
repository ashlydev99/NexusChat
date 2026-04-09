package com.nexuschat.messenger.connect;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcEvent;
import java.util.ArrayList;
import java.util.Hashtable;
import com.nexuschat.messenger.ApplicationContext;
import com.nexuschat.messenger.R;
import com.nexuschat.messenger.service.FetchForegroundService;
import com.nexuschat.messenger.util.Util;

public class NcEventCenter {
  private static final String TAG = NcEventCenter.class.getSimpleName();
  private @NonNull final Hashtable<Integer, ArrayList<NcEventDelegate>> currentAccountObservers =
      new Hashtable<>();
  private @NonNull final Hashtable<Integer, ArrayList<DcEventDelegate>> multiAccountObservers =
      new Hashtable<>();
  private final Object LOCK = new Object();
  private final @NonNull ApplicationContext context;

  public interface NcEventDelegate {
    void handleEvent(@NonNull NcEvent event);

    default boolean runOnMain() {
      return true;
    }
  }

  public NcEventCenter(@NonNull Context context) {
    this.context = ApplicationContext.getInstance(context);
  }

  public void addObserver(int eventId, @NonNull NcEventDelegate observer) {
    addObserver(currentAccountObservers, eventId, observer);
  }

  public void addMultiAccountObserver(int eventId, @NonNull NcEventDelegate observer) {
    addObserver(multiAccountObservers, eventId, observer);
  }

  private void addObserver(
      Hashtable<Integer, ArrayList<NcEventDelegate>> observers,
      int eventId,
      @NonNull NcEventDelegate observer) {
    synchronized (LOCK) {
      ArrayList<NcEventDelegate> idObservers = observers.get(eventId);
      if (idObservers == null) {
        observers.put(eventId, (idObservers = new ArrayList<>()));
      }
      idObservers.add(observer);
    }
  }

  public void removeObserver(int eventId, NcEventDelegate observer) {
    synchronized (LOCK) {
      ArrayList<NcEventDelegate> idObservers = currentAccountObservers.get(eventId);
      if (idObservers != null) {
        idObservers.remove(observer);
      }
      idObservers = multiAccountObservers.get(eventId);
      if (idObservers != null) {
        idObservers.remove(observer);
      }
    }
  }

  public void removeObservers(NcEventDelegate observer) {
    synchronized (LOCK) {
      for (Integer eventId : currentAccountObservers.keySet()) {
        ArrayList<NcEventDelegate> idObservers = currentAccountObservers.get(eventId);
        if (idObservers != null) {
          idObservers.remove(observer);
        }
      }
      for (Integer eventId : multiAccountObservers.keySet()) {
        ArrayList<NcEventDelegate> idObservers = multiAccountObservers.get(eventId);
        if (idObservers != null) {
          idObservers.remove(observer);
        }
      }
    }
  }

  private void sendToMultiAccountObservers(@NonNull NcEvent event) {
    sendToObservers(multiAccountObservers, event);
  }

  private void sendToCurrentAccountObservers(@NonNull NcEvent event) {
    sendToObservers(currentAccountObservers, event);
  }

  private void sendToObservers(
      Hashtable<Integer, ArrayList<NcEventDelegate>> observers, @NonNull NcEvent event) {
    synchronized (LOCK) {
      ArrayList<NcEventDelegate> idObservers = observers.get(event.getId());
      if (idObservers != null) {
        for (NcEventDelegate observer : idObservers) {
          // using try/catch blocks as under some circumstances eg. getContext() may return NULL -
          // and as this function is used virtually everywhere, also in libs,
          // it's not feasible to check all single occurrences.
          if (observer.runOnMain()) {
            Util.runOnMain(
                () -> {
                  try {
                    observer.handleEvent(event);
                  } catch (Exception e) {
                    Log.e(TAG, "Error calling observer.handleEvent()", e);
                  }
                });
          } else {
            Util.runOnBackground(
                () -> {
                  try {
                    observer.handleEvent(event);
                  } catch (Exception e) {
                    Log.e(TAG, "Error calling observer.handleEvent()", e);
                  }
                });
          }
        }
      }
    }
  }

  private final Object lastErrorLock = new Object();
  private boolean showNextErrorAsToast = true;

  public void captureNextError() {
    synchronized (lastErrorLock) {
      showNextErrorAsToast = false;
    }
  }

  public void endCaptureNextError() {
    synchronized (lastErrorLock) {
      showNextErrorAsToast = true;
    }
  }

  private void handleError(int event, String string) {
    // log error
    boolean showAsToast;
    Log.e("NexusChat", string);
    synchronized (lastErrorLock) {
      showAsToast = showNextErrorAsToast;
      showNextErrorAsToast = true;
    }

    // show error to user
    Util.runOnMain(
        () -> {
          if (showAsToast) {
            String toastString = null;

            if (event == NcContext.NC_EVENT_ERROR_SELF_NOT_IN_GROUP) {
              toastString = context.getString(R.string.group_self_not_in_group);
            }

            ForegroundDetector foregroundDetector = ForegroundDetector.getInstance();
            if (toastString != null
                && (foregroundDetector == null || foregroundDetector.isForeground())) {
              Toast.makeText(context, toastString, Toast.LENGTH_LONG).show();
            }
          }
        });
  }

  public void handleLogging(@NonNull NcEvent event) {
    final String logPrefix = "[accId=" + event.getAccountId() + "] ";
    switch (event.getId()) {
      case NcContext.NC_EVENT_INFO:
        Log.i("NexusChat", logPrefix + event.getData2Str());
        break;

      case NcContext.NC_EVENT_WARNING:
        Log.w("NexusChat", logPrefix + event.getData2Str());
        break;

      case NcContext.NC_EVENT_ERROR:
        Log.e("NexusChat", logPrefix + event.getData2Str());
        break;
    }
  }

  public long handleEvent(@NonNull NcEvent event) {
    int accountId = event.getAccountId();
    int id = event.getId();

    sendToMultiAccountObservers(event);

    switch (id) {
      case NcContext.NC_EVENT_INCOMING_MSG:
        NcHelper.getNotificationCenter(context)
            .notifyMessage(accountId, event.getData1Int(), event.getData2Int());
        break;

      case NcContext.NC_EVENT_INCOMING_REACTION:
        NcHelper.getNotificationCenter(context)
            .notifyReaction(
                accountId, event.getData1Int(), event.getData2Int(), event.getData2Str());
        break;

      case NcContext.NC_EVENT_INCOMING_WEBXDC_NOTIFY:
        NcHelper.getNotificationCenter(context)
            .notifyWebxdc(accountId, event.getData1Int(), event.getData2Int(), event.getData2Str());
        break;

      case NcContext.NC_EVENT_MSGS_NOTICED:
        NcHelper.getNotificationCenter(context).removeNotifications(accountId, event.getData1Int());
        break;

      case NcContext.NC_EVENT_MSG_DELETED:
        NcHelper.getNotificationCenter(context)
            .removeNotification(accountId, event.getData1Int(), event.getData2Int());
        break;

      case NcContext.NC_EVENT_ACCOUNTS_BACKGROUND_FETCH_DONE:
        FetchForegroundService.stop(context);
        break;

      case NcContext.NC_EVENT_IMEX_PROGRESS:
        sendToCurrentAccountObservers(event);
        return 0;
    }

    handleLogging(event);

    if (accountId != context.getNcContext().getAccountId()) {
      return 0;
    }

    switch (id) {
      case NcContext.NC_EVENT_ERROR:
      case NcContext.NC_EVENT_ERROR_SELF_NOT_IN_GROUP:
        handleError(id, event.getData2Str());
        break;

      default:
        sendToCurrentAccountObservers(event);
        break;
    }

    if (id == NcContext.NC_EVENT_CHAT_MODIFIED) {
      // Possibly a chat was deleted or the avatar was changed, directly refresh DirectShare so that
      // a new chat can move up / the chat avatar change is populated
      DirectShareUtil.triggerRefreshDirectShare(context);
    }

    return 0;
  }
}

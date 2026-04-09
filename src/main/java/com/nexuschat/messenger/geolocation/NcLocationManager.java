package com.nexuschat.messenger.geolocation;

import static android.content.Context.BIND_AUTO_CREATE;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.IBinder;
import android.util.Log;
import com.b44t.messenger.NcContext;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;
import com.nexuschat.messenger.connect.NcHelper;

public class NcLocationManager implements Observer {

  private static final String TAG = NcLocationManager.class.getSimpleName();
  private LocationBackgroundService.LocationBackgroundServiceBinder serviceBinder;
  private final Context context;
  private NcLocation ncLocation = NcLocation.getInstance();
  private final LinkedList<Integer> pendingShareLastLocation = new LinkedList<>();
  private final ServiceConnection serviceConnection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
          Log.d(TAG, "background service connected");
          serviceBinder = (LocationBackgroundService.LocationBackgroundServiceBinder) service;
          while (!pendingShareLastLocation.isEmpty()) {
            shareLastLocation(pendingShareLastLocation.pop());
          }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
          Log.d(TAG, "background service disconnected");
          serviceBinder = null;
        }
      };

  public NcLocationManager(Context context, NcContext ncContext) {
    this.context = context.getApplicationContext();
    NcLocation.getInstance().addObserver(this);
    if (ncContext.isSendingLocationsToChat(0)) {
      startLocationEngine();
    }
  }

  public void startLocationEngine() {
    if (serviceBinder == null) {
      Intent intent = new Intent(context.getApplicationContext(), LocationBackgroundService.class);
      context.bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }
  }

  public void stopLocationEngine() {
    if (serviceBinder == null) {
      return;
    }
    context.unbindService(serviceConnection);
    serviceBinder.stop();
    serviceBinder = null;
  }

  public void stopSharingLocation(int chatId) {
    NcHelper.getContext(context).sendLocationsToChat(chatId, 0);
    if (!NcHelper.getContext(context).isSendingLocationsToChat(0)) {
      stopLocationEngine();
    }
  }

  public void shareLocation(int duration, int chatId) {
    startLocationEngine();
    Log.d(TAG, String.format("Share location in chat %d for %d seconds", chatId, duration));
    NcHelper.getContext(context).sendLocationsToChat(chatId, duration);
    if (ncLocation.isValid()) {
      writeNcLocationUpdateMessage();
    }
  }

  public void shareLastLocation(int chatId) {
    if (serviceBinder == null) {
      pendingShareLastLocation.push(chatId);
      startLocationEngine();
      return;
    }

    if (ncLocation.isValid()) {
      NcHelper.getContext(context).sendLocationsToChat(chatId, 1);
      writeNcLocationUpdateMessage();
    }
  }

  @Override
  public void update(Observable o, Object arg) {
    if (o instanceof NcLocation) {
      dcLocation = (NcLocation) o;
      if (ncLocation.isValid()) {
        writeNcLocationUpdateMessage();
      }
    }
  }

  private void writeNcLocationUpdateMessage() {
    Log.d(
        TAG,
        "Share location: "
            + ncLocation.getLastLocation().getLatitude()
            + ", "
            + ncLocation.getLastLocation().getLongitude());
    Location lastLocation = ncLocation.getLastLocation();

    boolean continueLocationStreaming =
        NcHelper.getContext(context)
            .setLocation(
                (float) lastLocation.getLatitude(),
                (float) lastLocation.getLongitude(),
                lastLocation.getAccuracy());
    if (!continueLocationStreaming) {
      stopLocationEngine();
    }
  }
}

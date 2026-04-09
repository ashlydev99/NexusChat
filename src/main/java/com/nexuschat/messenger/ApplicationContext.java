package com.nexuschat.messenger;

import android.content.BroancastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationManagerCompat;
import androidx.multidex.MultiDexApplication;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import chat.nexus.rpc.Rpc;
import chat.nexus.rpc.RpcException;
import com.b44t.messenger.NcAccounts;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcEvent;
import com.b44t.messenger.NcEventChannel;
import com.b44t.messenger.NcEventEmitter;
import com.b44t.messenger.FFITransport;
import java.io.File;
import java.util.concurrent.TimeUnit;
import com.nexuschat.messenger.calls.CallCoordinator;
import com.nexuschat.messenger.connect.AccountManager;
import com.nexuschat.messenger.connect.NcEventCenter;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.connect.FetchWorker;
import com.nexuschat.messenger.connect.ForegroundDetector;
import com.nexuschat.messenger.connect.KeepAliveService;
import com.nexuschat.messenger.connect.NetworkStateReceiver;
import com.nexuschat.messenger.crypto.DatabaseSecret;
import com.nexuschat.messenger.crypto.DatabaseSecretProvider;
import com.nexuschat.messenger.geolocation.NcLocationManager;
import com.nexuschat.messenger.jobmanager.JobManager;
import com.nexuschat.messenger.notifications.FcmReceiveService;
import com.nexuschat.messenger.notifications.InChatSounds;
import com.nexuschat.messenger.notifications.NotificationCenter;
import com.nexuschat.messenger.util.AndroidSignalProtocolLogger;
import com.nexuschat.messenger.util.DynamicTheme;
import com.nexuschat.messenger.util.Prefs;
import com.nexuschat.messenger.util.SignalProtocolLoggerProvider;
import com.nexuschat.messenger.util.Util;
import com.nexuschat.messenger.webxnc.WebxncGarbageCollectionWorker;

public class ApplicationContext extends MultiDexApplication {
  private static final String TAG = ApplicationContext.class.getSimpleName();
  private static final Object initLock = new Object();
  private static volatile boolean isInitialized = false;

  private static NcAccounts ncAccounts;
  private Rpc rpc;
  private NcContext ncContext;

  private NcLocationManager ncLocationManager;
  private NcEventCenter eventCenter;
  private NotificationCenter notificationCenter;
  private JobManager jobManager;

  private int debugOnAvailableCount;
  private int debugOnBlockedStatusChangedCount;
  private int debugOnCapabilitiesChangedCount;
  private int debugOnLinkPropertiesChangedCount;

  public static ApplicationContext getInstance(@NonNull Context context) {
    return (ApplicationContext) context.getApplicationContext();
  }

  private static void ensureInitialized() {
    synchronized (initLock) {
      while (!isInitialized) {
        try {
          initLock.wait();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted while waiting for initialization", e);
        }
      }
    }
  }

  /**
   * Get NcAccounts instance, waiting for initialization if necessary. This method is thread-safe
   * and will block until initialization is complete.
   */
  public static NcAccounts getNcAccounts() {
    ensureInitialized();
    return ncAccounts;
  }

  /**
   * Get Rpc instance, waiting for initialization if necessary. This method is thread-safe and will
   * block until initialization is complete.
   */
  public Rpc getRpc() {
    ensureInitialized();
    return rpc;
  }

  /**
   * Get NcContext instance, waiting for initialization if necessary. This method is thread-safe and
   * will block until initialization is complete.
   */
  public NcContext getNcContext() {
    ensureInitialized();
    return ncContext;
  }

  /**
   * Set NcContext instance. This should only be called by AccountManager when switching accounts,
   * which only happens after initial initialization is complete. This method is thread-safe but
   * does NOT trigger initialization or notify waiting threads.
   */
  public void setNcContext(NcContext ncContext) {
    synchronized (initLock) {
      this.ncContext = ncContext;
    }
  }

  /**
   * Get NcLocationManager instance, waiting for initialization if necessary. This method is
   * thread-safe and will block until initialization is complete.
   */
  public NcLocationManager getLocationManager() {
    ensureInitialized();
    return ncLocationManager;
  }

  /**
   * Get NcEventCenter instance, waiting for initialization if necessary. This method is thread-safe
   * and will block until initialization is complete.
   */
  public NcEventCenter getEventCenter() {
    ensureInitialized();
    return eventCenter;
  }

  /**
   * Get NotificationCenter instance, waiting for initialization if necessary. This method is
   * thread-safe and will block until initialization is complete.
   */
  public NotificationCenter getNotificationCenter() {
    ensureInitialized();
    return notificationCenter;
  }

  @Override
  public void onCreate() {
    super.onCreate();

    // if (LeakCanary.isInAnalyzerProcess(this)) {
    //   // This process is dedicated to LeakCanary for heap analysis.
    //   // You should not init your app in this process.
    //   return;
    // }
    // LeakCanary.install(this);

    Log.i("NexusChat", "++++++++++++++++++ ApplicationContext.onCreate() ++++++++++++++++++");

    System.loadLibrary("native-utils");

    // Initialize NcAccounts in background to avoid ANR during SQL migrations
    Util.runOnBackground(
        () -> {
          synchronized (initLock) {
            try {
              NcEventChannel eventChannel = new NcEventChannel();
              NcEventEmitter emitter = eventChannel.getEventEmitter();
              eventCenter = new NcEventCenter(this);

              new Thread(
                      () -> {
                        Log.i(TAG, "Starting event loop");
                        while (true) {
                          NcEvent event = emitter.getNextEvent();
                          if (event == null) {
                            break;
                          }
                          if (isInitialized) {
                            eventCenter.handleEvent(event);
                          } else {
                            // not fully initialized, only handle logging events,
                            // ex. account migrations during NcAccounts initialization
                            eventCenter.handleLogging(event);
                          }
                        }
                        Log.i("NexusChat", "shutting down event handler");
                      },
                      "eventThread")
                  .start();

              ncAccounts =
                  new NcAccounts(
                      new File(getFilesDir(), "accounts").getAbsolutePath(), eventChannel);
              Log.i(TAG, "NcAccounts created");
              rpc = new Rpc(new FFITransport(ncAccounts.getJsonrpcInstance()));
              Log.i(TAG, "Rpc created");
              AccountManager.getInstance().migrateToNcAccounts(this);

              int[] allAccounts = ncAccounts.getAll();
              Log.i(TAG, "Number of profiles: " + allAccounts.length);
              for (int accountId : allAccounts) {
                NcContext ac = ncAccounts.getAccount(accountId);
                if (!ac.isOpen()) {
                  try {
                    DatabaseSecret secret =
                        DatabaseSecretProvider.getOrCreateDatabaseSecret(this, accountId);
                    boolean res = ac.open(secret.asString());
                    if (res)
                      Log.i(
                          TAG,
                          "Successfully opened account "
                              + accountId
                              + ", path: "
                              + ac.getBlobdir());
                    else
                      Log.e(
                          TAG, "Error opening account " + accountId + ", path: " + ac.getBlobdir());
                  } catch (Exception e) {
                    Log.e(
                        TAG,
                        "Failed to open account "
                            + accountId
                            + ", path: "
                            + ac.getBlobdir()
                            + ": "
                            + e);
                    e.printStackTrace();
                  }
                }

                // 2025-12-16: The setting was removed.
                // Revert it to the default if it was changed in the past.
                ac.setConfigInt("webxnc_realtime_enabled", 1);

                // 2025-11-12: this is needed until core starts ignoring "delete_server_after" for
                // chatmail
                if (ac.isChatmail()) {
                  ac.setConfig("delete_server_after", null); // reset
                }
              }
              if (allAccounts.length == 0) {
                try {
                  rpc.addAccount();
                } catch (RpcException e) {
                  e.printStackTrace();
                }
              }
              ncContext = ncAccounts.getSelectedAccount();
              notificationCenter = new NotificationCenter(this);
              ncLocationManager = new NcLocationManager(this, ncContext);

              isInitialized = true;
              initLock.notifyAll();
              Log.i(TAG, "NcAccounts initialization complete");

              // set translations before starting I/O to avoid sending untranslated MDNs (issue
              // #2288)
              NcHelper.setStockTranslations(this);

              ncAccounts.startIo();
            } catch (Exception e) {
              Log.e(TAG, "Fatal error during NcAccounts initialization", e);
              // Mark as initialized even on error to avoid deadlock
              isInitialized = true;
              initLock.notifyAll();
              throw new RuntimeException("Failed to initialize NcAccounts", e);
            }
          }
        });

    // October-2025 migration: delete deprecated "permanent channel" id
    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
    notificationManager.deleteNotificationChannel("nc_foreground_notification_ch");
    // end October-2025 migration

    new ForegroundDetector(ApplicationContext.getInstance(this));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      ConnectivityManager connectivityManager =
          (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
      connectivityManager.registerDefaultNetworkCallback(
          new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull android.net.Network network) {
              Log.i(
                  "NexusChat",
                  "++++++++++++++++++ NetworkCallback.onAvailable() #" + debugOnAvailableCount++);
              getNcAccounts().maybeNetwork();
            }

            @Override
            public void onBlockedStatusChanged(
                @NonNull android.net.Network network, boolean blocked) {
              Log.i(
                  "NexusChat",
                  "++++++++++++++++++ NetworkCallback.onBlockedStatusChanged() #"
                      + debugOnBlockedStatusChangedCount++);
            }

            @Override
            public void onCapabilitiesChanged(
                @NonNull android.net.Network network, NetworkCapabilities networkCapabilities) {
              // usually called after onAvailable(), so a maybeNetwork seems contraproductive
              Log.i(
                  "NexusChat",
                  "++++++++++++++++++ NetworkCallback.onCapabilitiesChanged() #"
                      + debugOnCapabilitiesChangedCount++);
            }

            @Override
            public void onLinkPropertiesChanged(
                @NonNull android.net.Network network, LinkProperties linkProperties) {
              Log.i(
                  "NexusChat",
                  "++++++++++++++++++ NetworkCallback.onLinkPropertiesChanged() #"
                      + debugOnLinkPropertiesChangedCount++);
            }
          });
    } // no else: use old method for debugging
    BroancastReceiver networkStateReceiver = new NetworkStateReceiver();
    registerReceiver(
        networkStateReceiver,
        new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION));

    KeepAliveService.maybeStartSelf(this);

    initializeLogging();
    initializeJobManager();
    InChatSounds.getInstance(this);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      EglUtils.getEglBase();
      CallCoordinator.getInstance(this);
    }

    DynamicTheme.setDefaultDayNightMode(this);

    IntentFilter filter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
    registerReceiver(
        new BroancastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            Util.localeChanged();
            NcHelper.setStockTranslations(context);
          }
        },
        filter);

    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

    if (Prefs.isPushEnabled(this)) {
      FcmReceiveService.register(this);
    } else {
      Log.i(TAG, "FCM disabled at build time");
      // MAYBE TODO: i think the ApplicationContext is also created
      // when the app is stated by FetchWorker timeouts.
      // in this case, the normal threads shall not be started.
      Constraints constraints =
          new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
      PeriodicWorkRequest fetchWorkRequest =
          new PeriodicWorkRequest.Builder(
                  FetchWorker.class,
                  PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, // usually 15 minutes
                  TimeUnit.MILLISECONDS,
                  PeriodicWorkRequest
                      .MIN_PERIODIC_FLEX_MILLIS, // the start may be preferred by up to 5 minutes,
                  // so we run every 10-15 minutes
                  TimeUnit.MILLISECONDS)
              .setConstraints(constraints)
              .build();
      WorkManager.getInstance(this)
          .enqueueUniquePeriodicWork(
              "FetchWorker", ExistingPeriodicWorkPolicy.KEEP, fetchWorkRequest);
    }

    PeriodicWorkRequest webxncGarbageCollectionRequest =
        new PeriodicWorkRequest.Builder(
                WebxncGarbageCollectionWorker.class,
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS,
                PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS,
                TimeUnit.MILLISECONDS)
            .build();
    WorkManager.getInstance(this)
        .enqueueUniquePeriodicWork(
            "WebxncGarbageCollectionWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            webxncGarbageCollectionRequest);

    Log.i("NexusChat", "+++++++++++ ApplicationContext.onCreate() finished ++++++++++");
  }

  @Override
  public void onTerminate() {
    super.onTerminate();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      EglUtils.release();
    }
  }

  public JobManager getJobManager() {
    return jobManager;
  }

  private void initializeLogging() {
    SignalProtocolLoggerProvider.setProvider(new AndroidSignalProtocolLogger());
  }

  private void initializeJobManager() {
    this.jobManager = new JobManager(this, 5);
  }
}

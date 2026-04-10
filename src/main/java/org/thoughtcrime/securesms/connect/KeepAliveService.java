package org.thoughtcrime.securesms.connect;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.notifications.NotificationCenter;
import org.thoughtcrime.securesms.util.IntentUtils;
import org.thoughtcrime.securesms.util.Prefs;

public class KeepAliveService extends Service {

  private static final String TAG = KeepAliveService.class.getSimpleName();

  static KeepAliveService s_this = null;

  public static void maybeStartSelf(Context context) {
    // note, that unfortunately, the check for isIgnoringBatteryOptimizations() is not sufficient,
    // this checks only stock-android settings, several os have additional "optimizers" that ignore
    // this setting.
    // therefore, the most reliable way to not get killed is a permanent-foreground-notification.
    if (Prefs.reliableService(context)) {
      startSelf(context);
    }
  }

  public static void startSelf(Context context) {
    try {
      ContextCompat.startForegroundService(context, new Intent(context, KeepAliveService.class));
    } catch (Exception e) {
      Log.i(TAG, "Error calling ContextCompat.startForegroundService()", e);
    }
  }

  @Override
  public void onCreate() {
    Log.i("DeltaChat", "*** KeepAliveService.onCreate()");
    // there's nothing more to do here as all initialisation stuff is already done in
    // ApplicationLoader.onCreate() which is called before this broadcast is sended.
    s_this = this;

    // set self as foreground con notificación invisible
    try {
      stopForeground(true);
      startForeground(NotificationCenter.ID_PERMANENT, createInvisibleNotification());
    } catch (Exception e) {
      Log.i(TAG, "Error in onCreate()", e);
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    // START_STICKY ensured, the service is recreated as soon it is terminated for any reasons.
    // as ApplicationLoader.onCreate() is called before a service starts, there is no more to do
    // here,
    // the app is just running fine.
    Log.i("DeltaChat", "*** KeepAliveService.onStartCommand()");
    return START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onDestroy() {
    Log.i("DeltaChat", "*** KeepAliveService.onDestroy()");
    // the service will be restarted due to START_STICKY automatically, there's nothing more to do.
  }

  @Override
  public void onTimeout(int startId, int fgsType) {
    stopSelf();
  }

  public static KeepAliveService getInstance() {
    return s_this; // may be null
  }

  /**
   * CAMBIO: Notificación INVISIBLE pero el servicio sigue funcionando
   * La notificación es necesaria para el foreground service,
   * pero la hacemos prácticamente invisible para el usuario
   */
  private Notification createInvisibleNotification() {
    Intent intent = new Intent(this, ConversationListActivity.class);
    PendingIntent contentIntent =
        PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | IntentUtils.FLAG_MUTABLE());
    
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

    // CAMBIO: Contenido mínimo y poco visible
    builder.setContentTitle("Nexus Chat");
    builder.setContentText("Conectado");
    
    // Prioridad mínima (casi invisible)
    builder.setPriority(NotificationCompat.PRIORITY_MIN);
    
    // Sin timestamp
    builder.setWhen(0);
    
    // No mostrar en pantalla de bloqueo
    builder.setVisibility(NotificationCompat.VISIBILITY_SECRET);
    
    // Sin sonido ni vibración
    builder.setSilent(true);
    
    // 🔧 CORREGIDO: Sin badge - comentado para evitar error de compilación
    // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    //     builder.setShowBadge(false);
    // }
    
    // Ícono pequeño (obligatorio)
    builder.setSmallIcon(R.drawable.notification_permanent);
    
    // Color de acento transparente (menos visible)
    builder.setColor(ContextCompat.getColor(this, android.R.color.transparent));
    
    // Quitar la expansión de la notificación
    builder.setStyle(new NotificationCompat.DecoratedCustomViewStyle());
    
    // Configurar el canal de notificación (importancia mínima)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      createFgNotificationChannel(this);
      builder.setChannelId(NotificationCenter.CH_PERMANENT);
    }
    
    return builder.build();
  }

  private static boolean ch_created = false;

  @TargetApi(Build.VERSION_CODES.O)
  private static void createFgNotificationChannel(Context context) {
    if (!ch_created) {
      ch_created = true;
      
      // CAMBIO IMPORTANTE: Usar IMPORTANCE_MIN para que la notificación sea prácticamente invisible
      // El usuario NO verá la notificación en la barra de estado
      NotificationChannel channel =
          new NotificationChannel(
              NotificationCenter.CH_PERMANENT,
              "Servicio Nexus Chat",
              NotificationManager.IMPORTANCE_MIN);
      
      // Configurar el canal para que no moleste
      channel.setDescription("Mantiene Nexus Chat conectado en segundo plano.");
      channel.setShowBadge(false);
      channel.setSound(null, null);           // Sin sonido
      channel.enableVibration(false);         // Sin vibración
      channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET); // No mostrar en bloqueo
      
      NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
      notificationManager.createNotificationChannel(channel);
    }
  }
}
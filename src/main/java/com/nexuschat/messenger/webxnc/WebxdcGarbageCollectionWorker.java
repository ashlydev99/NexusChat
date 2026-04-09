package com.nexuschat.messenger.webxnc;

import android.content.Context;
import android.util.Log;
import android.webkit.WebStorage;
import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import chat.delta.rpc.Rpc;
import chat.delta.rpc.RpcException;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.nexuschat.messenger.connect.NcHelper;

public class WebxncGarbageCollectionWorker extends ListenableWorker {
  private static final String TAG = WebxncGarbageCollectionWorker.class.getSimpleName();
  private Context context;

  public WebxncGarbageCollectionWorker(Context context, WorkerParameters params) {
    super(context, params);
    this.context = context;
  }

  @Override
  public @NonNull ListenableFuture<Result> startWork() {
    Log.i(TAG, "Running Webxnc storage garbage collection...");

    final Pattern WEBXNC_URL_PATTERN =
        Pattern.compile("^https?://acc(\\d+)-msg(\\d+)\\.localhost/?");

    return CallbackToFutureAdapter.getFuture(
        completer -> {
          WebStorage webStorage = WebStorage.getInstance();

          webStorage.getOrigins(
              (origins) -> {
                if (origins == null || origins.isEmpty()) {
                  Log.i(TAG, "Done, no WebView origins found.");
                  completer.set(Result.success());
                  return;
                }

                Rpc rpc = NcHelper.getRpc(context);
                if (rpc == null) {
                  Log.e(
                      TAG,
                      "Failed to get access to RPC, Webxnc storage garbage collection aborted.");
                  completer.set(Result.failure());
                  return;
                }

                for (Object key : origins.keySet()) {
                  String url = (String) key;
                  Matcher m = WEBXNC_URL_PATTERN.matcher(url);
                  if (m.matches()) {
                    int accId = Integer.parseInt(m.group(1));
                    int msgId = Integer.parseInt(m.group(2));
                    try {
                      if (rpc.getExistingMsgIds(accId, Collections.singletonList(msgId))
                          .isEmpty()) {
                        webStorage.deleteOrigin(url);
                        Log.i(TAG, String.format("Deleted webxnc origin: %s", url));
                      } else {
                        Log.i(TAG, String.format("Existing webxnc origin: %s", url));
                      }
                    } catch (RpcException e) {
                      Log.e(TAG, "error calling rpc.getExistingMsgIds()", e);
                      completer.set(Result.failure());
                      return;
                    }
                  } else { // old webxnc URL schemes, etc
                    webStorage.deleteOrigin(url);
                    Log.i(TAG, String.format("Deleted unknown origin: %s", url));
                  }
                }

                Log.i(TAG, "Done running Webxnc storage garbage collection.");
                completer.set(Result.success());
              });

          return "Webxnc Garbage Collector";
        });
  }
}

package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import chat.delta.rpc.Rpc;
import chat.delta.rpc.RpcException;
import com.b44t.messenger.NcChat;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcEvent;
import com.b44t.messenger.NcMsg;
import com.google.common.base.Charsets;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;
import org.thoughtcrime.securesms.connect.AccountManager;
import org.thoughtcrime.securesms.connect.NcEventCenter;
import org.thoughtcrime.securesms.connect.NcHelper;
import org.thoughtcrime.securesms.util.IntentUtils;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;

public class WebxncActivity extends WebViewActivity implements NcEventCenter.NcEventDelegate {
  private static final String TAG = WebxncActivity.class.getSimpleName();
  private static final String EXTRA_ACCOUNT_ID = "accountId";
  private static final String EXTRA_APP_MSG_ID = "appMessageId";
  private static final String EXTRA_HIDE_ACTION_BAR = "hideActionBar";
  private static final String EXTRA_HREF = "href";
  private static final int REQUEST_CODE_FILE_PICKER = 51426;
  private static long lastOpenTime = 0;

  private ValueCallback<Uri[]> filePathCallback;
  private NcContext ncContext;
  private Rpc rpc;
  private NcMsg ncAppMsg;
  private String baseURL;
  private String sourceCodeUrl = "";
  private String selfAddr;
  private int sendUpdateMaxSize;
  private int sendUpdateInterval;
  private boolean internetAccess = false;
  private boolean hideActionBar = false;

  private TextToSpeech tts;

  public static void openMaps(Context context, int chatId) {
    NcContext ncContext = NcHelper.getContext(context);
    int msgId = ncContext.initWebxncIntegration(chatId);
    if (msgId == 0) {
      try {
        InputStream inputStream = context.getResources().getAssets().open("webxnc/maps.xnc");
        String outputFile = NcHelper.getBlobdirFile(ncContext, "maps", ".xnc");
        Util.copy(inputStream, new FileOutputStream(outputFile));
        ncContext.setWebxncIntegration(outputFile);
        msgId = ncContext.initWebxncIntegration(chatId);
      } catch (IOException e) {
        e.printStackTrace();
      }
      if (msgId == 0) {
        Toast.makeText(context, "Cannot get maps.xnc, see log for details.", Toast.LENGTH_LONG)
            .show();
        return;
      }
    }
    openWebxncActivity(context, msgId, true, "");
  }

  public static void openWebxncActivity(Context context, NcMsg instance) {
    openWebxncActivity(context, instance, "");
  }

  public static void openWebxncActivity(Context context, @NonNull NcMsg instance, String href) {
    openWebxncActivity(context, instance.getId(), false, href);
  }

  public static void openWebxncActivity(
      Context context, int msgId, boolean hideActionBar, String href) {
    if (!Util.isClickedRecently()) {
      context.startActivity(getWebxncIntent(context, msgId, hideActionBar, href));
    }
  }

  private static Intent getWebxncIntent(
      Context context, int msgId, boolean hideActionBar, String href) {
    NcContext ncContext = NcHelper.getContext(context);
    Intent intent = new Intent(context, WebxncActivity.class);
    intent.setAction(Intent.ACTION_VIEW);
    intent.putExtra(EXTRA_ACCOUNT_ID, ncContext.getAccountId());
    intent.putExtra(EXTRA_APP_MSG_ID, msgId);
    intent.putExtra(EXTRA_HIDE_ACTION_BAR, hideActionBar);
    intent.putExtra(EXTRA_HREF, href);
    return intent;
  }

  private static Intent[] getWebxncIntentWithParentStack(Context context, int msgId) {
    NcContext ncContext = NcHelper.getContext(context);

    final Intent chatIntent =
        new Intent(context, ConversationActivity.class)
            .putExtra(ConversationActivity.CHAT_ID_EXTRA, ncContext.getMsg(msgId).getChatId())
            .setAction(Intent.ACTION_VIEW);

    final Intent webxncIntent = getWebxncIntent(context, msgId, false, "");

    return TaskStackBuilder.create(context)
        .addNextIntentWithParentStack(chatIntent)
        .addNextIntent(webxncIntent)
        .getIntents();
  }

  @Override
  protected boolean immersiveMode() {
    return hideActionBar;
  }

  @Override
  protected void onCreate(Bundle state, boolean ready) {
    Bundle b = getIntent().getExtras();
    hideActionBar = b.getBoolean(EXTRA_HIDE_ACTION_BAR, false);

    super.onCreate(state, ready);
    rpc = NcHelper.getRpc(this);
    initTTS();

    // enter fullscreen mode if necessary,
    // this is needed here because if the app is opened while already in landscape mode,
    // onConfigurationChanged() is not triggered
    setScreenMode(getResources().getConfiguration());

    webView.setWebChromeClient(
        new WebChromeClient() {
          @Override
          @RequiresApi(21)
          public boolean onShowFileChooser(
              WebView webView,
              ValueCallback<Uri[]> filePathCallback,
              WebChromeClient.FileChooserParams fileChooserParams) {
            if (WebxncActivity.this.filePathCallback != null) {
              WebxncActivity.this.filePathCallback.onReceiveValue(null);
            }
            WebxncActivity.this.filePathCallback = filePathCallback;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(
                Intent.EXTRA_ALLOW_MULTIPLE,
                fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
            WebxncActivity.this.startActivityForResult(
                Intent.createChooser(intent, getString(R.string.select)), REQUEST_CODE_FILE_PICKER);
            return true;
          }
        });

    NcEventCenter eventCenter =
        NcHelper.getEventCenter(WebxncActivity.this.getApplicationContext());
    eventCenter.addObserver(NcContext.NC_EVENT_WEBXNC_STATUS_UPDATE, this);
    eventCenter.addObserver(NcContext.NC_EVENT_MSGS_CHANGED, this);
    eventCenter.addObserver(NcContext.NC_EVENT_WEBXNC_REALTIME_DATA, this);

    int appMessageId = b.getInt(EXTRA_APP_MSG_ID);
    int accountId = b.getInt(EXTRA_ACCOUNT_ID);
    this.ncContext = NcHelper.getContext(getApplicationContext());
    if (accountId != ncContext.getAccountId()) {
      AccountManager.getInstance().switchAccount(getApplicationContext(), accountId);
      this.ncContext = NcHelper.getContext(getApplicationContext());
    }

    this.ncAppMsg = this.ncContext.getMsg(appMessageId);
    if (!this.ncAppMsg.isOk()) {
      Toast.makeText(this, "Webxnc does no longer exist.", Toast.LENGTH_LONG).show();
      finish();
      return;
    }

    // `msg_id` in the subdomain makes sure, different apps using same files do not share the same
    // cache entry
    // (WebView may use a global cache shared across objects).
    // (a random-id would also work, but would need maintenance and does not add benefits as we
    // regard the file-part interceptRequest() only,
    // also a random-id is not that useful for debugging)
    this.baseURL = "https://acc" + ncContext.getAccountId() + "-msg" + appMessageId + ".localhost";

    final JSONObject info = this.ncAppMsg.getWebxncInfo();
    internetAccess = JsonUtils.optBoolean(info, "internet_access");
    selfAddr = info.optString("self_addr");
    sendUpdateMaxSize = info.optInt("send_update_max_size");
    sendUpdateInterval = info.optInt("send_update_interval");

    toggleFakeProxy(!internetAccess);

    WebSettings webSettings = webView.getSettings();
    webSettings.setJavaScriptEnabled(true);
    webSettings.setAllowFileAccess(false);
    webSettings.setBlockNetworkLoads(!internetAccess);
    webSettings.setAllowContentAccess(false);
    webSettings.setGeolocationEnabled(false);
    webSettings.setAllowFileAccessFromFileURLs(false);
    webSettings.setAllowUniversalAccessFromFileURLs(false);
    webSettings.setDatabaseEnabled(true);
    webSettings.setDomStorageEnabled(true);
    webView.setNetworkAvailable(
        internetAccess); // this does not block network but sets `window.navigator.isOnline` in js
    // land
    webView.addJavascriptInterface(new InternalJSApi(), "InternalJSApi");

    String extraHref = b.getString(EXTRA_HREF, "");
    if (TextUtils.isEmpty(extraHref)) {
      extraHref = "index.html";
    }

    String href = baseURL + "/" + extraHref;
    String encodedHref = "";
    try {
      encodedHref = URLEncoder.encode(href, Charsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }

    long timeDelta = System.currentTimeMillis() - lastOpenTime;
    final String url =
        this.baseURL
            + "/webxnc_bootstrap324567869.html?i="
            + (internetAccess ? "1" : "0")
            + "&href="
            + encodedHref;
    Util.runOnAnyBackgroundThread(
        () -> {
          if (timeDelta < 2000) {
            // this is to avoid getting stuck in the FILL500 in some devices if the
            // previous webview was not destroyed yet and a new app is opened too soon
            Util.sleep(1000);
          }
          Util.runOnMain(() -> webView.loadUrl(url));
        });

    Util.runOnAnyBackgroundThread(
        () -> {
          final NcChat chat = ncContext.getChat(ncAppMsg.getChatId());
          Util.runOnMain(
              () -> {
                updateTitleAndMenu(info, chat);
              });
        });
  }

  @Override
  public void onResume() {
    super.onResume();
    NcHelper.getNotificationCenter(this)
        .updateVisibleWebxnc(ncContext.getAccountId(), ncAppMsg.getId());
  }

  @Override
  protected void onPause() {
    super.onPause();
    NcHelper.getNotificationCenter(this).clearVisibleWebxnc();
  }

  @Override
  protected void onDestroy() {
    lastOpenTime = System.currentTimeMillis();
    NcHelper.getEventCenter(this.getApplicationContext()).removeObservers(this);
    leaveRealtimeChannel();
    tts.shutdown();
    super.onDestroy();
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    // do not call super.onPrepareOptionsMenu() as the default "Search" menu is not needed
    menu.clear();
    this.getMenuInflater().inflate(R.menu.webxnc, menu);
    menu.findItem(R.id.source_code).setVisible(!sourceCodeUrl.isEmpty());
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    int itemId = item.getItemId();
    if (itemId == R.id.menu_add_to_home_screen) {
      addToHomeScreen(this, ncAppMsg.getId());
      return true;
    } else if (itemId == R.id.webxnc_help) {
      NcHelper.openHelp(this, "#webxnc");
    } else if (itemId == R.id.source_code) {
      IntentUtils.showInBrowser(this, sourceCodeUrl);
      return true;
    } else if (itemId == R.id.show_in_chat) {
      showInChat();
      return true;
    }
    return false;
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    Log.i(TAG, "onConfigurationChanged(" + newConfig.orientation + ")");
    super.onConfigurationChanged(newConfig);
    // orientation might have changed, enter/exit fullscreen mode if needed
    setScreenMode(newConfig);
  }

  private void initTTS() {
    tts =
        new TextToSpeech(
            this,
            new TextToSpeech.OnInitListener() {
              @Override
              public void onInit(int status) {
                Log.i(TAG, "TTS Init Status: " + status);
              }
            });
  }

  private void setScreenMode(Configuration config) {
    // enter/exit fullscreen mode depending on orientation (landscape/portrait),
    // on tablets there is enough height so fullscreen mode is never enabled there
    boolean enable =
        config.orientation == Configuration.ORIENTATION_LANDSCAPE
            && !getResources().getBoolean(R.bool.isBigScreen);
    getWindow().getDecorView().setSystemUiVisibility(enable ? View.SYSTEM_UI_FLAG_FULLSCREEN : 0);
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      if (hideActionBar || enable) {
        actionBar.hide();
      } else {
        actionBar.show();
      }
    }
  }

  @Override
  protected boolean shouldAskToOpenLink() {
    return true;
  }

  // This is usually only called when internetAccess == true or for mailto/openpgp4fpr scheme,
  // because when internetAccess == false, the page is loaded inside an iframe,
  // and WebViewClient.shouldOverrideUrlLoading is not called for HTTP(S) links inside the iframe
  // unless target=_blank is used
  @Override
  protected boolean openOnlineUrl(String url) {
    Log.i(TAG, "openOnlineUrl: " + url);

    // if there is internet access, allow internal loading of http
    if (internetAccess && url.startsWith("http")) {
      // returning `false` continues loading in WebView; returning `true` let WebView abort loading
      return false;
    }

    return super.openOnlineUrl(url);
  }

  @Override
  protected WebResourceResponse interceptRequest(String rawUrl) {
    Log.i(TAG, "interceptRequest: " + rawUrl);
    WebResourceResponse res = null;
    try {
      if (rawUrl == null) {
        throw new Exception("no url specified");
      }
      String path = Uri.parse(rawUrl).getPath();
      if (path.equalsIgnoreCase("/webxnc.js")) {
        InputStream targetStream = getResources().openRawResource(R.raw.webxnc);
        res = new WebResourceResponse("text/javascript", "UTF-8", targetStream);
      } else if (path.equalsIgnoreCase("/webxnc_bootstrap324567869.html")) {
        InputStream targetStream = getResources().openRawResource(R.raw.webxnc_wrapper);
        res = new WebResourceResponse("text/html", "UTF-8", targetStream);
      } else if (path.equalsIgnoreCase(
          "/sandboxed_iframe_rtcpeerconnection_check_5965668501706.html")) {
        InputStream targetStream =
            getResources().openRawResource(R.raw.sandboxed_iframe_rtcpeerconnection_check);
        res = new WebResourceResponse("text/html", "UTF-8", targetStream);
      } else {
        byte[] blob = this.ncAppMsg.getWebxncBlob(path);
        if (blob == null) {
          if (internetAccess) {
            return null; // do not intercept request
          }
          throw new Exception("\"" + path + "\" not found");
        }
        String ext = MediaUtil.getFileExtensionFromUrl(path);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (mimeType == null) {
          switch (ext) {
            case "js":
              mimeType = "text/javascript";
              break;
            case "wasm":
              mimeType = "application/wasm";
              break;
            default:
              mimeType = "application/octet-stream";
              Log.i(TAG, "unknown mime type for " + rawUrl);
              break;
          }
        }
        String encoding = mimeType.startsWith("text/") ? "UTF-8" : null;
        InputStream targetStream = new ByteArrayInputStream(blob);
        res = new WebResourceResponse(mimeType, encoding, targetStream);
      }
    } catch (Exception e) {
      e.printStackTrace();
      InputStream targetStream =
          new ByteArrayInputStream(("Webxnc Request Error: " + e.getMessage()).getBytes());
      res = new WebResourceResponse("text/plain", "UTF-8", targetStream);
    }

    if (!internetAccess) {
      Map<String, String> headers = new HashMap<>();
      headers.put(
          "Content-Security-Policy",
          "default-src 'self'; "
              + "style-src 'self' 'unsafe-inline' blob: ; "
              + "font-src 'self' data: blob: ; "
              + "script-src 'self' 'unsafe-inline' 'unsafe-eval' blob: ; "
              + "connect-src 'self' data: blob: ; "
              + "img-src 'self' data: blob: ; "
              + "media-src 'self' data: blob: ;"
              + "webrtc 'block' ; ");
      headers.put("X-DNS-Prefetch-Control", "off");
      res.setResponseHeaders(headers);
    }
    return res;
  }

  private void callJavaScriptFunction(String func) {
    webView.evaluateJavascript(
        "document.getElementById('frame').contentWindow." + func + ";", null);
  }

  @Override
  public void handleEvent(@NonNull NcEvent event) {
    int eventId = event.getId();
    if ((eventId == NcContext.NC_EVENT_WEBXNC_STATUS_UPDATE
        && event.getData1Int() == ncAppMsg.getId())) {
      Log.i(TAG, "handling status update event");
      callJavaScriptFunction("__webxncUpdate()");
    } else if ((eventId == NcContext.NC_EVENT_WEBXNC_REALTIME_DATA
        && event.getData1Int() == ncAppMsg.getId())) {
      Log.i(TAG, "handling realtime data event");
      StringBuilder data = new StringBuilder();
      for (byte b : event.getData2Blob()) {
        data.append(((int) b) + ",");
      }
      callJavaScriptFunction("__webxncRealtimeData([" + data + "])");
    } else if ((eventId == NcContext.NC_EVENT_MSGS_CHANGED
        && event.getData2Int() == ncAppMsg.getId())) {
      this.ncAppMsg =
          this.ncContext.getMsg(event.getData2Int()); // msg changed, reload data from db
      Util.runOnAnyBackgroundThread(
          () -> {
            final JSONObject info = ncAppMsg.getWebxncInfo();
            final NcChat chat = ncContext.getChat(ncAppMsg.getChatId());
            Util.runOnMain(
                () -> {
                  updateTitleAndMenu(info, chat);
                });
          });
    }
  }

  private void updateTitleAndMenu(JSONObject info, NcChat chat) {
    final String docName = JsonUtils.optString(info, "document");
    final String xncName = JsonUtils.optString(info, "name");
    final String currSourceCodeUrl = JsonUtils.optString(info, "source_code_url");
    getSupportActionBar()
        .setTitle((docName.isEmpty() ? xncName : docName) + " – " + chat.getName());
    if (!sourceCodeUrl.equals(currSourceCodeUrl)) {
      sourceCodeUrl = currSourceCodeUrl;
      invalidateOptionsMenu();
    }
  }

  private void showInChat() {
    Intent intent = new Intent(this, ConversationActivity.class);
    intent.putExtra(ConversationActivity.CHAT_ID_EXTRA, ncAppMsg.getChatId());
    intent.putExtra(
        ConversationActivity.STARTING_POSITION_EXTRA,
        NcMsg.getMessagePosition(ncAppMsg, ncContext));
    startActivity(intent);
  }

  public static void addToHomeScreen(Activity activity, int msgId) {
    Context context = activity.getApplicationContext();
    try {
      NcContext ncContext = NcHelper.getContext(context);
      NcMsg msg = ncContext.getMsg(msgId);
      final JSONObject info = msg.getWebxncInfo();

      final String docName = JsonUtils.optString(info, "document");
      final String xncName = JsonUtils.optString(info, "name");
      byte[] blob = msg.getWebxncBlob(JsonUtils.optString(info, "icon"));
      ByteArrayInputStream is = new ByteArrayInputStream(blob);
      BitmapDrawable drawable = (BitmapDrawable) Drawable.createFromStream(is, "icon");
      Bitmap bitmap = drawable.getBitmap();

      ShortcutInfoCompat shortcutInfoCompat =
          new ShortcutInfoCompat.Builder(context, "xnc-" + ncContext.getAccountId() + "-" + msgId)
              .setShortLabel(docName.isEmpty() ? xncName : docName)
              .setIcon(
                  IconCompat.createWithBitmap(
                      bitmap)) // createWithAdaptiveBitmap() removes decorations but cuts out a too
              // small circle and defamiliarize the icon too much
              .setIntents(getWebxncIntentWithParentStack(context, msgId))
              .build();

      Toast.makeText(context, R.string.one_moment, Toast.LENGTH_SHORT).show();
      if (!ShortcutManagerCompat.requestPinShortcut(context, shortcutInfoCompat, null)) {
        Toast.makeText(
                context, "ErrAddToHomescreen: requestPinShortcut() failed", Toast.LENGTH_LONG)
            .show();
      }
    } catch (Exception e) {
      Toast.makeText(context, "ErrAddToHomescreen: " + e, Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public void onActivityResult(int reqCode, int resultCode, final Intent data) {
    if (reqCode == REQUEST_CODE_FILE_PICKER && filePathCallback != null) {
      Uri[] dataUris = null;
      if (resultCode == Activity.RESULT_OK && data != null) {
        try {
          if (data.getDataString() != null) {
            dataUris = new Uri[] {Uri.parse(data.getDataString())};
          } else if (data.getClipData() != null) {
            final int numSelectedFiles = data.getClipData().getItemCount();
            dataUris = new Uri[numSelectedFiles];
            for (int i = 0; i < numSelectedFiles; i++) {
              dataUris[i] = data.getClipData().getItemAt(i).getUri();
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      filePathCallback.onReceiveValue(dataUris);
      filePathCallback = null;
    }
    super.onActivityResult(reqCode, resultCode, data);
  }

  private void leaveRealtimeChannel() {
    int accountId = ncContext.getAccountId();
    int msgId = ncAppMsg.getId();
    try {
      rpc.leaveWebxncRealtime(accountId, msgId);
    } catch (RpcException e) {
      e.printStackTrace();
    }
  }

  class InternalJSApi {
    @JavascriptInterface
    public int sendUpdateMaxSize() {
      return WebxncActivity.this.sendUpdateMaxSize;
    }

    @JavascriptInterface
    public int sendUpdateInterval() {
      return WebxncActivity.this.sendUpdateInterval;
    }

    @JavascriptInterface
    public String selfAddr() {
      return WebxncActivity.this.selfAddr;
    }

    /**
     * @noinspection unused
     */
    @JavascriptInterface
    public String selfName() {
      return WebxncActivity.this.ncContext.getName();
    }

    /**
     * @noinspection unused
     */
    @JavascriptInterface
    public boolean sendStatusUpdate(String payload) {
      Log.i(TAG, "sendStatusUpdate");
      if (!WebxncActivity.this.ncContext.sendWebxncStatusUpdate(
          WebxncActivity.this.ncAppMsg.getId(), payload)) {
        NcChat ncChat =
            WebxncActivity.this.ncContext.getChat(WebxncActivity.this.ncAppMsg.getChatId());
        Toast.makeText(
                WebxncActivity.this,
                ncChat.isContactRequest()
                    ? WebxncActivity.this.getString(R.string.accept_request_first)
                    : WebxncActivity.this.ncContext.getLastError(),
                Toast.LENGTH_LONG)
            .show();
        return false;
      }
      return true;
    }

    /**
     * @noinspection unused
     */
    @JavascriptInterface
    public String getStatusUpdates(int lastKnownSerial) {
      Log.i(TAG, "getStatusUpdates");
      return WebxncActivity.this.ncContext.getWebxncStatusUpdates(
          WebxncActivity.this.ncAppMsg.getId(), lastKnownSerial);
    }

    /**
     * @noinspection unused
     */
    @JavascriptInterface
    public String sendToChat(String message) {
      Log.i(TAG, "sendToChat");
      try {
        JSONObject jsonObject = new JSONObject(message);

        String text = null;
        byte[] data = null;
        String name = null;
        if (jsonObject.has("base64")) {
          data = Base64.decode(jsonObject.getString("base64"), Base64.NO_WRAP | Base64.NO_PADDING);
          name = jsonObject.getString("name");
        }
        if (jsonObject.has("text")) {
          text = jsonObject.getString("text");
        }

        if (TextUtils.isEmpty(text) && TextUtils.isEmpty(name)) {
          return "provided file is invalid, you need to set both name and base64 content";
        }

        NcHelper.sendToChat(WebxncActivity.this, data, "application/octet-stream", name, text);
        return null;
      } catch (Exception e) {
        e.printStackTrace();
        return e.toString();
      }
    }

    /**
     * @noinspection unused
     */
    @JavascriptInterface
    public void sendRealtimeAdvertisement() {
      int accountId = WebxncActivity.this.ncContext.getAccountId();
      int msgId = WebxncActivity.this.ncAppMsg.getId();
      try {
        WebxncActivity.this.rpc.sendWebxncRealtimeAdvertisement(accountId, msgId);
      } catch (RpcException e) {
        e.printStackTrace();
      }
    }

    /**
     * @noinspection unused
     */
    @JavascriptInterface
    public void leaveRealtimeChannel() {
      WebxncActivity.this.leaveRealtimeChannel();
    }

    /**
     * @noinspection unused
     */
    @JavascriptInterface
    public void sendRealtimeData(String jsonData) {
      int accountId = WebxncActivity.this.ncContext.getAccountId();
      int msgId = WebxncActivity.this.ncAppMsg.getId();
      try {
        Integer[] data = JsonUtils.fromJson(jsonData, Integer[].class);
        WebxncActivity.this.rpc.sendWebxncRealtimeData(accountId, msgId, Arrays.asList(data));
      } catch (IOException | RpcException e) {
        e.printStackTrace();
      }
    }

    @JavascriptInterface
    public void ttsSpeak(String text, String lang) {
      if (lang != null && !lang.isEmpty()) tts.setLanguage(Locale.forLanguageTag(lang));
      tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }
  }
}

package com.nexuschat.messenger;

import android.os.Bundle;
import android.view.Menu;
import androidx.annotation.NonNull;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcEvent;
import com.nexuschat.messenger.connect.NcEventCenter;
import com.nexuschat.messenger.connect.NcHelper;

public class ConnectivityActivity extends WebViewActivity implements NcEventCenter.NcEventDelegate {
  @Override
  protected void onCreate(Bundle state, boolean ready) {
    super.onCreate(state, ready);
    setForceDark();
    getSupportActionBar().setTitle(R.string.connectivity);
    refresh();

    NcHelper.getEventCenter(this).addObserver(NcContext.NC_EVENT_CONNECTIVITY_CHANGED, this);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    NcHelper.getEventCenter(this).removeObservers(this);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    // do not call super.onPrepareOptionsMenu() as the default "Search" menu is not needed
    return true;
  }

  private void refresh() {
    final String connectivityHtml =
        NcHelper.getContext(this)
            .getConnectivityHtml()
            .replace("</style>", " html { color-scheme: dark light; }</style>");
    webView.loadDataWithBaseURL(null, connectivityHtml, "text/html", "utf-8", null);
  }

  @Override
  public void handleEvent(@NonNull NcEvent event) {
    refresh();
  }
}

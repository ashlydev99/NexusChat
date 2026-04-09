package com.nexuschat.messenger.proxy;

import static com.nexuschat.messenger.connect.NcHelper.CONFIG_PROXY_ENABLED;
import static com.nexuschat.messenger.connect.NcHelper.CONFIG_PROXY_URL;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcEvent;
import com.b44t.messenger.NcLot;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGImageView;
import com.caverock.androidsvg.SVGParseException;
import java.util.LinkedList;
import com.nexuschat.messenger.BaseActionBarActivity;
import com.nexuschat.messenger.R;
import com.nexuschat.messenger.connect.NcEventCenter;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.util.Util;
import com.nexuschat.messenger.util.ViewUtil;

public class ProxySettingsActivity extends BaseActionBarActivity
    implements ProxyListAdapter.ItemClickListener, NcEventCenter.NcEventDelegate {

  private SwitchCompat proxySwitch;
  private ProxyListAdapter adapter;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.proxy_settings_activity);

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setTitle(R.string.proxy_settings);
      actionBar.setDisplayHomeAsUpEnabled(true);
    }

    proxySwitch = findViewById(R.id.proxy_switch);
    ListView proxyList = findViewById(R.id.proxy_list);

    ViewUtil.applyWindowInsets(proxySwitch, true, false, true, false);
    ViewUtil.applyWindowInsets(proxyList, true, false, true, true);

    adapter = new ProxyListAdapter(this);
    adapter.setItemClickListener(this);

    proxySwitch.setChecked(NcHelper.getInt(this, CONFIG_PROXY_ENABLED) == 1);
    proxySwitch.setOnClickListener(
        l -> {
          if (proxySwitch.isChecked() && adapter.getCount() == 0) {
            showAddProxyDialog();
          } else {
            NcHelper.set(this, CONFIG_PROXY_ENABLED, proxySwitch.isChecked() ? "1" : "0");
            NcHelper.getContext(this).restartIo();
          }
        });

    proxyList.setAdapter(adapter);
    proxyList.addHeaderView(View.inflate(this, R.layout.proxy_list_header, null), null, false);
    View footer = View.inflate(this, R.layout.proxy_list_footer, null);
    footer.setOnClickListener(l -> showAddProxyDialog());
    proxyList.addFooterView(footer);
    adapter.changeData(NcHelper.get(this, CONFIG_PROXY_URL));
    NcHelper.getEventCenter(this).addObserver(NcContext.NC_EVENT_CONNECTIVITY_CHANGED, this);

    handleOpenProxyUrl();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    handleOpenProxyUrl();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    NcHelper.getEventCenter(this).removeObservers(this);
  }

  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == android.R.id.home) {
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onItemClick(String proxyUrl) {
    if (NcHelper.getContext(this).setConfigFromQr(proxyUrl)) {
      NcHelper.getContext(this).restartIo();
      adapter.setSelectedProxy(proxyUrl);
      proxySwitch.setChecked(NcHelper.getInt(this, CONFIG_PROXY_ENABLED) == 1);
    } else {
      Toast.makeText(this, R.string.proxy_invalid, Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public void onItemShare(String proxyUrl) {
    View view = View.inflate(this, R.layout.dialog_share_proxy, null);
    SVGImageView qrImage = view.findViewById(R.id.qr_image);
    try {
      SVG svg = SVG.getFromString(NcHelper.getContext(this).createQrSvg(proxyUrl));
      qrImage.setSVG(svg);
    } catch (SVGParseException e) {
      e.printStackTrace();
    }

    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(
                R.string.proxy_share_link,
                (dlg, btn) -> {
                  Intent intent = new Intent(Intent.ACTION_SEND);
                  intent.setType("text/plain");
                  intent.putExtra(Intent.EXTRA_TEXT, proxyUrl);
                  startActivity(
                      Intent.createChooser(intent, getString(R.string.chat_share_with_title)));
                })
            .show();
  }

  @Override
  public void onItemDelete(String proxyUrl) {
    String host = NcHelper.getContext(this).checkQr(proxyUrl).getText1();
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(R.string.proxy_delete)
            .setMessage(getString(R.string.proxy_delete_explain, host))
            .setPositiveButton(R.string.delete, (dlg, btn) -> deleteProxy(proxyUrl))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    Util.redPositiveButton(dialog);
  }

  private void deleteProxy(String proxyUrl) {
    final LinkedList<String> proxies = new LinkedList<>();
    for (String proxy : NcHelper.get(this, CONFIG_PROXY_URL).split("\n")) {
      if (!proxy.equals(proxyUrl)) {
        proxies.add(proxy);
      }
    }
    if (proxies.isEmpty()) {
      NcHelper.set(this, CONFIG_PROXY_ENABLED, "0");
      proxySwitch.setChecked(false);
    }
    String proxyUrls = String.join("\n", proxies);
    NcHelper.set(this, CONFIG_PROXY_URL, proxyUrls);
    NcHelper.getContext(this).restartIo();
    adapter.changeData(proxyUrls);
  }

  private void showAddProxyDialog() {
    View view = View.inflate(this, R.layout.single_line_input, null);
    EditText inputField = view.findViewById(R.id.input_field);
    inputField.setHint(R.string.proxy_add_url_hint);

    new AlertDialog.Builder(this)
        .setTitle(R.string.proxy_add)
        .setMessage(R.string.proxy_add_explain)
        .setView(view)
        .setPositiveButton(
            R.string.proxy_use_proxy,
            (dialog, whichButton) -> {
              String newProxy = inputField.getText().toString().trim();
              NcContext ncContext = NcHelper.getContext(this);
              final NcLot qrParsed = ncContext.checkQr(newProxy);
              if (qrParsed.getState() == NcContext.NC_QR_PROXY) {
                ncContext.setConfigFromQr(newProxy);
                NcHelper.getContext(this).restartIo();
                adapter.changeData(NcHelper.get(this, CONFIG_PROXY_URL));
              } else {
                Toast.makeText(this, R.string.proxy_invalid, Toast.LENGTH_LONG).show();
              }
              proxySwitch.setChecked(NcHelper.getInt(this, CONFIG_PROXY_ENABLED) == 1);
            })
        .setNegativeButton(
            android.R.string.cancel,
            (dialog, whichButton) -> {
              if (proxySwitch.isChecked() && adapter.getCount() == 0) {
                // user enabled switch without having proxies yet, revert
                proxySwitch.setChecked(false);
              }
            })
        .setCancelable(false)
        .show();
  }

  private void handleOpenProxyUrl() {
    if (getIntent() != null && Intent.ACTION_VIEW.equals(getIntent().getAction())) {
      Uri uri = getIntent().getData();
      if (uri == null) {
        return;
      }

      NcContext ncContext = NcHelper.getContext(this);
      final NcLot qrParsed = ncContext.checkQr(uri.toString());
      if (qrParsed.getState() == NcContext.NC_QR_PROXY) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.proxy_use_proxy)
            .setMessage(getString(R.string.proxy_use_proxy_confirm, qrParsed.getText1()))
            .setPositiveButton(
                R.string.proxy_use_proxy,
                (dlg, btn) -> {
                  ncContext.setConfigFromQr(uri.toString());
                  ncContext.restartIo();
                  adapter.changeData(NcHelper.get(this, CONFIG_PROXY_URL));
                  proxySwitch.setChecked(NcHelper.getInt(this, CONFIG_PROXY_ENABLED) == 1);
                })
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(false)
            .show();
      } else {
        Toast.makeText(this, R.string.proxy_invalid, Toast.LENGTH_LONG).show();
      }
    }
  }

  @Override
  public void handleEvent(@NonNull NcEvent event) {
    if (event.getId() == NcContext.NC_EVENT_CONNECTIVITY_CHANGED) {
      adapter.refreshConnectivity();
    }
  }
}

package com.nexuschat.messenger.relay;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import chat.delta.rpc.Rpc;
import chat.delta.rpc.RpcException;
import chat.delta.rpc.types.TransportListEntry;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcEvent;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import java.util.List;
import com.nexuschat.messenger.BaseActionBarActivity;
import com.nexuschat.messenger.R;
import com.nexuschat.messenger.components.registration.PulsingFloatingActionButton;
import com.nexuschat.messenger.connect.NcEventCenter;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.qr.QrActivity;
import com.nexuschat.messenger.qr.QrCodeHandler;
import com.nexuschat.messenger.util.ScreenLockUtil;
import com.nexuschat.messenger.util.Util;
import com.nexuschat.messenger.util.ViewUtil;

public class RelayListActivity extends BaseActionBarActivity
    implements RelayListAdapter.OnRelayClickListener, NcEventCenter.NcEventDelegate {

  private static final String TAG = RelayListActivity.class.getSimpleName();
  public static final String EXTRA_QR_DATA = "qr_data";

  private RelayListAdapter adapter;
  private Rpc rpc;
  private int accId;

  /**
   * QR provided via Intent extras needs to be saved to pass it to QrCodeHandler when authorization
   * finishes
   */
  private String qrData = null;

  private ActivityResultLauncher<Intent> screenLockLauncher;
  private ActivityResultLauncher<Intent> qrScannerLauncher;

  /** Relay selected for context menu via onRelayLongClick() */
  private TransportListEntry contextMenuRelay = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_relay_list);

    qrScannerLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == RESULT_OK) {
                IntentResult scanResult =
                    IntentIntegrator.parseActivityResult(result.getResultCode(), result.getData());
                new QrCodeHandler(this).handleOnlyAddRelayQr(scanResult.getContents(), null);
              }
            });
    screenLockLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() != RESULT_OK) {
                // if user canceled unlocking, then finish
                finish();
                return;
              }
              // user authorized, then proceed to handle the QR data
              if (qrData != null) {
                new QrCodeHandler(this).handleOnlyAddRelayQr(qrData, null);
                qrData = null;
              }
            });

    rpc = NcHelper.getRpc(this);
    accId = NcHelper.getContext(this).getAccountId();

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setTitle(R.string.transports);
      actionBar.setDisplayHomeAsUpEnabled(true);
    }

    RecyclerView recyclerView = findViewById(R.id.relay_list);
    PulsingFloatingActionButton fabAdd = findViewById(R.id.fab_add_relay);

    // add padding to avoid content hidden behind system bars
    ScrollView scrollView = findViewById(R.id.relay_scroll_view);
    ViewUtil.applyWindowInsets(scrollView);
    // Apply insets to prevent fab from being covered by system bars
    ViewUtil.applyWindowInsetsAsMargin(fabAdd);

    qrData = getIntent().getStringExtra(EXTRA_QR_DATA);
    if (qrData != null) {
      // when the activity is opened with a QR data, we need to ask for authorization first
      boolean result =
          ScreenLockUtil.applyScreenLock(
              this,
              getString(R.string.add_transport),
              getString(R.string.enter_system_secret_to_continue),
              screenLockLauncher);
      if (!result) {
        new QrCodeHandler(this).handleOnlyAddRelayQr(qrData, null);
      }
    }

    fabAdd.setOnClickListener(
        v -> {
          Intent intent =
              new IntentIntegrator(this)
                  .setCaptureActivity(QrActivity.class)
                  .addExtra(QrActivity.EXTRA_SCAN_RELAY, true)
                  .createScanIntent();
          qrScannerLauncher.launch(intent);
        });

    LinearLayoutManager layoutManager = new LinearLayoutManager(this);
    // Add the default divider (uses the theme’s `android.R.attr.listDivider`)
    DividerItemDecoration divider =
        new DividerItemDecoration(recyclerView.getContext(), layoutManager.getOrientation());
    recyclerView.addItemDecoration(divider);
    recyclerView.setLayoutManager(layoutManager);

    adapter = new RelayListAdapter(this);
    recyclerView.setAdapter(adapter);

    loadRelays();

    NcEventCenter eventCenter = NcHelper.getEventCenter(this);
    eventCenter.addObserver(NcContext.NC_EVENT_CONFIGURE_PROGRESS, this);
    eventCenter.addObserver(NcContext.NC_EVENT_TRANSPORTS_MODIFIED, this);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    NcHelper.getEventCenter(this).removeObservers(this);
  }

  private void loadRelays() {
    Util.runOnAnyBackgroundThread(
        () -> {
          String mainRelayAddr = "";
          try {
            mainRelayAddr = rpc.getConfig(accId, NcHelper.CONFIG_CONFIGURED_ADDRESS);
          } catch (RpcException e) {
            Log.e(TAG, "RPC.getConfig() failed", e);
          }
          String finalMainRelayAddr = mainRelayAddr;

          try {
            List<TransportListEntry> relays = rpc.listTransportsEx(accId);

            Util.runOnMain(() -> adapter.setRelays(relays, finalMainRelayAddr));
          } catch (RpcException e) {
            Log.e(TAG, "RPC.listTransports() failed", e);
            Util.runOnMain(() -> adapter.setRelays(null, finalMainRelayAddr));
          }
        });
  }

  @Override
  public void onRelayClick(TransportListEntry relay) {
    if (relay.param.addr != null && !relay.param.addr.equals(adapter.getMainRelay())) {
      Util.runOnAnyBackgroundThread(
          () -> {
            try {
              rpc.setConfig(accId, NcHelper.CONFIG_CONFIGURED_ADDRESS, relay.param.addr);
            } catch (RpcException e) {
              Log.e(TAG, "RPC.setConfig() failed", e);
            }

            loadRelays();
          });
    }
  }

  @Override
  public void onRelayLongClick(View view, TransportListEntry relay) {
    contextMenuRelay = relay;
    registerForContextMenu(view);
    openContextMenu(view);
    unregisterForContextMenu(view);
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    getMenuInflater().inflate(R.menu.relay_item_context, menu);

    boolean nonNullAddr = contextMenuRelay != null && contextMenuRelay.param.addr != null;
    boolean isMain = nonNullAddr && contextMenuRelay.param.addr.equals(adapter.getMainRelay());

    Util.redMenuItem(menu, R.id.menu_delete_relay);
    menu.findItem(R.id.menu_delete_relay).setVisible(!isMain);
  }

  @Override
  public void onContextMenuClosed(android.view.Menu menu) {
    super.onContextMenuClosed(menu);
    contextMenuRelay = null;
  }

  @Override
  public boolean onContextItemSelected(@NonNull MenuItem item) {
    if (contextMenuRelay == null) return super.onContextItemSelected(item);

    int itemId = item.getItemId();
    if (itemId == R.id.menu_edit_relay) {
      onRelayEdit(contextMenuRelay);
      contextMenuRelay = null;
      return true;
    } else if (itemId == R.id.menu_delete_relay) {
      onRelayDelete(contextMenuRelay);
      contextMenuRelay = null;
      return true;
    }

    return super.onContextItemSelected(item);
  }

  private void onRelayEdit(TransportListEntry relay) {
    Intent intent = new Intent(this, EditRelayActivity.class);
    intent.putExtra(EditRelayActivity.EXTRA_ADDR, relay.param.addr);
    startActivity(intent);
  }

  private void onRelayDelete(TransportListEntry relay) {
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(R.string.remove_transport)
            .setMessage(getString(R.string.confirm_remove_or_hide_transport_x, relay.param.addr))
            .setPositiveButton(
                R.string.remove_transport,
                (d, which) -> {
                  try {
                    rpc.deleteTransport(accId, relay.param.addr);
                    loadRelays();
                  } catch (RpcException e) {
                    Log.e(TAG, "RPC.deleteTransport() failed", e);
                  }
                })
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(
                R.string.hide_from_contacts,
                (d, which) -> {
                  try {
                    rpc.setTransportUnpublished(accId, relay.param.addr, true);
                    loadRelays();
                  } catch (RpcException e) {
                    Log.e(TAG, "cannot unpublish relay: ", e);
                  }
                })
            .show();
    Util.redPositiveButton(dialog);
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void handleEvent(@NonNull NcEvent event) {
    int eventId = event.getId();
    if (eventId == NcContext.NC_EVENT_CONFIGURE_PROGRESS) {
      if (event.getData1Int() == 1000) loadRelays();
    } else if (eventId == NcContext.NC_EVENT_TRANSPORTS_MODIFIED) {
      loadRelays();
    }
  }
}

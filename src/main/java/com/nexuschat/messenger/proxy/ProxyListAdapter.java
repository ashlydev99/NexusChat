package com.nexuschat.messenger.proxy;

import static com.nexuschat.messenger.connect.NcHelper.CONFIG_PROXY_ENABLED;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcLot;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import com.nexuschat.messenger.R;
import com.nexuschat.messenger.connect.NcHelper;

public class ProxyListAdapter extends BaseAdapter {
  private enum ProxyState {
    CONNECTED,
    CONNECTING,
    NOT_CONNECTED,
  }

  @NonNull private final Context context;
  @NonNull private final NcContext ncContext;
  @NonNull private final List<String> proxies = new LinkedList<>();
  @Nullable private ItemClickListener itemClickListener;
  @Nullable private ProxyState proxyState;
  @Nullable private String selectedProxy;

  public ProxyListAdapter(@NonNull Context context) {
    this.context = context;
    this.ncContext = NcHelper.getContext(context);
  }

  @Override
  public int getCount() {
    return proxies.size();
  }

  @Override
  public Object getItem(int position) {
    return proxies.get(position);
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public View getView(final int position, View v, final ViewGroup parent) {
    if (v == null) {
      v = LayoutInflater.from(context).inflate(R.layout.proxy_list_item, parent, false);
    }

    TextView host = v.findViewById(R.id.host);
    TextView protocol = v.findViewById(R.id.protocol);
    ImageView checkmark = v.findViewById(R.id.checkmark);
    TextView status = v.findViewById(R.id.status);

    final String proxyUrl = (String) getItem(position);
    final NcLot qrParsed = ncContext.checkQr(proxyUrl);
    if (qrParsed.getState() == NcContext.NC_QR_PROXY) {
      host.setText(qrParsed.getText1());
      protocol.setText(proxyUrl.split(":", 2)[0]);
    } else {
      host.setText(proxyUrl);
      protocol.setText(R.string.unknown);
    }
    if (proxyUrl.equals(selectedProxy)) {
      checkmark.setVisibility(View.VISIBLE);
      if (ncContext.isConfigured() == 1 && ncContext.getConfigInt(CONFIG_PROXY_ENABLED) == 1) {
        status.setVisibility(View.VISIBLE);
        status.setText(getConnectivityString());
      } else {
        status.setVisibility(View.GONE);
      }
    } else {
      checkmark.setVisibility(View.GONE);
      status.setVisibility(View.GONE);
    }

    v.setOnClickListener(
        view -> {
          if (itemClickListener != null) {
            itemClickListener.onItemClick(proxyUrl);
          }
        });
    v.findViewById(R.id.share)
        .setOnClickListener(
            view -> {
              if (itemClickListener != null) {
                itemClickListener.onItemShare(proxyUrl);
              }
            });
    v.findViewById(R.id.delete)
        .setOnClickListener(
            view -> {
              if (itemClickListener != null) {
                itemClickListener.onItemDelete(proxyUrl);
              }
            });

    return v;
  }

  public void changeData(String newProxies) {
    proxies.clear();
    if (!TextUtils.isEmpty(newProxies)) {
      Collections.addAll(proxies, newProxies.split("\n"));
    }
    selectedProxy = proxies.isEmpty() ? null : proxies.get(0);
    proxyState = null; // to force notifyDataSetChanged() in refreshConnectivity()
    refreshConnectivity();
  }

  public void setSelectedProxy(String proxyUrl) {
    selectedProxy = proxyUrl;
    notifyDataSetChanged();
  }

  private String getConnectivityString() {
    if (proxyState == ProxyState.CONNECTED) {
      return context.getString(R.string.connectivity_connected);
    }
    if (proxyState == ProxyState.CONNECTING) {
      return context.getString(R.string.connectivity_connecting);
    }
    return context.getString(R.string.connectivity_not_connected);
  }

  public void refreshConnectivity() {
    if (NcHelper.getInt(context, CONFIG_PROXY_ENABLED) != 1) {
      if (proxyState != ProxyState.NOT_CONNECTED) {
        proxyState = ProxyState.NOT_CONNECTED;
        notifyDataSetChanged();
      }
      return;
    }

    int connectivity = ncContext.getConnectivity();
    if (connectivity >= NcContext.NC_CONNECTIVITY_WORKING) {
      if (proxyState != ProxyState.CONNECTED) {
        proxyState = ProxyState.CONNECTED;
        notifyDataSetChanged();
      }
    } else if (connectivity >= NcContext.NC_CONNECTIVITY_CONNECTING) {
      if (proxyState != ProxyState.CONNECTING) {
        proxyState = ProxyState.CONNECTING;
        notifyDataSetChanged();
      }
    } else if (proxyState != ProxyState.NOT_CONNECTED) {
      proxyState = ProxyState.NOT_CONNECTED;
      notifyDataSetChanged();
    }
  }

  public void setItemClickListener(@Nullable ItemClickListener listener) {
    itemClickListener = listener;
  }

  public interface ItemClickListener {
    void onItemClick(String proxyUrl);

    void onItemShare(String proxyUrl);

    void onItemDelete(String proxyUrl);
  }
}

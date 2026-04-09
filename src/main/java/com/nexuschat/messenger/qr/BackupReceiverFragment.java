package com.nexuschat.messenger.qr;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.b44t.messenger.NcContext;
import com.b44t.messenger.NcEvent;
import com.nexuschat.messenger.R;
import com.nexuschat.messenger.connect.NcEventCenter;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.util.Util;

public class BackupReceiverFragment extends Fragment implements NcEventCenter.NcEventDelegate {

  private static final String TAG = BackupProviderFragment.class.getSimpleName();

  private NcContext ncContext;
  private TextView statusLine;
  private ProgressBar progressBar;
  private TextView sameNetworkHint;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.backup_receiver_fragment, container, false);
    statusLine = view.findViewById(R.id.status_line);
    progressBar = view.findViewById(R.id.progress_bar);
    sameNetworkHint = view.findViewById(R.id.same_network_hint);

    statusLine.setText(R.string.connectivity_connecting);
    progressBar.setIndeterminate(true);

    ncContext = NcHelper.getContext(getActivity());
    NcHelper.getEventCenter(getActivity()).addObserver(NcContext.NC_EVENT_IMEX_PROGRESS, this);

    String qrCode = getActivity().getIntent().getStringExtra(BackupTransferActivity.QR_CODE);

    new Thread(
            () -> {
              Log.i(TAG, "##### receiveBackup() with qr: " + qrCode);
              boolean res = ncContext.receiveBackup(qrCode);
              Log.i(TAG, "##### receiveBackup() done with result: " + res);
            })
        .start();

    BackupTransferActivity.appendSSID(getActivity(), sameNetworkHint);

    return view;
  }

  @Override
  public void onDestroyView() {
    ncContext.stopOngoingProcess();
    super.onDestroyView();
    NcHelper.getEventCenter(getActivity()).removeObservers(this);
  }

  @Override
  public void handleEvent(@NonNull NcEvent event) {
    if (event.getId() == NcContext.NC_EVENT_IMEX_PROGRESS) {
      int permille = event.getData1Int();
      int percent = 0;
      int percentMax = 0;
      boolean hideSameNetworkHint = false;
      String statusLineText = "";

      Log.i(TAG, "NC_EVENT_IMEX_PROGRESS, " + permille);
      if (permille == 0) {
        NcHelper.maybeShowMigrationError(getTransferActivity());
        getTransferActivity().setTransferError("Receiving Error");
      } else if (permille < 1000) {
        percent = permille / 10;
        percentMax = 100;
        String formattedPercent =
            percent > 0 ? String.format(Util.getLocale(), " %d%%", percent) : "";
        statusLineText = getString(R.string.transferring) + formattedPercent;
        hideSameNetworkHint = true;
      } else if (permille == 1000) {
        getTransferActivity()
            .setTransferState(BackupTransferActivity.TransferState.TRANSFER_SUCCESS);
        getTransferActivity().doFinish();
        return;
      }

      statusLine.setText(statusLineText);
      getTransferActivity().notificationController.setProgress(percentMax, percent, statusLineText);
      if (percentMax == 0) {
        progressBar.setIndeterminate(true);
      } else {
        progressBar.setIndeterminate(false);
        progressBar.setMax(percentMax);
        progressBar.setProgress(percent);
      }

      if (hideSameNetworkHint && sameNetworkHint.getVisibility() != View.GONE) {
        sameNetworkHint.setVisibility(View.GONE);
      }
    }
  }

  private BackupTransferActivity getTransferActivity() {
    return (BackupTransferActivity) getActivity();
  }
}

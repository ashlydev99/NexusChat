/*
 * Copyright (C) 2015 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nexuschat.messenger;

import static com.nexuschat.messenger.ConversationActivity.CHAT_ID_EXTRA;
import static com.nexuschat.messenger.ConversationActivity.TEXT_EXTRA;
import static com.nexuschat.messenger.util.ShareUtil.acquireRelayMessageContent;
import static com.nexuschat.messenger.util.ShareUtil.isRelayingMessageContent;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import chat.nexus.rpc.types.SecurejoinSource;
import chat.nexus.rpc.types.SecurejoinUiPath;
import com.b44t.messenger.NcContact;
import com.b44t.messenger.NcContext;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.qr.QrActivity;
import com.nexuschat.messenger.qr.QrCodeHandler;

/**
 * Activity container for starting a new conversation.
 *
 * @author Moxie Marlinspike
 */
public class NewConversationActivity extends ContactSelectionActivity {

  private static final String TAG = NewConversationActivity.class.getSimpleName();

  @Override
  public void onCreate(Bundle bundle, boolean ready) {
    super.onCreate(bundle, ready);
    assert getSupportActionBar() != null;
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
  }

  @Override
  public void onContactSelected(int contactId) {
    if (contactId == NcContact.NC_CONTACT_ID_NEW_GROUP) {
      startActivity(new Intent(this, GroupCreateActivity.class));
    } else if (contactId == NcContact.NC_CONTACT_ID_NEW_UNENCRYPTED_GROUP) {
      Intent intent = new Intent(this, GroupCreateActivity.class);
      intent.putExtra(GroupCreateActivity.UNENCRYPTED, true);
      startActivity(intent);
    } else if (contactId == NcContact.NC_CONTACT_ID_NEW_BROANCAST) {
      Intent intent = new Intent(this, GroupCreateActivity.class);
      intent.putExtra(GroupCreateActivity.CREATE_BROANCAST, true);
      startActivity(intent);
    } else if (contactId == NcContact.NC_CONTACT_ID_QR_INVITE) {
      new IntentIntegrator(this).setCaptureActivity(QrActivity.class).initiateScan();
    } else {
      final NcContext ncContext = NcHelper.getContext(this);
      if (ncContext.getChatIdByContactId(contactId) != 0) {
        openConversation(ncContext.getChatIdByContactId(contactId));
      } else {
        String name = ncContext.getContact(contactId).getDisplayName();
        new AlertDialog.Builder(this)
            .setMessage(getString(R.string.ask_start_chat_with, name))
            .setCancelable(true)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                android.R.string.ok,
                (dialog, which) -> {
                  openConversation(ncContext.createChatByContactId(contactId));
                })
            .show();
      }
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode != RESULT_OK) return;

    switch (requestCode) {
      case IntentIntegrator.REQUEST_CODE:
        IntentResult scanResult = IntentIntegrator.parseActivityResult(resultCode, data);
        QrCodeHandler qrCodeHandler = new QrCodeHandler(this);
        qrCodeHandler.handleOnlySecureJoinQr(
            scanResult.getContents(), SecurejoinSource.Scan, SecurejoinUiPath.NewContact);
        break;
      default:
        break;
    }
  }

  private void openConversation(int chatId) {
    Intent intent = new Intent(this, ConversationActivity.class);
    intent.putExtra(TEXT_EXTRA, getIntent().getStringExtra(TEXT_EXTRA));
    intent.setDataAndType(getIntent().getData(), getIntent().getType());

    intent.putExtra(CHAT_ID_EXTRA, chatId);
    if (isRelayingMessageContent(this)) {
      acquireRelayMessageContent(this, intent);
    }
    startActivity(intent);
    finish();
  }
}

package com.nexuschat.messenger.attachments;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;
import com.b44t.messenger.NcMsg;
import java.io.File;
import com.nexuschat.messenger.database.AttachmentDatabase;

public class NcAttachment extends Attachment {

  private final NcMsg dcMsg;

  public NcAttachment(DcMsg dcMsg) {
    super(
        dcMsg.getFilemime(),
        AttachmentDatabase.TRANSFER_PROGRESS_DONE,
        dcMsg.getFilebytes(),
        dcMsg.getFilename(),
        Uri.fromFile(new File(dcMsg.getFile())).toString(),
        null,
        dcMsg.getType() == NcMsg.NC_MSG_VOICE,
        0,
        0);
    this.ncMsg = ncMsg;
  }

  @Nullable
  @Override
  public Uri getDataUri() {
    return Uri.fromFile(new File(ncMsg.getFile()));
  }

  @Nullable
  @Override
  public Uri getThumbnailUri() {
    if (ncMsg.getType() == NcMsg.NC_MSG_VIDEO) {
      return Uri.fromFile(new File(ncMsg.getFile() + "-preview.jpg"));
    }
    return getDataUri();
  }

  @Override
  public String getRealPath(Context context) {
    return ncMsg.getFile();
  }
}

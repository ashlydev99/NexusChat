package com.nexuschat.messenger.mms;

import androidx.annotation.Nullable;
import com.b44t.messenger.NcContact;
import com.b44t.messenger.NcMsg;
import java.util.List;
import com.nexuschat.messenger.attachments.Attachment;

public class QuoteModel {

  private final NcContact author;
  private final String text;
  private final List<Attachment> attachments;
  private final NcMsg quotedMsg;

  public QuoteModel(
      NcContact author, String text, @Nullable List<Attachment> attachments, NcMsg quotedMsg) {
    this.author = author;
    this.text = text;
    this.attachments = attachments;
    this.quotedMsg = quotedMsg;
  }

  public NcContact getAuthor() {
    return author;
  }

  public String getText() {
    return text;
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }

  public NcMsg getQuotedMsg() {
    return quotedMsg;
  }
}

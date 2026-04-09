package com.nexuschat.messenger;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.b44t.messenger.NcMsg;
import com.codewaves.stickyheadergrid.StickyHeaderGridAdapter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import com.nexuschat.messenger.components.DocumentView;
import com.nexuschat.messenger.components.WebxncView;
import com.nexuschat.messenger.components.audioplay.AudioPlaybackViewModel;
import com.nexuschat.messenger.components.audioplay.AudioView;
import com.nexuschat.messenger.database.loaders.BucketedThreadMediaLoader.BucketedThreadMedia;
import com.nexuschat.messenger.mms.AudioSlide;
import com.nexuschat.messenger.mms.DocumentSlide;
import com.nexuschat.messenger.mms.Slide;
import com.nexuschat.messenger.util.DateUtils;
import com.nexuschat.messenger.util.MediaUtil;

class AllMediaDocumentsAdapter extends StickyHeaderGridAdapter {

  private final Context context;
  private final ItemClickListener itemClickListener;
  private final Set<NcMsg> selected;

  private BucketedThreadMedia media;
  private AudioPlaybackViewModel playbackViewModel;

  private static class ViewHolder extends StickyHeaderGridAdapter.ItemViewHolder {
    private final DocumentView documentView;
    private final AudioView audioView;
    private final WebxncView webxncView;
    private final TextView date;

    public ViewHolder(View v) {
      super(v);
      documentView = v.findViewById(R.id.document_view);
      audioView = v.findViewById(R.id.audio_view);
      webxncView = v.findViewById(R.id.webxnc_view);
      date = v.findViewById(R.id.date);
    }
  }

  private static class HeaderHolder extends StickyHeaderGridAdapter.HeaderViewHolder {
    final TextView textView;

    HeaderHolder(View itemView) {
      super(itemView);
      textView = itemView.findViewById(R.id.label);
    }
  }

  AllMediaDocumentsAdapter(
      @NonNull Context context, BucketedThreadMedia media, ItemClickListener clickListener) {
    this.context = context;
    this.media = media;
    this.itemClickListener = clickListener;
    this.selected = new HashSet<>();
  }

  public void setMedia(BucketedThreadMedia media) {
    this.media = media;
  }

  public void setPlaybackViewModel(AudioPlaybackViewModel playbackViewModel) {
    this.playbackViewModel = playbackViewModel;
  }

  @Override
  public StickyHeaderGridAdapter.HeaderViewHolder onCreateHeaderViewHolder(
      ViewGroup parent, int headerType) {
    return new HeaderHolder(
        LayoutInflater.from(context)
            .inflate(R.layout.contact_selection_list_divider, parent, false));
  }

  @Override
  public ItemViewHolder onCreateItemViewHolder(ViewGroup parent, int itemType) {
    return new ViewHolder(
        LayoutInflater.from(context).inflate(R.layout.profile_document_item, parent, false));
  }

  @Override
  public void onBindHeaderViewHolder(
      StickyHeaderGridAdapter.HeaderViewHolder viewHolder, int section) {
    ((HeaderHolder) viewHolder).textView.setText(media.getName(section));
  }

  @Override
  public void onBindItemViewHolder(ItemViewHolder itemViewHolder, int section, int offset) {
    ViewHolder viewHolder = ((ViewHolder) itemViewHolder);
    NcMsg ncMsg = media.get(section, offset);
    Slide slide = MediaUtil.getSlideForMsg(context, ncMsg);

    if (slide != null && slide.hasAudio()) {
      viewHolder.documentView.setVisibility(View.GONE);
      viewHolder.webxncView.setVisibility(View.GONE);

      viewHolder.audioView.setVisibility(View.VISIBLE);
      viewHolder.audioView.setPlaybackViewModel(playbackViewModel);
      viewHolder.audioView.setAudio((AudioSlide) slide);
      viewHolder.audioView.setOnClickListener(view -> itemClickListener.onMediaClicked(ncMsg));
      viewHolder.audioView.setOnLongClickListener(
          view -> {
            itemClickListener.onMediaLongClicked(ncMsg);
            return true;
          });
      viewHolder.audioView.disablePlayer(!selected.isEmpty());
      viewHolder.itemView.setOnClickListener(view -> itemClickListener.onMediaClicked(ncMsg));
      viewHolder.date.setVisibility(View.VISIBLE);
    } else if (slide != null && slide.isWebxncDocument()) {
      viewHolder.audioView.setVisibility(View.GONE);
      viewHolder.documentView.setVisibility(View.GONE);

      viewHolder.webxncView.setVisibility(View.VISIBLE);
      viewHolder.webxncView.setWebxnc(ncMsg, "");
      viewHolder.webxncView.setOnClickListener(view -> itemClickListener.onMediaClicked(ncMsg));
      viewHolder.webxncView.setOnLongClickListener(
          view -> {
            itemClickListener.onMediaLongClicked(ncMsg);
            return true;
          });
      viewHolder.itemView.setOnClickListener(view -> itemClickListener.onMediaClicked(ncMsg));
      viewHolder.date.setVisibility(View.GONE);
    } else if (slide != null && slide.hasDocument()) {
      viewHolder.audioView.setVisibility(View.GONE);
      viewHolder.webxncView.setVisibility(View.GONE);

      viewHolder.documentView.setVisibility(View.VISIBLE);
      viewHolder.documentView.setDocument((DocumentSlide) slide);
      viewHolder.documentView.setOnClickListener(view -> itemClickListener.onMediaClicked(ncMsg));
      viewHolder.documentView.setOnLongClickListener(
          view -> {
            itemClickListener.onMediaLongClicked(ncMsg);
            return true;
          });
      viewHolder.itemView.setOnClickListener(view -> itemClickListener.onMediaClicked(ncMsg));
      viewHolder.date.setVisibility(View.VISIBLE);
    } else {
      viewHolder.documentView.setVisibility(View.GONE);
      viewHolder.audioView.setVisibility(View.GONE);
      viewHolder.webxncView.setVisibility(View.GONE);
      viewHolder.date.setVisibility(View.GONE);
    }

    viewHolder.itemView.setOnLongClickListener(
        view -> {
          itemClickListener.onMediaLongClicked(ncMsg);
          return true;
        });
    viewHolder.itemView.setSelected(selected.contains(ncMsg));

    viewHolder.date.setText(
        DateUtils.getBriefRelativeTimeSpanString(context, ncMsg.getTimestamp()));
  }

  @Override
  public int getSectionCount() {
    return media.getSectionCount();
  }

  @Override
  public int getSectionItemCount(int section) {
    return media.getSectionItemCount(section);
  }

  public void toggleSelection(@NonNull NcMsg mediaRecord) {
    if (!selected.remove(mediaRecord)) {
      selected.add(mediaRecord);
    }
    notifyDataSetChanged();
  }

  public void selectAll() {
    selected.clear();
    selected.addAll(media.getAll());
    notifyDataSetChanged();
  }

  public int getSelectedMediaCount() {
    return selected.size();
  }

  public Set<NcMsg> getSelectedMedia() {
    return Collections.unmodifiableSet(new HashSet<>(selected));
  }

  public void clearSelection() {
    selected.clear();
    notifyDataSetChanged();
  }

  interface ItemClickListener {
    void onMediaClicked(@NonNull NcMsg mediaRecord);

    void onMediaLongClicked(NcMsg mediaRecord);
  }
}

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
import com.nexuschat.messenger.components.ThumbnailView;
import com.nexuschat.messenger.database.loaders.BucketedThreadMediaLoader.BucketedThreadMedia;
import com.nexuschat.messenger.mms.GlideRequests;
import com.nexuschat.messenger.mms.Slide;
import com.nexuschat.messenger.util.MediaUtil;

class AllMediaGalleryAdapter extends StickyHeaderGridAdapter {

  private final Context context;
  private final GlideRequests glideRequests;
  private final ItemClickListener itemClickListener;
  private final Set<NcMsg> selected;

  private BucketedThreadMedia media;

  private static class ViewHolder extends StickyHeaderGridAdapter.ItemViewHolder {
    final ThumbnailView imageView;
    final View selectedIndicator;

    ViewHolder(View v) {
      super(v);
      imageView = v.findViewById(R.id.image);
      selectedIndicator = v.findViewById(R.id.selected_indicator);
    }
  }

  private static class HeaderHolder extends StickyHeaderGridAdapter.HeaderViewHolder {
    final TextView textView;

    HeaderHolder(View itemView) {
      super(itemView);
      textView = itemView.findViewById(R.id.label);
    }
  }

  AllMediaGalleryAdapter(
      @NonNull Context context,
      @NonNull GlideRequests glideRequests,
      BucketedThreadMedia media,
      ItemClickListener clickListener) {
    this.context = context;
    this.glideRequests = glideRequests;
    this.media = media;
    this.itemClickListener = clickListener;
    this.selected = new HashSet<>();
  }

  public void setMedia(BucketedThreadMedia media) {
    this.media = media;
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
        LayoutInflater.from(context).inflate(R.layout.profile_gallery_item, parent, false));
  }

  @Override
  public void onBindHeaderViewHolder(
      StickyHeaderGridAdapter.HeaderViewHolder viewHolder, int section) {
    ((HeaderHolder) viewHolder).textView.setText(media.getName(section));
  }

  @Override
  public void onBindItemViewHolder(ItemViewHolder viewHolder, int section, int offset) {
    NcMsg mediaRecord = media.get(section, offset);
    ThumbnailView thumbnailView = ((ViewHolder) viewHolder).imageView;
    View selectedIndicator = ((ViewHolder) viewHolder).selectedIndicator;
    Slide slide = MediaUtil.getSlideForMsg(context, mediaRecord);

    if (slide != null) {
      thumbnailView.setImageResource(glideRequests, slide);
    }

    thumbnailView.setOnClickListener(view -> itemClickListener.onMediaClicked(mediaRecord));
    thumbnailView.setOnLongClickListener(
        view -> {
          itemClickListener.onMediaLongClicked(mediaRecord);
          return true;
        });

    selectedIndicator.setVisibility(selected.contains(mediaRecord) ? View.VISIBLE : View.GONE);
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

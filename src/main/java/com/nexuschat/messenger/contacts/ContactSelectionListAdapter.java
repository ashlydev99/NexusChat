/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * <p>This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 */
package com.nexuschat.messenger.contacts;

import android.content.Context;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.b44t.messenger.NcContact;
import com.b44t.messenger.NcContext;
import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.nexuschat.messenger.R;
import com.nexuschat.messenger.connect.NcContactsLoader;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.mms.GlideRequests;
import com.nexuschat.messenger.util.LRUCache;

/**
 * List adapter to display all contacts and their related information
 *
 * @author Jake McGinty
 */
public class ContactSelectionListAdapter
    extends RecyclerView.Adapter<ContactSelectionListAdapter.ViewHolder> {
  private static final int VIEW_TYPE_CONTACT = 0;
  private static final int MAX_CACHE_SIZE = 100;

  private final Map<Integer, SoftReference<NcContact>> recordCache =
      Collections.synchronizedMap(new LRUCache<Integer, SoftReference<NcContact>>(MAX_CACHE_SIZE));

  private final @NonNull Context context;
  private final @NonNull NcContext ncContext;
  private @NonNull int[] ncContactList = new int[0];
  private final boolean multiSelect;
  private final boolean longPressSelect;
  private final LayoutInflater li;
  private final ItemClickListener clickListener;
  private final GlideRequests glideRequests;
  private final Set<Integer> selectedContacts = new HashSet<>();
  private final SparseIntArray actionModeSelection = new SparseIntArray();

  @Override
  public int getItemCount() {
    return ncContactList.length;
  }

  private @NonNull NcContact getContact(int position) {
    if (position < 0 || position >= ncContactList.length) {
      return new NcContact(0);
    }

    final SoftReference<NcContact> reference = recordCache.get(position);
    if (reference != null) {
      final NcContact fromCache = reference.get();
      if (fromCache != null) {
        return fromCache;
      }
    }

    final NcContact fromDb = ncContext.getContact(ncContactList[position]);
    recordCache.put(position, new SoftReference<>(fromDb));
    return fromDb;
  }

  public void resetActionModeSelection() {
    actionModeSelection.clear();
    notifyDataSetChanged();
  }

  public void selectAll() {
    actionModeSelection.clear();
    for (int index = 0; index < ncContactList.length; index++) {
      int value = ncContactList[index];
      if (value > 0) {
        actionModeSelection.put(index, value);
      }
    }
    notifyDataSetChanged();
  }

  private boolean isActionModeEnabled() {
    return actionModeSelection.size() != 0;
  }

  public abstract static class ViewHolder extends RecyclerView.ViewHolder {

    public ViewHolder(View itemView) {
      super(itemView);
    }

    public abstract void bind(
        @NonNull GlideRequests glideRequests,
        int type,
        NcContact contact,
        String name,
        String number,
        String label,
        boolean multiSelect,
        boolean enabled);

    public abstract void unbind(@NonNull GlideRequests glideRequests);

    public abstract void setChecked(boolean checked);

    public abstract void setSelected(boolean enabled);

    public abstract void setEnabled(boolean enabled);
  }

  public class ContactViewHolder extends ViewHolder {

    ContactViewHolder(
        @NonNull final View itemView, @Nullable final ItemClickListener clickListener) {
      super(itemView);
      itemView.setOnClickListener(
          view -> {
            if (clickListener != null) {
              if (isActionModeEnabled()) {
                toggleSelection();
                clickListener.onItemClick(getView(), true);
              } else {
                clickListener.onItemClick(getView(), false);
              }
            }
          });
      itemView.setOnLongClickListener(
          view -> {
            if (clickListener != null) {
              int contactId = getContactId(getAdapterPosition());
              if (contactId > 0) {
                toggleSelection();
                clickListener.onItemLongClick(getView());
              }
            }
            return true;
          });
    }

    private int getContactId(int adapterPosition) {
      return ContactSelectionListAdapter.this.ncContactList[adapterPosition];
    }

    private void toggleSelection() {
      if (!longPressSelect) {
        return;
      }
      int adapterPosition = getBindingAdapterPosition();
      if (adapterPosition < 0) return;
      int contactId = getContactId(adapterPosition);
      boolean enabled = actionModeSelection.indexOfKey(adapterPosition) > -1;
      if (enabled) {
        ContactSelectionListAdapter.this.actionModeSelection.delete(adapterPosition);
      } else {
        ContactSelectionListAdapter.this.actionModeSelection.put(adapterPosition, contactId);
      }
      notifyDataSetChanged();
    }

    public ContactSelectionListItem getView() {
      return (ContactSelectionListItem) itemView;
    }

    public void bind(
        @NonNull GlideRequests glideRequests,
        int type,
        NcContact contact,
        String name,
        String addr,
        String label,
        boolean multiSelect,
        boolean enabled) {
      getView().set(glideRequests, type, contact, name, addr, label, multiSelect, enabled);
    }

    @Override
    public void unbind(@NonNull GlideRequests glideRequests) {
      getView().unbind(glideRequests);
    }

    @Override
    public void setChecked(boolean checked) {
      getView().setChecked(checked);
    }

    @Override
    public void setSelected(boolean enabled) {
      getView().setSelected(enabled);
    }

    @Override
    public void setEnabled(boolean enabled) {
      getView().setEnabled(enabled);
    }
  }

  public static class DividerViewHolder extends ViewHolder {

    private final TextView label;

    DividerViewHolder(View itemView) {
      super(itemView);
      this.label = itemView.findViewById(R.id.label);
    }

    @Override
    public void bind(
        @NonNull GlideRequests glideRequests,
        int type,
        NcContact contact,
        String name,
        String number,
        String label,
        boolean multiSelect,
        boolean enabled) {
      this.label.setText(name);
    }

    @Override
    public void unbind(@NonNull GlideRequests glideRequests) {}

    @Override
    public void setChecked(boolean checked) {}

    @Override
    public void setSelected(boolean enabled) {}

    @Override
    public void setEnabled(boolean enabled) {}
  }

  public ContactSelectionListAdapter(
      @NonNull Context context,
      @NonNull GlideRequests glideRequests,
      @Nullable ItemClickListener clickListener,
      boolean multiSelect,
      boolean longPressSelect) {
    super();
    this.context = context;
    this.ncContext = DcHelper.getContext(context);
    this.li = LayoutInflater.from(context);
    this.glideRequests = glideRequests;
    this.multiSelect = multiSelect;
    this.clickListener = clickListener;
    this.longPressSelect = longPressSelect;
  }

  @NonNull
  @Override
  public ContactSelectionListAdapter.ViewHolder onCreateViewHolder(
      @NonNull ViewGroup parent, int viewType) {
    if (viewType == VIEW_TYPE_CONTACT) {
      return new ContactViewHolder(
          li.inflate(R.layout.contact_selection_list_item, parent, false), clickListener);
    } else {
      return new DividerViewHolder(
          li.inflate(R.layout.contact_selection_list_divider, parent, false));
    }
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {

    int id = ncContactList[i];
    NcContact dcContact = null;
    String name;
    String addr = null;
    boolean itemMultiSelect = multiSelect;

    if (id == NcContact.NC_CONTACT_ID_NEW_CLASSIC_CONTACT) {
      name = context.getString(R.string.menu_new_classic_contact);
      itemMultiSelect =
          false; // the item creates a new contact in the list that will be selected instead
    } else if (id == NcContact.NC_CONTACT_ID_NEW_GROUP) {
      name = context.getString(R.string.menu_new_group);
    } else if (id == NcContact.NC_CONTACT_ID_NEW_UNENCRYPTED_GROUP) {
      name = context.getString(R.string.new_email);
    } else if (id == NcContact.NC_CONTACT_ID_NEW_BROADCAST) {
      name = context.getString(R.string.new_channel);
    } else if (id == NcContact.NC_CONTACT_ID_QR_INVITE) {
      name = context.getString(R.string.menu_new_contact);
    } else {
      dcContact = getContact(i);
      name = ncContact.getDisplayName();
      addr = ncContact.getAddr();
    }

    viewHolder.unbind(glideRequests);
    boolean enabled = true;
    if (ncContact == null) {
      viewHolder.setSelected(false);
      viewHolder.setEnabled(!isActionModeEnabled());
      if (isActionModeEnabled()) {
        enabled = false;
      }
    } else {
      boolean selected = actionModeSelection.indexOfValue(id) > -1;
      viewHolder.setSelected(selected);
      enabled = !(ncContact.getId() == NcContact.NC_CONTACT_ID_SELF && itemMultiSelect);
    }
    viewHolder.bind(glideRequests, id, ncContact, name, addr, null, itemMultiSelect, enabled);
    viewHolder.setChecked(selectedContacts.contains(id));
  }

  @Override
  public int getItemViewType(int i) {
    return VIEW_TYPE_CONTACT;
  }

  public Set<Integer> getSelectedContacts() {
    return selectedContacts;
  }

  public SparseIntArray getActionModeSelection() {
    return actionModeSelection;
  }

  public interface ItemClickListener {
    void onItemClick(ContactSelectionListItem item, boolean handleActionMode);

    void onItemLongClick(ContactSelectionListItem view);
  }

  public void changeData(NcContactsLoader.Ret loaderRet) {
    this.ncContactList = loaderRet == null ? new int[0] : loaderRet.ids;
    recordCache.clear();
    notifyDataSetChanged();
  }
}

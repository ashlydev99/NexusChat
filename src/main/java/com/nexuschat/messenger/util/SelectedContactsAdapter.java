package com.nexuschat.messenger.util;

import static com.b44t.messenger.NcContact.NC_CONTACT_ID_ADD_MEMBER;
import static com.b44t.messenger.NcContact.NC_CONTACT_ID_SELF;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import com.b44t.messenger.NcContact;
import com.b44t.messenger.NcContext;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import com.nexuschat.messenger.R;
import com.nexuschat.messenger.components.AvatarImageView;
import com.nexuschat.messenger.connect.NcHelper;
import com.nexuschat.messenger.mms.GlideRequests;
import com.nexuschat.messenger.recipients.Recipient;

public class SelectedContactsAdapter extends BaseAdapter {
  @NonNull private final Context context;
  @Nullable private ItemClickListener itemClickListener;
  @NonNull private final List<Integer> contacts = new LinkedList<>();
  private final boolean isBroadcast;
  @NonNull private final NcContext ncContext;
  @NonNull private final GlideRequests glideRequests;

  public SelectedContactsAdapter(
      @NonNull Context context, @NonNull GlideRequests glideRequests, boolean isBroadcast) {
    this.context = context;
    this.glideRequests = glideRequests;
    this.isBroadcast = isBroadcast;
    this.ncContext = NcHelper.getContext(context);
  }

  public void changeData(Collection<Integer> contactIds) {
    contacts.clear();
    if (!isBroadcast) {
      contacts.add(NC_CONTACT_ID_ADD_MEMBER);
    }
    if (contactIds != null) {
      for (int id : contactIds) {
        if (id != NC_CONTACT_ID_SELF) {
          contacts.add(id);
        }
      }
    }
    if (!isBroadcast) {
      contacts.add(NC_CONTACT_ID_SELF);
    }
    notifyDataSetChanged();
  }

  public void remove(@NonNull Integer contactId) {
    if (contacts.remove(contactId)) {
      notifyDataSetChanged();
    }
  }

  public Set<Integer> getContacts() {
    final Set<Integer> set = new HashSet<>(contacts.size());
    for (int i = 1; i < contacts.size(); i++) {
      set.add(contacts.get(i));
    }
    return set;
  }

  @Override
  public int getCount() {
    return contacts.size();
  }

  @Override
  public Object getItem(int position) {
    return contacts.get(position);
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public View getView(final int position, View v, final ViewGroup parent) {
    if (v == null) {
      v = LayoutInflater.from(context).inflate(R.layout.selected_contact_list_item, parent, false);
    }

    AvatarImageView avatar = v.findViewById(R.id.contact_photo_image);
    AppCompatTextView name = v.findViewById(R.id.name);
    TextView phone = v.findViewById(R.id.phone);
    ImageButton delete = v.findViewById(R.id.delete);

    final int contactId = (int) getItem(position);
    final boolean modifiable =
        contactId != NC_CONTACT_ID_ADD_MEMBER && contactId != NC_CONTACT_ID_SELF;
    Recipient recipient = null;

    if (contactId == NcContact.NC_CONTACT_ID_ADD_MEMBER) {
      name.setText(context.getString(R.string.group_add_members));
      name.setTypeface(null, Typeface.BOLD);
      phone.setVisibility(View.GONE);
    } else {
      NcContact ncContact = ncContext.getContact(contactId);
      recipient = new Recipient(context, ncContact);
      name.setText(ncContact.getDisplayName());
      name.setTypeface(null, Typeface.NORMAL);
      phone.setText(ncContact.getAddr());
      phone.setVisibility(View.VISIBLE);
    }

    avatar.clear(glideRequests);
    avatar.setAvatar(glideRequests, recipient, false);
    delete.setVisibility(modifiable ? View.VISIBLE : View.GONE);
    delete.setColorFilter(DynamicTheme.isDarkTheme(context) ? Color.WHITE : Color.BLACK);
    delete.setOnClickListener(
        view -> {
          if (itemClickListener != null) {
            itemClickListener.onItemDeleteClick(contacts.get(position));
          }
        });
    v.setOnClickListener(
        view -> {
          if (itemClickListener != null) {
            itemClickListener.onItemClick(contacts.get(position));
          }
        });

    return v;
  }

  public void setItemClickListener(@Nullable ItemClickListener listener) {
    itemClickListener = listener;
  }

  public interface ItemClickListener {
    void onItemClick(int contactId);

    void onItemDeleteClick(int contactId);
  }
}

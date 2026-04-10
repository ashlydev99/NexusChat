package org.thoughtcrime.securesms;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.b44t.messenger.DcContact;
import com.b44t.messenger.DcContext;
import org.thoughtcrime.securesms.components.AvatarView;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ViewUtil;

public class ContactsFragment extends Fragment {

    private RecyclerView recyclerView;
    private ContactsAdapter adapter;
    private DcContext dcContext;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_contacts, container, false);
        
        dcContext = DcHelper.getContext(requireContext());
        recyclerView = view.findViewById(R.id.contacts_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        adapter = new ContactsAdapter();
        recyclerView.setAdapter(adapter);
        
        loadContacts();
        
        return view;
    }
    
    private void loadContacts() {
        int[] contactIds = dcContext.getContacts(0, null);
        adapter.setContacts(contactIds);
    }
    
    private class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ViewHolder> {
        private int[] contactIds = new int[0];
        
        public void setContacts(int[] ids) {
            this.contactIds = ids;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.contact_list_item, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            int contactId = contactIds[position];
            DcContact contact = dcContext.getContact(contactId);
            
            holder.name.setText(contact.getDisplayName());
            holder.email.setText(contact.getAddr());
            
            Recipient recipient = new Recipient(requireContext(), contact, contact.getDisplayName());
            holder.avatar.setAvatar(GlideApp.with(requireContext()), recipient, false);
            
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), ProfileActivity.class);
                intent.putExtra(ProfileActivity.CONTACT_ID_EXTRA, contactId);
                startActivity(intent);
            });
        }
        
        @Override
        public int getItemCount() {
            return contactIds.length;
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            AvatarView avatar;
            TextView name;
            TextView email;
            
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                avatar = itemView.findViewById(R.id.contact_avatar);
                name = itemView.findViewById(R.id.contact_name);
                email = itemView.findViewById(R.id.contact_email);
            }
        }
    }
}
package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.util.Prefs;

public class SettingsFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        
        // Configurar listeners para cada sección de ajustes
        setupSettingsListeners(view);
        
        return view;
    }
    
    private void setupSettingsListeners(View view) {
        // Cuenta
        view.findViewById(R.id.settings_account).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), AccountSettingsActivity.class));
        });
        
        // Ajustes de chats
        view.findViewById(R.id.settings_chats).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), ChatSettingsActivity.class));
        });
        
        // Privacidad y seguridad
        view.findViewById(R.id.settings_privacy).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), PrivacySettingsActivity.class));
        });
        
        // Notificaciones
        view.findViewById(R.id.settings_notifications).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), NotificationSettingsActivity.class));
        });
        
        // Datos y almacenamiento
        view.findViewById(R.id.settings_data_storage).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), DataStorageSettingsActivity.class));
        });
        
        // Dispositivos
        view.findViewById(R.id.settings_devices).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), MultiDeviceSettingsActivity.class));
        });
        
        // Avanzado
        view.findViewById(R.id.settings_advanced).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), AdvancedPreferencesActivity.class));
        });
        
        // Ayuda
        view.findViewById(R.id.settings_help).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), HelpActivity.class));
        });
    }
}
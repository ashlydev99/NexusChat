package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Redirigir a la actividad de ajustes existente
        Intent intent = new Intent(getActivity(), ApplicationPreferencesActivity.class);
        startActivity(intent);
        
        // Cerrar el fragment
        if (getActivity() != null) {
            getActivity().finish();
        }
        
        return new View(getContext());
    }
}
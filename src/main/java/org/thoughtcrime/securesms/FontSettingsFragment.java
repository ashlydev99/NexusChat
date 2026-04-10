package org.thoughtcrime.securesms.preferences;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Prefs;

public class FontSettingsFragment extends Fragment {
    
    private RadioGroup fontRadioGroup;
    private TextView previewText;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_font_settings, container, false);
        
        fontRadioGroup = view.findViewById(R.id.font_radio_group);
        previewText = view.findViewById(R.id.preview_text);
        
        // Cargar fuente guardada
        String savedFont = Prefs.getStringPreference(getContext(), "selected_font", "default");
        selectRadioButton(savedFont);
        
        // Configurar listener
        fontRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String selectedFont = getFontFromId(checkedId);
            Prefs.setStringPreference(getContext(), "selected_font", selectedFont);
            
            // Recrear actividad para aplicar cambios
            if (getActivity() != null) {
                getActivity().recreate();
            }
        });
        
        return view;
    }
    
    private void selectRadioButton(String font) {
        int id = getIdFromFont(font);
        if (id != -1) {
            fontRadioGroup.check(id);
        }
    }
    
    private int getIdFromFont(String font) {
        switch (font) {
            case "default": return R.id.font_default;
            case "sans_serif": return R.id.font_sans;
            case "serif": return R.id.font_serif;
            case "monospace": return R.id.font_mono;
            case "roboto": return R.id.font_roboto;
            default: return R.id.font_default;
        }
    }
    
    private String getFontFromId(int id) {
        if (id == R.id.font_sans) return "sans_serif";
        if (id == R.id.font_serif) return "serif";
        if (id == R.id.font_mono) return "monospace";
        if (id == R.id.font_roboto) return "roboto";
        return "default";
    }
}
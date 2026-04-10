package org.thoughtcrime.securesms;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.service.GenericForegroundService;
import org.thoughtcrime.securesms.util.Prefs;

public abstract class PassphraseRequiredActionBarActivity extends BaseActionBarActivity {
  private static final String TAG = PassphraseRequiredActionBarActivity.class.getSimpleName();

  // ============================================
  // MÓDULO 1: FUENTES PERSONALIZADAS
  // ============================================
  
  public static final String FONT_DEFAULT = "default";
  public static final String FONT_SANS_SERIF = "sans_serif";
  public static final String FONT_SERIF = "serif";
  public static final String FONT_MONOSPACE = "monospace";
  public static final String FONT_ROBOTO = "roboto";
  
  /**
   * Aplica la fuente seleccionada a toda la vista
   */
  protected void applySelectedFont(View view) {
    String selectedFont = Prefs.getStringPreference(this, "selected_font", FONT_DEFAULT);
    Typeface typeface = getTypefaceForFont(selectedFont);
    if (typeface != null) {
      applyTypefaceToView(view, typeface);
    }
  }
  
  /**
   * Obtiene el Typeface según la fuente seleccionada
   */
  private Typeface getTypefaceForFont(String font) {
    switch (font) {
      case FONT_SANS_SERIF:
        return Typeface.create("sans-serif", Typeface.NORMAL);
      case FONT_SERIF:
        return Typeface.create("serif", Typeface.NORMAL);
      case FONT_MONOSPACE:
        return Typeface.create("monospace", Typeface.NORMAL);
      case FONT_ROBOTO:
        return Typeface.create("sans-serif", Typeface.NORMAL);
      case FONT_DEFAULT:
      default:
        return null;
    }
  }
  
  /**
   * Aplica Typeface recursivamente a todos los TextView de una vista
   */
  private void applyTypefaceToView(View view, Typeface typeface) {
    if (view == null) return;
    
    if (view instanceof TextView) {
      ((TextView) view).setTypeface(typeface);
    } else if (view instanceof ViewGroup) {
      ViewGroup viewGroup = (ViewGroup) view;
      for (int i = 0; i < viewGroup.getChildCount(); i++) {
        applyTypefaceToView(viewGroup.getChildAt(i), typeface);
      }
    }
  }

  @Override
  protected final void onCreate(Bundle savedInstanceState) {
    Log.w(TAG, "onCreate(" + savedInstanceState + ")");
    
    // Aplicar fuente seleccionada antes de crear la actividad
    String selectedFont = Prefs.getStringPreference(this, "selected_font", FONT_DEFAULT);
    if (!selectedFont.equals(FONT_DEFAULT)) {
      Typeface typeface = getTypefaceForFont(selectedFont);
      if (typeface != null) {
        getTheme().applyStyle(getFontThemeStyle(selectedFont), true);
      }
    }

    if (allowInLockedMode()) {
      super.onCreate(savedInstanceState);
      onCreate(savedInstanceState, true);
      return;
    }

    if (GenericForegroundService.isForegroundTaskStarted()) {
      super.onCreate(savedInstanceState);
      finish();
      return;
    }

    if (!DcHelper.isConfigured(getApplicationContext())) {
      Intent intent = new Intent(this, WelcomeActivity.class);
      startActivity(intent);
      super.onCreate(savedInstanceState);
      finish();
    } else {
      super.onCreate(savedInstanceState);
    }

    if (!isFinishing()) {
      onCreate(savedInstanceState, true);
    }
  }
  
  /**
   * Obtiene el estilo del tema según la fuente seleccionada
   */
  private int getFontThemeStyle(String font) {
    switch (font) {
      case FONT_SANS_SERIF:
        return R.style.Font_SansSerif;
      case FONT_SERIF:
        return R.style.Font_Serif;
      case FONT_MONOSPACE:
        return R.style.Font_Monospace;
      case FONT_ROBOTO:
        return R.style.Font_Roboto;
      default:
        return R.style.Font_Default;
    }
  }

  protected void onCreate(Bundle savedInstanceState, boolean ready) {}

  protected boolean allowInLockedMode() {
    return false;
  }
}
package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class SecurePrefs {
    private static final String SECURE_PREFS_NAME = "secure_prefs";
    private static SharedPreferences sharedPreferences = null;

    private static SharedPreferences getSharedPreferences(Context context) {
        if (sharedPreferences == null) {
            try {
                String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
                sharedPreferences = EncryptedSharedPreferences.create(
                    SECURE_PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (GeneralSecurityException | IOException e) {
                e.printStackTrace();
                sharedPreferences = context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE);
            }
        }
        return sharedPreferences;
    }

    public static void setStringPreference(Context context, String key, String value) {
        getSharedPreferences(context).edit().putString(key, value).apply();
    }

    public static String getStringPreference(Context context, String key, String defaultValue) {
        return getSharedPreferences(context).getString(key, defaultValue);
    }

    public static void removePreference(Context context, String key) {
        getSharedPreferences(context).edit().remove(key).apply();
    }

    public static boolean contains(Context context, String key) {
        return getSharedPreferences(context).contains(key);
    }
}
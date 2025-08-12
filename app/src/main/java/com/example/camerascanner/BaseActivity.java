package com.example.camerascanner;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import java.util.Locale;

public abstract class BaseActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "MyPrefs";
    private static final String THEME_KEY = "theme";
    private static final String LANGUAGE_KEY = "language";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // --- Tải và áp dụng cài đặt trước khi tạo Activity ---

        // Lấy SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Áp dụng ngôn ngữ đã lưu
        String savedLang = sharedPreferences.getString(LANGUAGE_KEY, "vi"); // Mặc định là tiếng Việt
        setLocale(this, savedLang);

        // Áp dụng giao diện đã lưu
        int savedTheme = sharedPreferences.getInt(THEME_KEY, AppCompatDelegate.MODE_NIGHT_NO); // Mặc định là Sáng
        AppCompatDelegate.setDefaultNightMode(savedTheme);

        super.onCreate(savedInstanceState);
    }

    // Phương thức để thay đổi ngôn ngữ, có thể được gọi từ các Activity con
    public void setLocale(Context context, String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }
}

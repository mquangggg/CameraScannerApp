package com.example.camerascanner.activitymain;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatButton;

import com.example.camerascanner.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import java.util.Locale;

public class MeActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "MyPrefs";
    private static final String THEME_KEY = "theme";
    private static final String LANGUAGE_KEY = "language";


    private BottomNavigationView bottomNavigationiew;
    private AppCompatButton btnLanguage, btnBlackWhite;

    @Override
    protected void onCreate(Bundle saveInstance){
        // --- 1. Tải giao diện và ngôn ngữ đã lưu từ SharedPreferences ---
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Tải và áp dụng giao diện
        int savedTheme = sharedPreferences.getInt(THEME_KEY, AppCompatDelegate.MODE_NIGHT_NO);
        AppCompatDelegate.setDefaultNightMode(savedTheme);

        // Tải và áp dụng ngôn ngữ
        String savedLang = sharedPreferences.getString(LANGUAGE_KEY, "vi"); // Mặc định là Tiếng Việt
        setLocale(this, savedLang);

        super.onCreate(saveInstance);
        setContentView(R.layout.activity_me_main);

        btnLanguage = findViewById(R.id.btnLanguage);
        btnBlackWhite = findViewById(R.id.btnBlackWhite);

        btnLanguage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLanguageDialog();
            }
        });
        btnBlackWhite.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showThemeDialog();
            }
        }));

        bottomNavigationiew = findViewById(R.id.bottom_navigation);
        // Đặt mục được chọn là nav_me khi MeActivity được tạo
        bottomNavigationiew.setSelectedItemId(R.id.nav_me);

        bottomNavigationiew.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int itemID = item.getItemId();
                if(itemID == R.id.nav_home){
                    Intent intent = new Intent(MeActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish(); // Kết thúc MeActivity khi chuyển về MainActivity
                    return true;
                }else if(itemID == R.id.nav_tools){
                    Toast.makeText(MeActivity.this, "Chức năng Tools sẽ sớm ra mắt!", Toast.LENGTH_SHORT).show();
                    return true;
                }else if(itemID == R.id.nav_me){
                    return true; // Đã ở MeActivity, không làm gì
                }
                return false;
            }
        });
    }
    private void showLanguageDialog() {
        final String[] languages = {"Tiếng Việt", "English"};
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Lấy chế độ ban đêm hiện tại của ứng dụng
        String currentLanguageMode = sharedPreferences.getString(LANGUAGE_KEY, "vi"); // Lấy ngôn ngữ đã lưu

        // Thiết lập chỉ số mặc định đã chọn
        int checkedItem = -1;
        if (currentLanguageMode.equals("vi")) {
            checkedItem = 0; // Tiếng Việt
        } else if (currentLanguageMode.equals("en")) {
            checkedItem = 1; // Tối
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(MeActivity.this);
        builder.setTitle("Chọn ngôn ngữ");
        builder.setSingleChoiceItems(languages, checkedItem, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                String newLangCode = "";

                if (which == 0) { // Tiếng Việt
                    newLangCode = "vi";
                } else if (which == 1) { // English
                    newLangCode = "en";
                }
                setLocale(MeActivity.this, newLangCode);
                editor.putString(LANGUAGE_KEY, newLangCode);
                editor.apply(); // Lưu ngôn ngữ mới


                dialog.dismiss();
                // Khởi động lại activity để áp dụng thay đổi
                recreate();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void setLocale(Context context, String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }
    private void showThemeDialog() {
        final String[] themes = {"Sáng", "Tối"};
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Lấy chế độ ban đêm hiện tại của ứng dụng
        int currentNightMode = sharedPreferences.getInt(THEME_KEY, AppCompatDelegate.MODE_NIGHT_NO);

        // Thiết lập chỉ số mặc định đã chọn
        int checkedItem = -1;
        if (currentNightMode == AppCompatDelegate.MODE_NIGHT_NO) {
            checkedItem = 0; // Sáng
        } else if (currentNightMode == AppCompatDelegate.MODE_NIGHT_YES) {
            checkedItem = 1; // Tối
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(MeActivity.this);
        builder.setTitle("Chọn giao diện");
        builder.setSingleChoiceItems(themes, checkedItem, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences.Editor editor = sharedPreferences.edit();

                switch (which) {
                    case 0: // Sáng
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                        editor.putInt(THEME_KEY, AppCompatDelegate.MODE_NIGHT_NO);

                        break;
                    case 1: // Tối
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                        editor.putInt(THEME_KEY, AppCompatDelegate.MODE_NIGHT_YES);

                        break;
                }
                editor.apply();
                dialog.dismiss();
                recreate();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
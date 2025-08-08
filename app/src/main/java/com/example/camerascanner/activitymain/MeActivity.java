package com.example.camerascanner.activitymain;

import android.content.DialogInterface;
import android.content.Intent;
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

    private BottomNavigationView bottomNavigationiew;
    private AppCompatButton btnLanguage, btnBlackWhite;

    @Override
    protected void onCreate(Bundle saveInstance){
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

        AlertDialog.Builder builder = new AlertDialog.Builder(MeActivity.this);
        builder.setTitle("Chọn ngôn ngữ");
        builder.setSingleChoiceItems(languages, -1, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) { // Tiếng Việt
                    setLocale("vi");
                } else if (which == 1) { // English
                    setLocale("en");
                }
                dialog.dismiss();
                // Khởi động lại activity để áp dụng thay đổi
                recreate();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void setLocale(String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }
    private void showThemeDialog() {
        final String[] themes = {"Sáng", "Tối"};
        AlertDialog.Builder builder = new AlertDialog.Builder(MeActivity.this);
        builder.setTitle("Chọn giao diện");
        builder.setSingleChoiceItems(themes, -1, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: // Sáng
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                        break;
                    case 1: // Tối
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                        break;
                }
                dialog.dismiss();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
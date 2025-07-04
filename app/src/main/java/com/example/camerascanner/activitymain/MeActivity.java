package com.example.camerascanner.activitymain;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.camerascanner.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class MeActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationiew;

    @Override
    protected void onCreate(Bundle saveInstance){
        super.onCreate(saveInstance);
        setContentView(R.layout.activity_me_main);

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
}
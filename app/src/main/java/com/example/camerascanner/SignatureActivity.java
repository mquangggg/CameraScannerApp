package com.example.camerascanner;


import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.camerascanner.R;
import com.example.camerascanner.SignatureView;

import java.io.File;
import java.io.FileOutputStream;

public class SignatureActivity extends AppCompatActivity {

    private SignatureView signatureView;
    private Button btnClear, btnDone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign);

        signatureView = findViewById(R.id.signatureView);
        btnClear = findViewById(R.id.btnDeleteSign);
        btnDone = findViewById(R.id.btnYesSign);

        btnClear.setOnClickListener(v -> signatureView.clear());

        btnDone.setOnClickListener(v -> {
            Bitmap signatureBitmap = getBitmapFromView(signatureView);
            Uri savedUri = saveBitmapToCache(signatureBitmap);
            if (savedUri != null) {
                Intent resultIntent = new Intent();
                resultIntent.setData(savedUri);
                setResult(RESULT_OK, resultIntent);
            }
            finish();
        });
    }

    private Bitmap getBitmapFromView(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }

    private Uri saveBitmapToCache(Bitmap bitmap) {
        try {
            File cachePath = new File(getCacheDir(), "signatures");
            cachePath.mkdirs();
            File file = new File(cachePath, "signature_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            return Uri.fromFile(file);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

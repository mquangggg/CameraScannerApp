package com.example.camerascanner.activitycamera; // Đảm bảo đúng package của bạn

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

public class AppPermissionHandler {

    private final FragmentActivity activity;
    private final PermissionCallbacks callbacks;
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;
    private ActivityResultLauncher<String> requestStoragePermissionLauncher;

    public interface PermissionCallbacks {
        void onCameraPermissionGranted();
        void onCameraPermissionDenied();
        void onStoragePermissionGranted();
        void onStoragePermissionDenied();
    }

    public AppPermissionHandler(FragmentActivity activity, PermissionCallbacks callbacks) {
        this.activity = activity;
        this.callbacks = callbacks;
        initPermissionLaunchers();
    }

    private void initPermissionLaunchers() {
        requestCameraPermissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        callbacks.onCameraPermissionGranted();
                    } else {
                        callbacks.onCameraPermissionDenied();
                    }
                });

        requestStoragePermissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        callbacks.onStoragePermissionGranted();
                    } else {
                        callbacks.onStoragePermissionDenied();
                    }
                });
    }

    // --- Xử lý quyền Camera ---
    public boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    public void requestCameraPermission() {
        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    // --- Xử lý quyền Thư viện (Storage) ---
    public boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    public void requestStoragePermission() {
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        requestStoragePermissionLauncher.launch(permission);
    }
}
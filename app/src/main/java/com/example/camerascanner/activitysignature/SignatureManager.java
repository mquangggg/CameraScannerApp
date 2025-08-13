package com.example.camerascanner.activitycamera;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SignatureManager {

    private static final String PREF_NAME = "signature_prefs";
    private static final String KEY_SIGNATURE_URIS = "signature_uris";

    private final Context context;
    private final SharedPreferences sharedPreferences;

    public SignatureManager(Context context) {
        this.context = context;
        this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Lưu URI chữ ký mới vào SharedPreferences
     */
    public void saveSignatureUri(Uri signatureUri) {
        Set<String> uriStrings = sharedPreferences.getStringSet(KEY_SIGNATURE_URIS, new HashSet<>());
        Set<String> newUriStrings = new HashSet<>(uriStrings); // Tạo bản sao để có thể chỉnh sửa
        newUriStrings.add(signatureUri.toString());

        sharedPreferences.edit()
                .putStringSet(KEY_SIGNATURE_URIS, newUriStrings)
                .apply();
    }

    /**
     * Lấy danh sách tất cả URI chữ ký đã lưu
     */
    public List<Uri> getSavedSignatureUris() {
        Set<String> uriStrings = sharedPreferences.getStringSet(KEY_SIGNATURE_URIS, new HashSet<>());
        List<Uri> uris = new ArrayList<>();

        for (String uriString : uriStrings) {
            try {
                uris.add(Uri.parse(uriString));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return uris;
    }

    /**
     * Xóa URI chữ ký khỏi SharedPreferences
     */
    public void removeSignatureUri(Uri signatureUri) {
        Set<String> uriStrings = sharedPreferences.getStringSet(KEY_SIGNATURE_URIS, new HashSet<>());
        Set<String> newUriStrings = new HashSet<>(uriStrings);
        newUriStrings.remove(signatureUri.toString());

        sharedPreferences.edit()
                .putStringSet(KEY_SIGNATURE_URIS, newUriStrings)
                .apply();
    }

    /**
     * Xóa tất cả chữ ký đã lưu
     */
    public void clearAllSignatures() {
        sharedPreferences.edit()
                .remove(KEY_SIGNATURE_URIS)
                .apply();
    }
}
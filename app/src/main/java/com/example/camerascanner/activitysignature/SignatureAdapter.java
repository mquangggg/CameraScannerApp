package com.example.camerascanner.activitycamera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.camerascanner.R;
import java.util.List;

public class SignatureAdapter extends RecyclerView.Adapter<SignatureAdapter.SignatureViewHolder> {

    private List<Uri> signatureUris;
    private final Context context;
    private final OnSignatureClickListener listener;
    private final OnSignatureDeleteListener deleteListener;

    public interface OnSignatureClickListener {
        void onSignatureClick(Uri signatureUri);
    }

    public interface OnSignatureDeleteListener {
        void onSignatureDelete(Uri signatureUri, int position);
    }

    public SignatureAdapter(Context context, List<Uri> signatureUris,
                            OnSignatureClickListener listener, OnSignatureDeleteListener deleteListener) {
        this.context = context;
        this.signatureUris = signatureUris;
        this.listener = listener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public SignatureViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_signature, parent, false);
        return new SignatureViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SignatureViewHolder holder, int position) {
        Uri signatureUri = signatureUris.get(position);

        try {
            // Load bitmap từ URI và hiển thị trong ImageView
            Bitmap bitmap = BitmapFactory.decodeStream(context.getContentResolver().openInputStream(signatureUri));
            holder.imageViewSignature.setImageBitmap(bitmap);

            // Set click listener cho chữ ký
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSignatureClick(signatureUri);
                }
            });

            // Set click listener cho nút xóa
            holder.btnDeleteSignature.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onSignatureDelete(signatureUri, position);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            // Set placeholder nếu không load được ảnh
            holder.imageViewSignature.setImageResource(R.drawable.ic_home_white);
        }
    }

    @Override
    public int getItemCount() {
        return signatureUris != null ? signatureUris.size() : 0;
    }

    public void updateSignatures(List<Uri> newSignatureUris) {
        this.signatureUris = newSignatureUris;
        notifyDataSetChanged();
    }

    public void addSignature(Uri signatureUri) {
        if (signatureUris != null) {
            signatureUris.add(signatureUri);
            notifyItemInserted(signatureUris.size() - 1);
        }
    }

    public void removeSignature(int position) {
        if (signatureUris != null && position >= 0 && position < signatureUris.size()) {
            signatureUris.remove(position);
            notifyItemRemoved(position);
        }
    }

    static class SignatureViewHolder extends RecyclerView.ViewHolder {
        ImageView imageViewSignature;
        ImageButton btnDeleteSignature;

        public SignatureViewHolder(@NonNull View itemView) {
            super(itemView);
            imageViewSignature = itemView.findViewById(R.id.imageViewSignature);
            btnDeleteSignature = itemView.findViewById(R.id.btnDeleteSignature);
        }
    }
}
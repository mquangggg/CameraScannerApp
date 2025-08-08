package com.example.camerascanner.activitysignature.signatureview.signature;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.camerascanner.R;

import java.util.List;

public class ColorAdapter extends RecyclerView.Adapter<ColorAdapter.ColorViewHolder> {
    private List<Integer> colorList; // Danh sách màu
    private Context context;
    private OnColorSelectedListener listener;
    private int selectedPosition = 0; // Vị trí màu được chọn (mặc định là màu đầu tiên)

    @NonNull
    @Override
    public ColorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.color_item,parent,false);
        return new ColorViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ColorViewHolder holder, int position) {
        int color = colorList.get(position);
        // Tạo drawable hình tròn với màu tương ứng
        GradientDrawable colorCircle = new GradientDrawable();
        colorCircle.setShape(GradientDrawable.OVAL);
        colorCircle.setColor(color);
        colorCircle.setStroke(3, Color.WHITE); // Viền trắng

        // Đặt background cho colorCircle view
        holder.colorCircle.setBackground(colorCircle);

        if(position == selectedPosition){
            holder.centerIcon.setVisibility(View.VISIBLE);
            if(isLightColor(color)){
                holder.centerIcon.setColorFilter(Color.BLACK);
            } else {
                holder.centerIcon.setColorFilter(Color.WHITE);
            }
        }else {
            holder.centerIcon.setVisibility(View.GONE);
        }
        holder.itemView.setOnClickListener(v->{
            int oldPossition = selectedPosition;
            selectedPosition = position;

            notifyItemChanged(oldPossition);
            notifyItemChanged(selectedPosition);

            if(listener != null){
                listener.onColorSelected(color);
            }
        });
    }

    @Override
    public int getItemCount() {
        return colorList.size();
    }
    /**
     * Kiểm tra xem màu có phải là màu sáng không để chọn màu icon phù hợp
     * @param color Màu cần kiểm tra
     * @return true nếu là màu sáng, false nếu là màu tối
     */
    private boolean isLightColor(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness < 0.5;
    }

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    public ColorAdapter(Context context, List<Integer> colorList , OnColorSelectedListener listener){
        this.context = context;
        this.colorList = colorList;
        this.listener = listener;
    }

    /**
     * ViewHolder cho mỗi item màu
     */

    public static class ColorViewHolder extends RecyclerView.ViewHolder {
        View colorCircle;
        ImageView centerIcon;

        public ColorViewHolder(@NonNull View itemView) {
            super(itemView);
            colorCircle = itemView.findViewById(R.id.colorCircle);
            centerIcon = itemView.findViewById(R.id.centerIcon);
        }
    }
}

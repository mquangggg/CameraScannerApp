package com.example.camerascanner.activitypdf;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileNameGenerator {

    /**
     * Tạo một tên file PDF mặc định dựa trên thời gian hiện tại.
     * Tên file sẽ có định dạng "ScannedPdf_YYYYMMDD_HHmmss.pdf".
     * Điều này giúp đảm bảo tên file là duy nhất và dễ dàng nhận biết thời điểm tạo.
     *
     * @return Một chuỗi là tên file PDF mặc định với timestamp.
     */
    public static String generateDefaultFileName() {
        // Lấy thời gian hiện tại.
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        // Kết hợp tiền tố "ScannedPdf_", timestamp và đuôi ".pdf" để tạo tên file.
        return "ScannedPdf_" + timestamp + ".pdf";
    }

    /**
     * Đảm bảo rằng tên file được cung cấp có đuôi mở rộng ".pdf".
     * Nếu tên file rỗng, null hoặc chỉ chứa khoảng trắng, một tên file mặc định sẽ được tạo và trả về.
     * Nếu tên file không kết thúc bằng ".pdf" (không phân biệt chữ hoa chữ thường),
     * thì ".pdf" sẽ được thêm vào cuối.
     *
     * @param fileName Tên file gốc cần kiểm tra và điều chỉnh.
     * @return Tên file đã được đảm bảo có đuôi ".pdf".
     */
    public static String ensurePdfExtension(String fileName) {
        // Kiểm tra nếu tên file là null, rỗng hoặc chỉ chứa khoảng trắng.
        if (fileName == null || fileName.trim().isEmpty()) {
            // Nếu có, trả về một tên file mặc định.
            return generateDefaultFileName();
        }

        // Loại bỏ khoảng trắng ở đầu và cuối tên file.
        String trimmedName = fileName.trim();
        // Kiểm tra xem tên file (chuyển sang chữ thường) có kết thúc bằng ".pdf" hay không.
        if (!trimmedName.toLowerCase().endsWith(".pdf")) {
            // Nếu không, thêm ".pdf" vào cuối tên file.
            trimmedName += ".pdf";
        }

        // Trả về tên file đã được đảm bảo có đuôi ".pdf".
        return trimmedName;
    }
}
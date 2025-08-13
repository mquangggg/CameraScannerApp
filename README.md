# Camera Scanner App

Ứng dụng quét tài liệu với AI nhận diện realtime và xử lý ảnh chuyên nghiệp.

## Tính năng

- 📷 **Camera Scanner** - Nhận diện khung tài liệu realtime bằng OpenCV
- ✂️ **Auto Crop** - Tự động crop theo khung nhận diện hoặc Google ML Kit Text
- 🔄 **Xử lý ảnh** - Xoay, crop, tạo PDF, OCR, ký tên số
- 📂 **Quản lý ảnh** - Lưu trữ và tổ chức ảnh đã quét

## Cấu trúc Activities

### MainActivity
<img src="images/main_activity.jpg" alt="Main Activity" width="250" align="left"/>

- Hiển thị danh sách ảnh đã quét
- Nút "QUÉT NGAY BÂY GIỜ" mở camera scanner
- Tìm kiếm ảnh, navigation tabs

<br clear="left"/>

### CameraScannerActivity  
<img src="images/camera_scanner.jpg" alt="Camera Scanner" width="250" align="left"/>

- **OpenCV realtime detection** - Nhận diện khung tài liệu trong thời gian thực
- **Auto crop** - Tự động cắt theo khung phát hiện
- **Fallback** - Dùng ML Kit Text Detection nếu không phát hiện được khung

<br clear="left"/>

### ImageListActivity
<img src="images/image_list.jpg" alt="Image List" width="250" align="left"/>

- Hiển thị tất cả ảnh đã quét
- Nút thêm ảnh mới (quay lại camera scanner)
- Chọn và xóa nhiều ảnh

<br clear="left"/>

### ImageEditorActivity
<img src="images/image_editor.jpg" alt="Image Editor" width="250" align="left"/>

- **Xoay ảnh** - 90°, 180°, 270° hoặc góc tùy chỉnh
- **Crop ảnh** - Cắt tự do hoặc theo tỷ lệ chuẩn
- **Tạo PDF** - Chuyển đổi ảnh thành PDF
- **OCR** - Trích xuất text bằng Google ML Kit
- **Ký tên** - Thêm chữ ký số lên ảnh

<br clear="left"/>

## Công nghệ

- **OpenCV 4.x** - Computer vision và image processing
- **Google ML Kit** - Text recognition và object detection
- **CameraX** - Camera handling
- **Android 7.0+** - Target platform

## Dependencies

```gradle
implementation 'org.opencv:opencv-android:4.8.0'
implementation 'com.google.android.gms:play-services-mlkit-text-recognition:19.0.0'
implementation 'com.google.android.gms:play-services-mlkit-object-detection:17.0.0'
```

## Permissions

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

## Flow hoạt động

1. **Camera Scanner** → OpenCV nhận diện realtime → Auto crop → Lưu ảnh
2. **Image List** → Chọn ảnh → **Image Editor** → Xoay/Crop/PDF/OCR/Ký tên

## Cấu trúc Project

```
app/src/main/java/
├── activities/          # 4 activities chính
├── opencv/             # OpenCV helpers
├── mlkit/              # ML Kit integration  
├── utils/              # PDF, Image, File utils
└── views/              # Custom views
```

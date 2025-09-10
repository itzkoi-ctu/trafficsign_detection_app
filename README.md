
<h3 align="center" font-size= 14px;><b>Trường Công nghệ thông tin và Truyền thông - ĐH Cần Thơ</b></h3>
<p align="center">
  <a href="https://www.cit.ctu.edu.vn/" title="Trường Công nghệ thông tin và Truyền thông" style="border: 5;">
    <img src="https://www.cit.ctu.edu.vn/images/Banner-Logo/Banner_06-8-2025.jpg" alt="Trường Công nghệ thông tin và Truyền thông | Collage of Information Technology and Communication">
  </a>
</p>



# 🚦 Traffic Sign Detection App

Ứng dụng Android phát hiện & nhận diện **biển báo giao thông** theo thời gian thực, tích hợp **CameraX** và **TensorFlow Lite**.  
Ứng dụng hướng tới việc chạy trên **màn hình ngang của ô tô (head-unit)** để hỗ trợ lái xe.

---

## ✨ Badges

![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue?logo=kotlin)
![Android](https://img.shields.io/badge/Android-12+-3DDC84?logo=android)
![CameraX](https://img.shields.io/badge/CameraX-Live%20Preview-orange)
![TensorFlowLite](https://img.shields.io/badge/TensorFlow%20Lite-ML-green?logo=tensorflow)
![License](https://img.shields.io/badge/License-MIT-yellow)

---

## ✨ Tính năng

- 📷 **CameraX**: Lấy hình ảnh từ camera sau (back camera) để mô phỏng **camera trước xe**.
- 🧠 **Nhận diện biển báo**: 
  - Dùng **SSD (Single Shot Detector)** để phát hiện vùng chứa biển.
  - Dùng **TFLite Classifier** để phân loại loại biển báo.
- 🔊 **Cảnh báo bằng giọng nói**: Tích hợp **Text-to-Speech (TTS)** đọc tên biển báo bằng tiếng Việt.
- 🎛 **Overlay trực quan**: Hiển thị bounding box + độ chính xác lên camera preview.
- ⏱ **Chống lặp**: Không đọc lại liên tục cùng một biển báo khi nó còn trong khung hình (debounce & rearm).
- 🚘 **Tối ưu cho màn hình xe hơi**:
  - Khóa màn hình ngang (`landscape`)
  - Tự động điều chỉnh orientation
  - Hiệu năng real-time với `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`.

---

## 📂 Cấu trúc dự án

```
app/
 ├─ java/com/example/trafficsignapp/
 │   ├─ MainActivity.kt          # Activity chính: CameraX + Analyzer
 │   ├─ ImgUtils.kt
 │   ├─ DetectionOverlay.kt      # Vẽ bounding boxes
 │   ├─ LabelTranslator.kt       # Dịch nhãn sang tiếng Việt
 │   ├─ Tf1SsdDetector.kt        # Mô hình SSD phát hiện biển báo
 │   ├─ TFLiteClassifier.kt      # Phân loại biển báo bằng TFLite
 │   └─ TFLiteSpec
 ├─ res/layout/activity_main.xml # UI: PreviewView + Overlay + debug
 ├─ assets/                      # Chứa mô hình .tflite, label.txt
 └─ ...
```

---

## ⚙️ Công nghệ sử dụng

- **Ngôn ngữ**: Kotlin  
- **Machine Learning**: TensorFlow Lite 2.x  
- **Camera**: CameraX (Preview, ImageAnalysis)  
- **Android SDK**: minSdk 26, targetSdk 34  
- **TTS**: Android TextToSpeech API  

---

## 🚀 Cách chạy dự án

### Yêu cầu
- Android Studio **Giraffe** trở lên
- Gradle 8+
- Máy thật hoặc emulator hỗ trợ CameraX
- Nếu test trên **LDPlayer/Emulator**: cần bật webcam làm **Back Camera**

### Cài đặt
```bash
git clone https://github.com/<your-username>/TrafficSignApp.git
cd TrafficSignApp
```

Mở dự án bằng Android Studio, sync Gradle và build.

### Chạy ứng dụng
- Cắm thiết bị Android (hoặc mở emulator hỗ trợ camera).
- Run app từ Android Studio.
- Cho camera hướng vào các biển báo (in giấy hoặc hiển thị trên màn hình khác).
- Nghe kết quả đọc bằng giọng nói tiếng Việt.

---

## 🧩 Các module chính

- **RealtimeAnalyzer**  
  - Nhận frame từ camera  
  - SSD detect vùng biển báo  
  - Crop & resize → Classifier  
  - Gửi kết quả lên `DetectionOverlay` và TextToSpeech  

- **Chống đọc lặp (Debounce)**  
  - Biển mới → đọc 1 lần  
  - Nếu biển vẫn trong khung hình → không đọc lại  
  - Khi biển biến mất >1.5s → cho phép đọc lại nếu gặp lại  

---

## 📷 Demo (ảnh minh họa)

![Demo App](docs/demo.png)

---

## 🔮 Hướng phát triển

- [ ] Hỗ trợ nhiều loại biển báo hơn (mô hình nhiều lớp).  
- [ ] Tối ưu model bằng **quantization** để chạy mượt trên head-unit yếu.  
- [ ] Tích hợp bản đồ (Google Maps API) để gắn cảnh báo vị trí.  
- [ ] Xuất log hành trình: thời điểm, biển báo đã gặp.  
- [ ] Hỗ trợ tiếng Anh song song với tiếng Việt.  

---

## 👨‍💻 Tác giả

- Quang Long 
- Gia Đại
- Trần Lực
- Gia Bảo
Kỹ thuật phần mềm CLC - K48
- Liên hệ: [longnguyen0397042@gmail.com](mailto:longnguyen0397042@gmail.com)

---

## 📜 Giấy phép

Dự án cho mục đích học tập & nghiên cứu. Không chịu trách nhiệm khi sử dụng trong môi trường lái xe thực tế.

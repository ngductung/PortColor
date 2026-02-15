# PortColor - Burp Suite Extension

**PortColor** là một tiện ích mở rộng mạnh mẽ dành cho Burp Suite (Montoya API) giúp tự động phân loại và đánh dấu màu sắc (highlight) các yêu cầu HTTP dựa trên **Proxy Listener Port**.

Dự án này giúp các chuyên gia bảo mật phân biệt traffic từ nhiều nguồn khác nhau (chủ yếu dùng cho android) một cách trực quan ngay trong bảng HTTP History.



## ✨ Tính năng nổi bật

* **Auto-Discovery:** Tự động phát hiện các Proxy Port đang hoạt động ngay khi có traffic đi qua. Không cần nhập thủ công.
* **Dynamic UI:** Giao diện quản lý quy tắc (Rule) trực quan nằm ngay phía trên bảng hiển thị.
* **Color Mapping:** Hỗ trợ đầy đủ các màu sắc của Burp Suite (Red, Blue, Pink, Green, Magenta, Cyan, Grey, Yellow).
* **Persistence:** Tự động lưu trữ cấu hình vào bộ nhớ của Burp Suite. Các thiết lập màu sắc và danh sách Port sẽ được giữ nguyên khi bạn khởi động lại Burp.
* **Reflection Engine:** Sử dụng kỹ thuật Reflection để truy xuất thông tin `listenerInterface`, đảm bảo tương thích tốt nhất với nhân của Burp Suite.

### Yêu cầu
* **Java 17** trở lên.
* **Burp Suite** phiên bản 2023.12 trở lên (hỗ trợ Montoya API).

## 🛠 Hướng dẫn sử dụng

1.  **Phát hiện Port:** Truy cập một vài trang web qua các Proxy Listener hiện có của bạn (ví dụ: port 8080 và 8081).
2.  **Cấu hình:**
    * Mở tab **PortColor**.
    * Chọn Port từ danh sách **Detected Port** (danh sách này tự cập nhật khi có traffic).
    * Chọn màu sắc mong muốn.
    * Nhấn **Add Rule**.
3.  **Kết quả:** Quay lại tab **Proxy** -> **HTTP History**, các yêu cầu sẽ tự động được tô màu dựa trên Port mà chúng đi qua.



## 📝 Kỹ thuật xử lý (Deep Dive)

* **Persistence:** Sử dụng `api.persistence().extensionData().setString()` và `getString()` để lưu trữ chuỗi cấu hình dạng Serialized.
* **Reflection:** Truy xuất phương thức `listenerInterface()` từ đối tượng `InterceptedRequest` để vượt qua các giới hạn định danh Port trong API chuẩn.
* **Swing Thread Safety:** Toàn bộ quá trình cập nhật giao diện (Dropdown, Table) được bọc trong `SwingUtilities.invokeLater()` để tránh gây treo hoặc lỗi giao diện Burp.

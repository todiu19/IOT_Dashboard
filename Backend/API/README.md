# Postman API Documentation

Thư mục này chứa tài liệu và bộ kiểm thử API theo định dạng Postman Collection v2.1:

- `IOT-Dashboard.postman_collection.json`: request, mô tả, ví dụ response và test.
- `IOT-Dashboard-Local.postman_environment.json`: biến môi trường local.

## Import và sử dụng

1. Mở Postman và chọn **Import**.
2. Import cả hai file JSON trong thư mục này.
3. Chọn environment **IoT Dashboard - Local**.
4. Khởi động backend tại `http://localhost:8080`.
5. Gửi request **01 - Authentication / Login** trước.
6. Gọi các request nghiệp vụ; Postman tự gửi cookie `jwt` đã lưu.
7. Gửi **99 - Session / Logout** khi hoàn tất.

Muốn chạy toàn bộ Collection ổn định mà không cần MQTT broker:

```bash
MQTT_ENABLED=false ./mvnw spring-boot:run
```

## Xuất bản tài liệu

Sau khi import Collection vào workspace Postman:

1. Mở Collection **IoT Dashboard API**.
2. Chọn **View complete documentation** để xem tài liệu được tạo từ mô tả và các example.
3. Chọn **Publish Docs** nếu cần đường dẫn công khai để nộp cùng báo cáo.

Không đưa thông tin đăng nhập hoặc URL production có dữ liệu nhạy cảm vào environment được publish.

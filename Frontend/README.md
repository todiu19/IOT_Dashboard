# IoT Dashboard Frontend (React)

Frontend dùng React + Vite. `index.html` là trang vào của Vite; giao diện và logic nằm trong `src/`. Các file `legacy-index.html`, `script1.js` và `script.js` được giữ lại để đối chiếu, nhưng **không được tải** bởi ứng dụng React.

## Chạy local

Chạy backend ở `http://localhost:8080`, rồi trong thư mục `Frontend`:

```bash
npm install
npm run dev
```

Mở `http://localhost:5173`. Backend cần cho phép origin này (`FRONTEND_ORIGIN=http://localhost:5173`). Nếu backend ở địa chỉ khác, tạo `.env.local` với:

```dotenv
VITE_API_URL=http://localhost:8080
```

`VITE_API_URL` cũng được dùng để tạo địa chỉ WebSocket `/ws`. Để tạo bản phát hành: `npm run build`; các file đầu ra ở `dist/`.

Ứng dụng đăng nhập trực tiếp bằng `POST /api/auth/login`, khôi phục đăng nhập bằng `GET /api/profile` và đăng xuất bằng `POST /api/auth/logout`. Các request gửi cookie JWT với `credentials: "include"`; frontend không gọi API CSRF hoặc gửi header CSRF. Backend đã tắt CSRF cho demo/local, cần khôi phục bảo vệ trước khi triển khai cho người dùng thật.

Trang Data Sensor và Action History gửi bộ lọc, `page` và `size` lên backend; frontend chỉ hiển thị kết quả và nút phân trang. Profile đọc từ `GET /api/profile` và không có chức năng chỉnh sửa.

Dữ liệu cảm biến chỉ nhận từ ESP32 qua MQTT; không có chức năng thêm dữ liệu thủ công trên FE hoặc endpoint `POST /api/data-sensor` trên BE. API `GET /api/data-sensor` vẫn dùng để xem, lọc và phân trang dữ liệu đã lưu.

Bộ lọc thời gian của hai trang nhận tiền tố theo dạng `YYYY-MM-DD HH:mm:ss`: có thể nhập `2026`, `2026-10`, `2026-10-06 22:46` hoặc đủ đến giây. FE gửi chuỗi đó bằng query parameter `time`, BE lọc và phân trang theo khoảng thời gian tương ứng.

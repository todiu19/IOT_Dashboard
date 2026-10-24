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

Action History hiển thị cả lần bật/tắt tự động của LED 4, với thiết bị **Cảnh báo** và người thực hiện **Hệ thống** (`username: iot-system`). Mỗi chuyển trạng thái ESP32 xác nhận có một dòng ON hoặc OFF; ACK trùng không thêm dòng. Có thể chọn **Cảnh báo** trong bộ lọc thiết bị; lịch sử tải lại qua `/topic/status` như các thiết bị khác.

Dashboard có hai biểu đồ cảm biến cạnh nhau và biểu đồ **Số lần bật cảnh báo** nằm bên dưới, rộng toàn hàng. Trên màn hình nhỏ, các biểu đồ xếp thành một cột. Chọn tháng bằng ô tháng để xem một cột số lần bật cho từng ngày, tổng số lần bật của tháng và tooltip khi trỏ vào ngày. Dữ liệu lấy từ `GET /api/dashboard/alert-statistics?month=YYYY-MM` qua các trường `totalOn` và `days[].onCount`; tự tải lại khi LED 4 đổi trạng thái hoặc WebSocket kết nối lại. Chỉ đếm các chuyển trạng thái do ESP32 xác nhận, không đếm reading vượt ngưỡng liên tiếp hay ACK trùng. Tháng chưa có lần bật hiển thị 0; không suy diễn lịch sử từ dữ liệu cảm biến cũ. Backend và Action History vẫn lưu cả ON/OFF.

Dữ liệu cảm biến chỉ nhận từ ESP32 qua MQTT; không có chức năng thêm dữ liệu thủ công trên FE hoặc endpoint `POST /api/data-sensor` trên BE. API `GET /api/data-sensor` vẫn dùng để xem, lọc và phân trang dữ liệu đã lưu.

Trên dashboard, thiết bị ID `4` hiển thị là **Cảnh báo**, chỉ hiển thị trạng thái và không có nút bật/tắt. Backend tự quyết định ON/OFF theo ngưỡng ánh sáng. Khi trạng thái thiết bị là `ON`, viền ô nhấp nháy đỏ và hiển thị “Đang cảnh báo”, nền giữ nguyên; `OFF` dừng nhấp nháy và trở lại viền bình thường. Trạng thái lấy từ API dashboard và cập nhật qua WebSocket `/topic/devices`, dựa trên phản hồi MQTT của ESP32. Chu kỳ hiệu ứng trên giao diện là 0,6 giây, không đồng bộ chính xác nhịp nháy phần cứng. Nếu người dùng bật giảm chuyển động, ô giữ viền đỏ nhưng không nhấp nháy. Database cần có thiết bị ID `4` để dashboard trả về ô này.

Bộ lọc thời gian của hai trang nhận tiền tố theo dạng `YYYY-MM-DD HH:mm:ss`: có thể nhập `2026`, `2026-10`, `2026-10-06 22:46` hoặc đủ đến giây. Với bộ lọc riêng theo thời gian, FE gửi chuỗi đó bằng query parameter `time`, BE lọc và phân trang theo khoảng thời gian tương ứng.

Data Sensor dùng một ô nhập chung. Chọn **Tất cả** sẽ tìm theo tên cảm biến, giá trị hoặc tiền tố thời gian (qua `search`); không cần chọn **Theo thời gian** để tìm ngày giờ. Option **Theo thời gian** giới hạn tìm kiếm vào thời gian và gửi tham số `time`. Đổi option chỉ thay phạm vi của lần tìm tiếp theo, giữ nguyên nội dung ô nhập, kết quả và trang hiện tại. Bộ lọc được áp dụng khi nhập/xóa nội dung (chờ 300 ms) hoặc nhấn Enter/nút **Tìm kiếm**; lúc đó quay về trang 1. Phân trang tiếp tục dùng bộ lọc đã áp dụng, không dùng option vừa chọn nhưng chưa tìm kiếm.

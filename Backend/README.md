# IoT Dashboard Backend

Backend Java 21 + Spring Boot cho hệ thống trong báo cáo: PostgreSQL, JWT trong cookie, MQTT và WebSocket.

## Cấu trúc mã nguồn

`src/main/java/com/iot` chia `controller`/`service` theo tính năng và tập trung mô hình dữ liệu trong `domain`:

```text
com/iot/
├── auth/         # đăng nhập, JWT, người dùng
├── dashboard/    # dữ liệu tổng hợp dashboard
├── device/       # thiết bị và điều khiển
├── sensor/       # cảm biến và dữ liệu đo
├── history/      # lịch sử thao tác
├── profile/      # hồ sơ người dùng
├── domain/
│   ├── entity/        # toàn bộ JPA entity
│   └── repository/    # toàn bộ Spring Data repository
├── common/
│   ├── dto/ApiResponse.java
│   ├── dto/request/   # dữ liệu đầu vào API
│   ├── dto/response/  # dữ liệu trả về API
│   ├── enums/
│   └── exception/     # ApiException, ErrorCode, xử lý lỗi tập trung
├── config/       # cấu hình Spring, bảo mật, MQTT
└── mqtt/         # giao tiếp MQTT
```

Mỗi tính năng có `controller` và `service`; entity và repository nằm chung trong `domain`. Không dùng Flyway; JPA/Hibernate tạo và cập nhật bảng từ các `entity` (`ddl-auto: update`). Spring không tự chạy `dataseed.sql`; file này được chạy thủ công khi cần.

## Chạy local

Yêu cầu: Java 21 và PostgreSQL đang chạy.

Nếu muốn dùng dữ liệu mẫu, chạy `dataseed.sql` **một lần trên PostgreSQL trống, trước khi khởi động backend**. File có lệnh `CREATE TABLE` cho cả 6 bảng, nên không chạy lại trên database đã có bảng. Ví dụ từ thư mục `Backend`:

```bash
psql -h localhost -U postgres -d iot -f dataseed.sql
```

Các bài test dùng `src/test/resources/test-seed.sql` riêng; không chạy script thủ công trên database test. Sau khi chuẩn bị database, khởi động backend:

```bash
./mvnw spring-boot:run
```

API chạy tại `http://localhost:8080`. Nếu đã chạy script thủ công, dữ liệu mẫu gồm:

- Username: `admin`
- Password: `admin123`
- 3 sensor và 3 thiết bị LED

MQTT mặc định bật; có thể tắt khi không có broker:

```bash
MQTT_ENABLED=false ./mvnw spring-boot:run
```

## Swagger / OpenAPI

Sau khi backend chạy, tài liệu API có tại:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

Các đường dẫn tài liệu được mở không cần đăng nhập. Để thử API nghiệp vụ ngay trong Swagger UI, gọi `POST /api/auth/login` trước; trình duyệt sẽ lưu cookie `jwt` và tự gửi cookie trong các request tiếp theo.

## Postman

Bộ tài liệu và kiểm thử Postman nằm trong thư mục `postman/`:

- `postman/IOT-Dashboard.postman_collection.json`
- `postman/IOT-Dashboard-Local.postman_environment.json`

Import cả hai file, chọn environment **IoT Dashboard - Local**, sau đó chạy request **Login** trước. Collection chứa mô tả endpoint, query/path parameters, request body, response mẫu và test tự động. Xem hướng dẫn chi tiết tại `postman/README.md`.

## Xác thực

Backend dùng JWT trong cookie `jwt` (HttpOnly); frontend gửi cookie bằng `credentials: "include"`. CSRF đã tắt cho app demo: login, logout và các request thay đổi dữ liệu không cần token CSRF. Endpoint `/api/auth/csrf` đã được bỏ.

Lưu ý: cấu hình tắt CSRF với JWT trong cookie chỉ dành cho demo/local. Cần khôi phục bảo vệ CSRF hoặc thiết kế lại cách xác thực trước khi triển khai cho người dùng thật. Xác thực JWT, mã hóa mật khẩu BCrypt và CORS vẫn được giữ nguyên.

## API

Base URL mặc định: `http://localhost:8080`. Các endpoint JSON trả về `ApiResponse<T>`; trường `null` được lược bỏ.

Thành công:

```json
{"code":0,"data":{"id":1,"username":"admin","fullname":"Nguyễn Đình Tô","email":"admin@example.com"}}
```

Lỗi giữ HTTP status phù hợp và có `code`, `errorCode`, `message`:

```json
{"code":401,"errorCode":"INVALID_CREDENTIALS","message":"Tên đăng nhập hoặc mật khẩu không đúng."}
```

Các API nghiệp vụ vẫn yêu cầu JWT hợp lệ; thiếu hoặc sai JWT trả HTTP 401. Frontend dùng cookie `jwt`; backend cũng chấp nhận header `Authorization: Bearer <token>`. Không cần header `X-XSRF-TOKEN`.

### Xác thực

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| POST | `/api/auth/login` | Đăng nhập, response đặt JWT cookie `jwt` |
| POST | `/api/auth/logout` | Xóa JWT cookie; trả HTTP 200 với `ApiResponse` |

Request đăng nhập:

```json
{"username":"admin","password":"admin123"}
```

Ví dụ đăng nhập trực tiếp bằng `curl` (cookie JWT được giữ trong `cookies.txt`):

```bash
curl -c cookies.txt \
	-H 'Content-Type: application/json' \
	-d '{"username":"admin","password":"admin123"}' \
	http://localhost:8080/api/auth/login
```

Sau đăng nhập, dùng `-b cookies.txt` cho các request cần xác thực. Endpoint `/api/auth/login`, `/api/auth/logout` và `/actuator/health` được mở khỏi xác thực ở security filter. Khi mở hoặc tải lại trang, frontend gọi `GET /api/profile` để khôi phục trạng thái đăng nhập và lưu hồ sơ cho trang Profile; JWT thiếu hoặc hết hạn trả HTTP 401.

Trong Postman, gửi `POST /api/auth/login` với JSON trên; giữ bật cookie jar để Postman lưu và gửi cookie `jwt` khi gọi API khác. Gửi `POST /api/auth/logout` để xóa cookie đăng nhập. Bỏ script lấy/gửi CSRF và header `X-XSRF-TOKEN` đã cấu hình trước đó.

### Dashboard và thiết bị

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| GET | `/api/dashboard` | Sensor mới nhất, dữ liệu biểu đồ, danh sách thiết bị |
| POST | `/api/device/{id}/control` | Gửi lệnh `ON` hoặc `OFF` cho thiết bị |

Dashboard trả về `data.latestReadings`, `data.chartData`, `data.devices` và `data.pendingControls` (các lệnh đang `LOADING`, để khôi phục giao diện khi tải lại trang). Một reading có dạng:

```json
{"id":10,"sensorId":1,"sensorName":"Nhiệt độ (°C)","value":30.5,"recordedAt":"2026-10-07T01:18:42"}
```

Thiết bị có dạng `{"id":1,"name":"Led 1","status":"OFF"}`. Gửi lệnh điều khiển:

```http
POST /api/device/1/control
Content-Type: application/json
Cookie: jwt=<jwt-cookie>
```

```json
{"action":"ON"}
```

`action` chỉ nhận `ON` hoặc `OFF`. Response `data` gồm `historyId`, `deviceId`, `deviceName`, `action`, `status`, `performedAt`. Khi MQTT bật, `status` ban đầu là `LOADING`; thiết bị cần phản hồi qua MQTT để backend cập nhật thành `ON`/`OFF`. Nếu không nhận phản hồi trong thời gian cấu hình (`app.mqtt.control-timeout`, mặc định `30s`), trạng thái lịch sử thành `FAILED`.

### Sensor

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| GET | `/api/data-sensor` | Tìm kiếm/phân trang dữ liệu sensor |

Query của `GET /api/data-sensor`:

| Tham số | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `search` | Không lọc | Tên sensor gần đúng, giá trị số chính xác hoặc tiền tố thời gian `YYYY-MM-DD HH:mm:ss` (kết hợp OR) |
| `sensor` | Không lọc | Tên sensor chính xác, ví dụ `Nhiệt độ (°C)` |
| `time` | Không lọc | Prefix thời gian, từ `YYYY` đến `YYYY-MM-DD HH:mm:ss` |
| `page` | `0` | Trang bắt đầu từ 0 |
| `size` | `7` | Số dòng mỗi trang, tối đa 100 |

Lọc thời gian chỉ dùng `time`: nhập `2026` để lọc năm, `2026-10` để lọc tháng, `2026-10-06` để lọc ngày, hoặc nhập tiếp giờ/phút/giây. Tham số `date` đã được bỏ; request cũ cần đổi `date=YYYY-MM-DD` thành `time=YYYY-MM-DD`.

Dữ liệu cảm biến chỉ được nhận từ ESP32 qua MQTT topic `sensor_data`; không có API HTTP thêm dữ liệu thủ công. Luồng MQTT vẫn lưu reading vào database và gửi WebSocket `/topic/sensors`. Gửi `POST /api/data-sensor` khi đã đăng nhập trả HTTP 405 `METHOD_NOT_ALLOWED`.

Response GET phân trang có `content`, `page`, `size`, `totalElements`, `totalPages`.

### Lịch sử và hồ sơ

| Method | Endpoint | Mô tả |
| --- | --- | --- |
| GET | `/api/action-history` | Tìm kiếm/phân trang lịch sử điều khiển |
| GET | `/api/profile` | Lấy hồ sơ người dùng hiện tại, đồng thời kiểm tra/khôi phục trạng thái đăng nhập; cần JWT |

`GET /api/action-history` nhận các query `deviceId`, `action` (`ON`/`OFF`), `status` (`LOADING`, `ON`, `OFF`, `FAILED`), `time` (prefix thời gian), `page` (mặc định `0`) và `size` (mặc định `7`, tối đa `100`). Bộ lọc `time` và response phân trang dùng cùng cách xử lý như Data Sensor; không còn tham số `date` hoặc `search`. Chọn thiết bị bằng `deviceId` thay vì tìm theo tên; `search` của Data Sensor vẫn được giữ nguyên.

### Health check

`GET /actuator/health` trả health status của Spring Actuator, không bọc trong `ApiResponse`.

### MQTT và WebSocket

| Hướng | Topic | Payload |
| --- | --- | --- |
| ESP32 → Backend | `sensor_data` | `{"temperature":30.50,"humidity":47.00,"light":1370}` |
| Backend → ESP32 | `control_device` | `{"deviceId":1,"command":"ON"}` |
| ESP32 → Backend | `status` | `{"deviceId":3,"status":"OFF"}` |

LED ID `4` là đèn cảnh báo ánh sáng, chỉ do backend tự điều khiển; gọi API điều khiển thủ công cho ID `4` trả HTTP 400 `INVALID_REQUEST`. Khi nhận reading của sensor `Độ sáng (lux)`, backend tự gửi `{"deviceId":4,"command":"ON"}` tới `control_device` nếu giá trị **lớn hơn hoặc bằng `4000`** và chưa bật cảnh báo. Chỉ gửi `{"deviceId":4,"command":"OFF"}` nếu giá trị nhỏ hơn `4000` và trước đó đã gửi `ON` thành công. Bình thường dưới ngưỡng không gửi lệnh. Ngưỡng áp dụng trên giá trị đã chuyển đổi `4095 - light` (giống giá trị lưu trong database và hiển thị trên dashboard); payload dạng `sensorId`/`value` dùng trực tiếp `value`.

Database cần có thiết bị ID `4`. ESP32 cần xử lý `ON` của LED 4 là bắt đầu nhấp nháy liên tục, `OFF` là dừng nháy và tắt LED; trả `{"deviceId":4,"status":"ON"}` hoặc `OFF` vào topic `status` theo trạng thái cảnh báo (không theo từng nhịp sáng/tắt của LED).

Thống kê cảnh báo: `GET /api/dashboard/alert-statistics?month=2026-10` (cần đăng nhập) trả `month`, `totalOn`, `totalOff`, `days` gồm `{date,onCount,offCount}` cho tất cả ngày của tháng, kể cả ngày không có sự kiện. Thống kê được truy vấn trực tiếp từ bảng `action_history` với `deviceId = 4` và trạng thái `ON`/`OFF`. Mỗi ACK LED 4 chuyển trạng thái khác trạng thái đã lưu tạo một dòng trong bảng `action_history` với tài khoản `iot-system` (Hệ thống), cùng transaction và khóa thiết bị với cập nhật `devices.status`; ACK trùng không tăng số lần. Thời gian ghi nhận theo giờ local của backend, giữ nguyên khi nhóm ngày trong SQL. Dữ liệu tồn tại qua restart.

Đồng thời mỗi lần LED 4 đổi trạng thái được xác nhận sẽ lưu một dòng `action_history` với action/status `ON` hoặc `OFF`. Tài khoản tự động `iot-system` (tên hiển thị **Hệ thống**) được tạo khi cần, có mật khẩu ngẫu nhiên được băm bằng password encoder hiện tại; không gán thao tác tự động cho admin đang đăng nhập. API `/api/action-history` trả các dòng này như lịch sử thiết bị khác; WebSocket `/topic/status` thông báo sau commit để frontend tải lại Action History. Không tạo dòng khi ACK trùng hoặc chỉ mới publish mà chưa nhận xác nhận. Không tự bổ sung các đợt cũ vào Action History.

Mỗi reading ánh sáng được kiểm tra ngay; backend chỉ publish khi trạng thái cảnh báo cần thay đổi: `cao, cao, cao → ON` một lần; sau đó `thấp, thấp → OFF` một lần; cao trở lại mới gửi `ON` của đợt tiếp theo. Khi backend khởi động, trạng thái theo dõi ban đầu là `OFF`, nên reading dưới ngưỡng không gửi gì. Trạng thái chống gửi trùng lưu trong bộ nhớ, không phải bộ đếm lưu DB. Nếu publish ném lỗi, backend ghi log và thử lại ở reading tiếp theo. Hàm publish dùng trong callback không gọi `waitForCompletion` để tránh chặn luồng MQTT; gửi thành công ở đây nghĩa là lệnh đã được Paho tiếp nhận, chưa phải xác nhận của ESP32. Chức năng chỉ chạy khi MQTT bật. Lệnh tự động gửi trực tiếp qua MQTT; lịch sử, thống kê và trạng thái thiết bị được cập nhật khi nhận ACK chuyển trạng thái từ ESP32.

Khi MQTT bật, lệnh điều khiển có trạng thái `LOADING`. Nếu không nhận phản hồi trong 30 giây, lịch sử chuyển thành `FAILED`.

WebSocket dùng STOMP endpoint `/ws`:

- `/topic/sensors`: dữ liệu cảm biến mới
- `/topic/devices`: trạng thái thiết bị mới
- `/topic/status`: tiến trình lệnh điều khiển `LOADING` → `ON`/`OFF` hoặc `FAILED`

Payload `/topic/status` giống `ControlResponse`:

```json
{"historyId":42,"deviceId":1,"deviceName":"LED 1","action":"ON","status":"LOADING","performedAt":"2026-10-07T12:00:00"}
```

Backend lưu lịch sử `LOADING` trước khi gửi MQTT. Khi nhận phản hồi trên MQTT `status`, backend cập nhật `devices.status` và lịch sử lệnh, rồi gửi WebSocket sau khi transaction commit. Không có phản hồi trong `app.mqtt.control-timeout` (30 giây) thì lịch sử thành `FAILED`; trạng thái ON/OFF đã xác nhận của thiết bị được giữ nguyên. Phản hồi đến sau timeout chỉ cập nhật trạng thái thực của thiết bị, không đổi lịch sử đã `FAILED`.

Frontend giữ công tắc chờ đến khi có kết quả và subscribe `/topic/status` để tải lại Action History theo trang/bộ lọc hiện tại. Sau reconnect, frontend đồng bộ lại dashboard và history. Backend khôi phục timer cho lệnh còn chờ khi khởi động lại; lệnh đã quá 30 giây sẽ được xử lý timeout, không chờ thêm 30 giây nữa. Mỗi LED chỉ nhận một lệnh đang chờ tại một thời điểm; gửi lệnh thứ hai trả HTTP 409 `DEVICE_BUSY`. Với giao thức MQTT chỉ có `deviceId`/`status` (không có mã lệnh), không thể phân biệt hoàn toàn ACK cũ tới muộn với ACK của lệnh mới trên cùng LED.

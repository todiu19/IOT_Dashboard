-- =============================================================================
-- HỆ THỐNG IOT DASHBOARD - DATABASE SCHEMA, SEED DATA & TRUY VẤN THƯỜNG GẶP
-- DBMS: PostgreSQL 14+ (tương thích H2 PostgreSQL Mode)
-- Sinh viên: Nguyễn Đình Tô - MSV: B23DCCN827
-- =============================================================================

-- =============================================================================
-- PHẦN 1: TẠO BẢNG (DDL)
-- =============================================================================

-- 1. BẢNG USERS (Người dùng hệ thống)
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    fullname VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    msv VARCHAR(50) NOT NULL,
    email VARCHAR(255),
    github VARCHAR(255),
    postman VARCHAR(255),
    figma VARCHAR(255),
    pdf VARCHAR(255)
);

-- 2. BẢNG SENSORS (Danh mục cảm biến)
CREATE TABLE sensors (
    id SERIAL PRIMARY KEY,
    sensorname VARCHAR(255) NOT NULL
);

-- 3. BẢNG DEVICES (Danh mục thiết bị điều khiển)
CREATE TABLE devices (
    id SERIAL PRIMARY KEY,
    devicename VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL
);

-- 4. BẢNG DATA_SENSOR (Dữ liệu đo từ cảm biến gửi về qua MQTT)
CREATE TABLE data_sensor (
    id SERIAL PRIMARY KEY,
    idsensor INTEGER NOT NULL,
    "value" DECIMAL(10, 2) NOT NULL,
    "datetime" TIMESTAMP NOT NULL,

    CONSTRAINT fk_data_sensor_sensor
        FOREIGN KEY (idsensor)
        REFERENCES sensors(id)
);

-- 5. BẢNG ACTION_HISTORY (Lịch sử điều khiển thiết bị & cảnh báo tự động)
CREATE TABLE action_history (
    id SERIAL PRIMARY KEY,
    iddevice INTEGER NOT NULL REFERENCES devices(id),
    iduser INTEGER NOT NULL REFERENCES users(id),
    action VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    "time" TIMESTAMP NOT NULL
);

-- =============================================================================
-- PHẦN 2: CHỈ MỤC (INDEXES) - TỐI ƯU HIỆU NĂNG TRUY VẤN
-- =============================================================================

-- Tăng tốc truy vấn lọc theo loại cảm biến và sắp xếp/tìm kiếm theo thời gian
CREATE INDEX idx_data_sensor_sensor ON data_sensor(idsensor);
CREATE INDEX idx_data_sensor_time ON data_sensor("datetime" DESC);

-- Tăng tốc tìm kiếm lịch sử bật tắt và thống kê cảnh báo theo thiết bị & thời gian
CREATE INDEX idx_action_history_device ON action_history(iddevice);
CREATE INDEX idx_action_history_time ON action_history("time" DESC);

-- =============================================================================
-- PHẦN 3: DỮ LIỆU MẪU BAN ĐẦU (SEED DATA)
-- =============================================================================

-- 1. Thêm tài khoản Admin mặc định
INSERT INTO users (fullname, username, password, msv, email, github, postman, figma, pdf)
SELECT 'Nguyễn Đình Tô', 'admin',
       '$2a$10$cYA3x2E3inz.e2E.hoCj6O.XHtw/4cuL.DYwNIYhbbiYwyleoKdj2',
       'B23DCCN827',
       'tonguyen191224@gmail.com',
       'https://github.com/tota-19',
       'https://documenter.getpostman.com/',
       'https://www.figma.com/',
       'https://docs.google.com/document/d/1uC1i10ORd-40ut9SUd3Pt-BTMLrsdlPXRvYqLH1cQwo'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

-- 2. Thêm 3 loại cảm biến: Nhiệt độ, Độ ẩm, Ánh sáng
INSERT INTO sensors (sensorname)
SELECT seed.name
FROM (VALUES ('Nhiệt độ (°C)'), ('Độ ẩm (%)'), ('Độ sáng (lux)')) AS seed(name)
WHERE NOT EXISTS (SELECT 1 FROM sensors);

-- 3. Thêm các thiết bị ban đầu
INSERT INTO devices (devicename, status)
SELECT seed.name, seed.state
FROM (VALUES ('Led 1', 'ON'), ('Led 2', 'ON'), ('Led 3', 'OFF')) AS seed(name, state)
WHERE NOT EXISTS (SELECT 1 FROM devices);

-- 4. Thêm 30 bản ghi mẫu cho dữ liệu cảm biến
INSERT INTO data_sensor (idsensor, "value", "datetime")
SELECT sensor.id,
       CASE sensor.sensorname
           WHEN 'Nhiệt độ (°C)' THEN sample.temperature
           WHEN 'Độ ẩm (%)' THEN sample.humidity
           WHEN 'Độ sáng (lux)' THEN sample.light
       END,
       CURRENT_TIMESTAMP - sample.n * INTERVAL '20' SECOND
FROM sensors AS sensor
CROSS JOIN (VALUES
    (0, 27.30, 52.00, 243.20),
    (1, 27.64, 52.59, 246.57),
    (2, 27.66, 52.64, 246.84),
    (3, 27.36, 52.10, 243.76),
    (4, 27.00, 51.47, 240.17),
    (5, 26.92, 51.33, 239.36),
    (6, 27.19, 51.80, 242.08),
    (7, 27.56, 52.46, 245.83),
    (8, 27.70, 52.69, 247.16),
    (9, 27.46, 52.29, 244.85)
) AS sample(n, temperature, humidity, light)
WHERE sensor.sensorname IN ('Nhiệt độ (°C)', 'Độ ẩm (%)', 'Độ sáng (lux)')
  AND NOT EXISTS (
      SELECT 1 FROM data_sensor AS existing WHERE existing.idsensor = sensor.id
  );

-- =============================================================================
-- PHẦN 4: CÁC CÂU LỆNH TRUY VẤN SQL QUAN TRỌNG (DÙNG TRẢ LỜI VẤN ĐÁP / PHẢN BIỆN)
-- =============================================================================

/*
--------------------------------------------------------------------------------
CÂU 1: Lấy giá trị đo mới nhất của từng loại cảm biến (Hiển thị lên Card Dashboard)
- Cách 1: Sử dụng cú pháp PostgreSQL chuẩn với DISTINCT ON (tối ưu nhất):
--------------------------------------------------------------------------------
SELECT DISTINCT ON (s.id)
    s.id AS sensor_id,
    s.sensorname,
    ds.value,
    ds.datetime
FROM sensors s
JOIN data_sensor ds ON s.id = ds.idsensor
ORDER BY s.id, ds.datetime DESC;

- Cách 2: Sử dụng Window Function ROW_NUMBER() (chuẩn ANSI SQL, chạy được trên mọi DBMS):
--------------------------------------------------------------------------------
WITH ranked_data AS (
    SELECT 
        s.sensorname,
        ds.value,
        ds.datetime,
        ROW_NUMBER() OVER (PARTITION BY ds.idsensor ORDER BY ds.datetime DESC) AS rn
    FROM data_sensor ds
    JOIN sensors s ON ds.idsensor = s.id
)
SELECT sensorname, value, datetime
FROM ranked_data
WHERE rn = 1;

--------------------------------------------------------------------------------
CÂU 2: Thống kê số lần Bật cảnh báo (LED 4) theo từng ngày trong một tháng cụ thể
(Tương ứng với API GET /api/dashboard/alert-statistics?month=2026-10 trên biểu đồ)
--------------------------------------------------------------------------------
SELECT 
    EXTRACT(DAY FROM ah.time) AS ngay,
    COUNT(*) AS so_lan_bat_canh_bao
FROM action_history ah
WHERE ah.iddevice = 4
  AND ah.status = 'ON'
  AND ah.time >= '2026-10-01 00:00:00'
  AND ah.time <  '2026-11-01 00:00:00'
GROUP BY EXTRACT(DAY FROM ah.time)
ORDER BY ngay ASC;

--------------------------------------------------------------------------------
CÂU 3: Tìm kiếm dữ liệu cảm biến kết hợp Phân trang (Pagination)
(Tương ứng với trang Lịch sử cảm biến /api/sensors/search)
--------------------------------------------------------------------------------
-- Tìm kiếm các bản ghi có giá trị hoặc thời gian chứa chuỗi '27' với trang 1 (kích thước 10):
SELECT 
    ds.id,
    s.sensorname,
    ds.value,
    ds.datetime
FROM data_sensor ds
JOIN sensors s ON ds.idsensor = s.id
WHERE CAST(ds.value AS TEXT) LIKE '%27%'
   OR TO_CHAR(ds.datetime, 'YYYY-MM-DD HH24:MI:SS') LIKE '%27%'
ORDER BY ds.datetime DESC
LIMIT 10 OFFSET 0;

--------------------------------------------------------------------------------
CÂU 4: Thống kê Nhiệt độ/Độ ẩm/Ánh sáng Cao nhất (MAX), Thấp nhất (MIN), Trung bình (AVG)
trong ngày hôm nay
--------------------------------------------------------------------------------
SELECT 
    s.sensorname,
    ROUND(AVG(ds.value), 2) AS gia_tri_trung_binh,
    MAX(ds.value)           AS gia_tri_cao_nhat,
    MIN(ds.value)           AS gia_tri_thap_nhat,
    COUNT(ds.id)            AS tong_so_mau_do
FROM data_sensor ds
JOIN sensors s ON ds.idsensor = s.id
WHERE ds.datetime >= CURRENT_DATE
  AND ds.datetime <  CURRENT_DATE + INTERVAL '1 day'
GROUP BY s.id, s.sensorname;

--------------------------------------------------------------------------------
CÂU 5: Lấy lịch sử điều khiển thiết bị (JOIN 3 bảng: action_history, devices, users)
(Tương ứng với trang Action History /api/devices/history)
--------------------------------------------------------------------------------
SELECT 
    ah.id,
    d.devicename        AS ten_thiet_bi,
    u.fullname          AS nguoi_thuc_hien,
    u.username,
    ah.action           AS hanh_dong_yeu_cau,
    ah.status           AS trang_thai_ket_qua,
    ah.time             AS thoi_gian
FROM action_history ah
JOIN devices d ON ah.iddevice = d.id
JOIN users u   ON ah.iduser = u.id
ORDER BY ah.time DESC
LIMIT 20;

--------------------------------------------------------------------------------
CÂU 6: Thống kê số lần bật/tắt của từng thiết bị trong hệ thống
--------------------------------------------------------------------------------
SELECT 
    d.id,
    d.devicename,
    COUNT(CASE WHEN ah.status = 'ON' THEN 1 END)  AS so_lan_bat,
    COUNT(CASE WHEN ah.status = 'OFF' THEN 1 END) AS so_lan_tat,
    COUNT(ah.id)                                  AS tong_so_lan_thao_tac
FROM devices d
LEFT JOIN action_history ah ON d.id = ah.iddevice
GROUP BY d.id, d.devicename
ORDER BY d.id ASC;

--------------------------------------------------------------------------------
CÂU 7: Tìm kiếm những lần vượt ngưỡng cảnh báo (Lux >= 4000)
--------------------------------------------------------------------------------
SELECT 
    ds.id,
    s.sensorname,
    ds.value AS lux_reading,
    ds.datetime
FROM data_sensor ds
JOIN sensors s ON ds.idsensor = s.id
WHERE s.sensorname LIKE '%lux%'
  AND ds.value >= 4000.00
ORDER BY ds.datetime DESC;
*/

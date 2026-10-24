-- =========================
-- 1. BẢNG USERS
-- =========================
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


-- =========================
-- 2. BẢNG SENSORS
-- =========================
CREATE TABLE sensors (
    id SERIAL PRIMARY KEY,
    sensorname VARCHAR(255) NOT NULL
);


-- =========================
-- 3. BẢNG DEVICES
-- =========================
CREATE TABLE devices (
    id SERIAL PRIMARY KEY,
    devicename VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL
);


-- =========================
-- 4. BẢNG DATA_SENSOR
-- =========================
CREATE TABLE data_sensor (
    id SERIAL PRIMARY KEY,
    idsensor INTEGER NOT NULL,
    "value" DECIMAL(10, 2) NOT NULL,
    "datetime" TIMESTAMP NOT NULL,

    CONSTRAINT fk_data_sensor_sensor
        FOREIGN KEY (idsensor)
        REFERENCES sensors(id)
);

-- =========================
-- 5. BẢNG ACTION_HISTORY
-- =========================
CREATE TABLE action_history (
    id SERIAL PRIMARY KEY,
    iddevice INTEGER NOT NULL REFERENCES devices(id),
    iduser INTEGER NOT NULL REFERENCES users(id),
    action VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    "time" TIMESTAMP NOT NULL
);


-- =========================
-- 6. LỊCH SỬ CẢNH BÁO TỰ ĐỘNG
-- =========================
CREATE TABLE alert_history (
    id SERIAL PRIMARY KEY,
    state VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_alert_history_time ON alert_history(occurred_at);

-- =========================
-- INDEX
-- =========================
CREATE INDEX idx_data_sensor_sensor
ON data_sensor(idsensor);

CREATE INDEX idx_data_sensor_time
ON data_sensor("datetime");

-- Chỉ thêm tài khoản mặc định khi chưa có người dùng nào.
-- Đổi mật khẩu admin123 sau lần đăng nhập đầu tiên nếu triển khai thực tế.
INSERT INTO users (fullname, username, password, msv, email, github, postman, figma, pdf)
SELECT 'Nguyễn Đình Tô', 'admin',
       '$2a$10$cYA3x2E3inz.e2E.hoCj6O.XHtw/4cuL.DYwNIYhbbiYwyleoKdj2',
       'B23DCCN827',
       'tonguyen191224@gmail.com',
       'https://github.com/tota-19',
       'https://documenter.getpostman.com/',
       'https://www.figma.com/',
       'https://docs.google.com/document/d/1uC1i10ORd-40ut9SUd3Pt-BTMLrsdlPXRvYqLH1cQwo'
WHERE NOT EXISTS (SELECT 1 FROM users);

INSERT INTO sensors (sensorname)
SELECT seed.name
FROM (VALUES ('Nhiệt độ (°C)'), ('Độ ẩm (%)'), ('Độ sáng (lux)')) AS seed(name)
WHERE NOT EXISTS (SELECT 1 FROM sensors);

INSERT INTO devices (devicename, status)
SELECT seed.name, seed.state
FROM (VALUES ('Led 1', 'ON'), ('Led 2', 'ON'), ('Led 3', 'OFF')) AS seed(name, state)
WHERE NOT EXISTS (SELECT 1 FROM devices);

-- Mỗi cảm biến chỉ nhận 10 mẫu ban đầu nếu chưa có dữ liệu đo.
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

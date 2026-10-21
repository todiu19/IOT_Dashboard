INSERT INTO users (fullname, username, password, msv, email)
VALUES ('Test Admin', 'admin',
        '$2a$10$cYA3x2E3inz.e2E.hoCj6O.XHtw/4cuL.DYwNIYhbbiYwyleoKdj2',
        'TEST001',
        'admin@example.com');

INSERT INTO sensors (sensorname)
VALUES ('Nhiệt độ (°C)'), ('Độ ẩm (%)'), ('Độ sáng (lux)');

INSERT INTO devices (devicename, status)
VALUES ('Led 1', 'ON'), ('Led 2', 'ON'), ('Led 3', 'OFF');

INSERT INTO data_sensor (idsensor, "value", "datetime")
SELECT id, 27.30, CURRENT_TIMESTAMP
FROM sensors
WHERE sensorname = 'Nhiệt độ (°C)';

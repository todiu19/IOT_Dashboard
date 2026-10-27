import { useEffect, useState } from "react";
import { API, apiFetch } from "../api.js";
import "./AlertStatisticsChart.css";

// Hàm tiện ích: Lấy tháng và năm hiện tại theo định dạng chuẩn "YYYY-MM" (ví dụ: "2026-10")
function currentMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

export default function AlertStatisticsChart({ refreshVersion = 0 }) {
  const [month, setMonth] = useState(currentMonth);// Tháng đang được chọn để xem thống kê (mặc định là tháng hiện tại)
  const [data, setData] = useState(null);// Dữ liệu thống kê trả về từ Backend (tổng số lần bật và danh sách theo từng ngày)
  const [loading, setLoading] = useState(true);// Trạng thái đang tải dữ liệu từ API
  const [error, setError] = useState("");// Câu thông báo lỗi nếu gọi API thất bại
  const [retry, setRetry] = useState(0);// Bộ đếm để kích hoạt gọi lại API khi người dùng bấm nút "Thử lại"

  // Effect: Gọi API lấy thống kê cảnh báo mỗi khi đổi tháng, có tín hiệu realtime (refreshVersion) hoặc retry
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");

    // Gọi API: GET /api/dashboard/alert-statistics?month=YYYY-MM
    apiFetch(`${API.alertStatistics}?${new URLSearchParams({ month })}`, { signal: controller.signal })
      .then((response) => { 
        if (!controller.signal.aborted) setData(response); 
      })
      .catch((failure) => { 
        if (!controller.signal.aborted) setError(failure.message); 
      })
      .finally(() => { 
        if (!controller.signal.aborted) setLoading(false); 
      });

    // Cleanup: Hủy request mạng cũ nếu người dùng chuyển tháng nhanh hoặc component bị unmount
    return () => controller.abort();
  }, [month, refreshVersion, retry]);

  // Danh sách thống kê từng ngày trong tháng từ API (mỗi phần tử có date, onCount, offCount)
  const days = data?.days || [];
  // Tìm giá trị lớn nhất của trục Y (làm tròn lên bội số của 4 để chia 4 vạch lưới đều đặn, tối thiểu là 4)
  const maximum = Math.max(4, Math.ceil(Math.max(0, ...days.map((day) => day.onCount)) / 4) * 4);
  // Cấu hình khung vẽ biểu đồ bên trong SVG (lề trái, lề trên, chiều rộng, chiều cao - thu nhỏ gọn gàng)
  const plot = { left: 36, top: 16, width: 660, height: 145 };
  // Bước nhảy chiều ngang giữa các cột ngày
  const step = plot.width / Math.max(1, days.length);
  // Chiều rộng thực tế của mỗi cột (chiếm 60% khoảng cách step)
  const barWidth = step * 0.6;

  // Xử lý giá trị Tháng và Năm cho 2 dropdown menu chọn tháng
  const currentYear = new Date().getFullYear();
  const [yearStr, monthStr] = (month || currentMonth()).split("-");
  const selectedYear = Number(yearStr) || currentYear;
  const selectedMonth = monthStr || String(new Date().getMonth() + 1).padStart(2, "0");
  
  // Tạo danh sách năm hiển thị trong menu (từ currentYear - 3 đến currentYear + 3)
  const baseYears = Array.from({ length: 7 }, (_, i) => currentYear - 3 + i);
  const years = baseYears.includes(selectedYear) ? baseYears : [...baseYears, selectedYear].sort((a, b) => a - b);

  return (
    <article className="card chart-card alert-chart-card" aria-busy={loading}>
      <header className="card-header alert-chart-header">
        <h2 className="card-title">Số lần bật cảnh báo</h2>
        {/* Bộ chọn Tháng và Năm dạng dropdown để người dùng click chọn trực tiếp */}
        <div className="alert-month-selector">
          <label>
            <span className="sr-only">Chọn tháng</span>
            <select
              className="control alert-month-select"
              aria-label="Chọn tháng"
              value={selectedMonth}
              onChange={(e) => setMonth(`${selectedYear}-${e.target.value}`)}
            >
              {Array.from({ length: 12 }, (_, i) => {
                const m = String(i + 1).padStart(2, "0");
                return <option key={m} value={m}>{`Tháng ${i + 1}`}</option>;
              })}
            </select>
          </label>
          <label>
            <span className="sr-only">Chọn năm</span>
            <select
              className="control alert-year-select"
              aria-label="Chọn năm"
              value={selectedYear}
              onChange={(e) => setMonth(`${e.target.value}-${selectedMonth}`)}
            >
              {years.map((y) => (
                <option key={y} value={y}>{`Năm ${y}`}</option>
              ))}
            </select>
          </label>
        </div>
      </header>

      <div className="chart-wrap">
        {/* Trạng thái đang tải */}
        {loading ? (
          <p className="alert-chart-message" role="status">Đang tải thống kê…</p>
        ) : error ? (
          /* Trạng thái xảy ra lỗi */
          <div className="data-error" role="alert">
            <p>{error}</p>
            <button type="button" className="primary-button" onClick={() => setRetry((value) => value + 1)}>Thử lại</button>
          </div>
        ) : data && (
          /* Khi đã có dữ liệu từ Backend */
          <>
            {/* Tổng số lần bật cảnh báo trong cả tháng */}
            <div className="alert-chart-totals">
              <span className="alert-total-on">Bật <strong>{data.totalOn}</strong> lần</span>
            </div>
            
            {/* Chú thích màu sắc biểu đồ */}
            <div className="chart-legend">
              <span className="legend-item legend-alert-on"><i className="legend-line" />Bật cảnh báo</span>
            </div>

            {/* Vùng vẽ đồ thị cột SVG */}
            <div className="alert-chart-scroll">
              <svg className="chart-svg alert-statistics-svg" viewBox="0 0 720 200" role="img"
                aria-label={`Số lần bật cảnh báo theo từng ngày tháng ${month}`}>
                <title>{`Tháng ${month}: ${data.totalOn} lần bật cảnh báo`}</title>
                
                {/* 5 đường kẻ ngang mốc tọa độ trục Y (từ 0 đến maximum) */}
                {Array.from({ length: 5 }, (_, index) => {
                  const count = maximum - index * maximum / 4;
                  const y = plot.top + index * plot.height / 4;
                  return (
                    <g key={index}>
                      <line className="grid" x1={plot.left} x2={plot.left + plot.width} y1={y} y2={y} />
                      <text x={plot.left - 6} y={y + 4} textAnchor="end">{count}</text>
                    </g>
                  );
                })}

                {/* Vẽ từng cột theo từng ngày trong tháng */}
                {days.map((day, index) => {
                  const x = plot.left + index * step + step / 2;
                  const onHeight = day.onCount / maximum * plot.height;
                  return (
                    <g key={day.date} tabIndex="0" role="img"
                      aria-label={`${day.date}: bật cảnh báo ${day.onCount} lần`}>
                      <title>{`${day.date}: bật cảnh báo ${day.onCount} lần`}</title>
                      {/* Vùng cảm ứng hover/focus trong suốt */}
                      <rect x={x - step / 2} y={plot.top} width={step} height={plot.height} fill="transparent" />
                      {/* Cột thể hiện số lần bật cảnh báo trong ngày */}
                      <rect className="alert-bar-on" x={x - barWidth / 2} y={plot.top + plot.height - onHeight} width={barWidth} height={onHeight} rx="1" />
                      {/* Nhãn số ngày dưới chân cột (ví dụ: ngày 01 -> hiển thị 1) */}
                      <text x={x} y={plot.top + plot.height + 15} textAnchor="middle">{Number(day.date.slice(-2))}</text>
                    </g>
                  );
                })}

                {/* Nhãn tên trục tọa độ */}
                <text x={plot.left} y="11">Số lần</text>
                <text x={plot.left + plot.width} y="196" textAnchor="end">Ngày trong tháng</text>
              </svg>
            </div>

            {/* Thông báo nếu trong tháng không có lần bật cảnh báo nào */}
            {data.totalOn === 0 && <p className="alert-chart-message">Chưa ghi nhận bật cảnh báo trong tháng này.</p>}
            <p className="chart-date">Đếm theo trạng thái LED 4 được ESP32 xác nhận. Các phản hồi trùng không tính thêm.</p>
          </>
        )}
      </div>
    </article>
  );
}

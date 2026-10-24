import { useEffect, useState } from "react";
import { API, apiFetch } from "../api.js";

function currentMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

export default function AlertStatisticsChart({ refreshVersion = 0 }) {
  const [month, setMonth] = useState(currentMonth);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    apiFetch(`${API.alertStatistics}?${new URLSearchParams({ month })}`, { signal: controller.signal })
      .then((response) => { if (!controller.signal.aborted) setData(response); })
      .catch((failure) => { if (!controller.signal.aborted) setError(failure.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [month, refreshVersion, retry]);

  const days = data?.days || [];
  const maximum = Math.max(4, Math.ceil(Math.max(0, ...days.map((day) => day.onCount)) / 4) * 4);
  const plot = { left: 42, top: 24, width: 654, height: 230 };
  const step = plot.width / Math.max(1, days.length);
  const barWidth = step * 0.6;

  return (
    <article className="card chart-card alert-chart-card" aria-busy={loading}>
      <header className="card-header alert-chart-header">
        <h2 className="card-title">Số lần bật cảnh báo</h2>
        <label className="alert-month-field">
          <span>Tháng</span>
          <input className="control" type="month" aria-label="Tháng thống kê cảnh báo"
            value={month} min="0001-01" max="9999-12" required
            onChange={(event) => { if (event.target.value) setMonth(event.target.value); }} />
        </label>
      </header>
      <div className="chart-wrap">
        {loading ? <p className="alert-chart-message" role="status">Đang tải thống kê…</p>
          : error ? <div className="data-error" role="alert">
            <p>{error}</p><button type="button" className="primary-button" onClick={() => setRetry((value) => value + 1)}>Thử lại</button>
          </div> : data && <>
            <div className="alert-chart-totals">
              <span className="alert-total-on">Bật <strong>{data.totalOn}</strong> lần</span>
            </div>
            <div className="chart-legend">
              <span className="legend-item legend-alert-on"><i className="legend-line" />Bật cảnh báo</span>
            </div>
            <div className="alert-chart-scroll">
              <svg className="chart-svg alert-statistics-svg" viewBox="0 0 720 300" role="img"
                aria-label={`Số lần bật cảnh báo theo từng ngày tháng ${month}`}>
                <title>{`Tháng ${month}: ${data.totalOn} lần bật cảnh báo`}</title>
                {Array.from({ length: 5 }, (_, index) => {
                  const count = maximum - index * maximum / 4;
                  const y = plot.top + index * plot.height / 4;
                  return <g key={index}>
                    <line className="grid" x1={plot.left} x2={plot.left + plot.width} y1={y} y2={y} />
                    <text x={plot.left - 8} y={y + 4} textAnchor="end">{count}</text>
                  </g>;
                })}
                {days.map((day, index) => {
                  const x = plot.left + index * step + step / 2;
                  const onHeight = day.onCount / maximum * plot.height;
                  return <g key={day.date} tabIndex="0" role="img"
                    aria-label={`${day.date}: bật cảnh báo ${day.onCount} lần`}>
                    <title>{`${day.date}: bật cảnh báo ${day.onCount} lần`}</title>
                    <rect x={x - step / 2} y={plot.top} width={step} height={plot.height} fill="transparent" />
                    <rect className="alert-bar-on" x={x - barWidth / 2} y={plot.top + plot.height - onHeight} width={barWidth} height={onHeight} rx="1" />
                    <text x={x} y={plot.top + plot.height + 18} textAnchor="middle">{Number(day.date.slice(-2))}</text>
                  </g>;
                })}
                <text x={plot.left} y="14">Số lần</text>
                <text x={plot.left + plot.width} y="294" textAnchor="end">Ngày trong tháng</text>
              </svg>
            </div>
            {data.totalOn === 0 && <p className="alert-chart-message">Chưa ghi nhận bật cảnh báo trong tháng này.</p>}
            <p className="chart-date">Đếm theo trạng thái LED 4 được ESP32 xác nhận. Các phản hồi trùng không tính thêm.</p>
          </>}
      </div>
    </article>
  );
}

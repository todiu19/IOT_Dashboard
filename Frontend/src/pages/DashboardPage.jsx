import SensorChart, { sensorKind } from "../components/SensorChart.jsx";

const METRIC_CONFIG = [
  { kind: "temperature", label: "Nhiệt độ", unit: "°C", icon: "fa-temperature-half" },
  { kind: "humidity", label: "Độ ẩm", unit: "%", icon: "fa-droplet" },
  { kind: "light", label: "Độ sáng", unit: "lux", icon: "fa-sun" }
];

export default function DashboardPage({ dashboard, pendingDeviceIds, controlErrors = {}, onControlDevice }) {
  const metrics = {};
  (dashboard.latestReadings || []).forEach((reading) => {
    const kind = sensorKind(reading.sensorName);
    const value = Number(reading.value);
    if (kind && Number.isFinite(value)) metrics[kind] = value.toFixed(1);
  });

  return (
    <section className="page dashboard-page">
      <header className="page-head"><h1>Dashboard</h1></header>
      <div className="metrics-grid">
        {METRIC_CONFIG.map(({ kind, label, unit, icon }) => (
          <article className={`metric-card metric-${kind}`} key={kind}>
            <span className="metric-label">{label}</span>
            <div className="metric-reading">
              <i className={`fa-solid ${icon}`} aria-hidden="true" />
              <span className="metric-value">{metrics[kind] ?? "--"} {unit}</span>
            </div>
          </article>
        ))}
      </div>
      <div className="dashboard-grid">
        <article className="card chart-card">
          <header className="card-header"><h2 className="card-title">Nhiệt độ &amp; Độ ẩm</h2></header>
          <div className="chart-wrap">
            <div className="chart-legend">
              <span className="legend-item legend-temperature"><i className="legend-line" />Nhiệt độ (°C)</span>
              <span className="legend-item legend-humidity"><i className="legend-line" />Độ ẩm (%)</span>
            </div>
            <SensorChart readings={dashboard.chartData || []} />
          </div>
        </article>
        <article className="card chart-card">
          <header className="card-header"><h2 className="card-title">Độ sáng (lux)</h2></header>
          <div className="chart-wrap">
            <div className="chart-legend">
              <span className="legend-item legend-light"><i className="legend-line" />Độ sáng (lux)</span>
            </div>
            <SensorChart readings={dashboard.chartData || []} variant="light" />
          </div>
        </article>
      </div>
      <section className="devices-card" aria-labelledby="device-control-title">
        <h2 className="card-title" id="device-control-title">Device Control</h2>
        <div className="device-list">
          {(dashboard.devices || []).map((device) => (
            <div className="device-item" key={device.id}>
              <div className="device-label">
                <span className="device-name">{device.name}</span>
                {pendingDeviceIds.has(device.id)
                  ? <span className="device-control-status" role="status">LOADING — đang chờ phản hồi</span>
                  : controlErrors[device.id] && <span className="device-control-status is-failed" role="alert">FAILED — {controlErrors[device.id]}</span>}
              </div>
              <button
                className="switch"
                type="button"
                role="switch"
                aria-label={`Bật tắt ${device.name}`}
                aria-checked={device.status === "ON"}
                disabled={pendingDeviceIds.has(device.id)}
                aria-busy={pendingDeviceIds.has(device.id)}
                onClick={() => onControlDevice(device)}
              />
            </div>
          ))}
        </div>
      </section>
    </section>
  );
}

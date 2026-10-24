import TableFooter from "../components/TableFooter.jsx";
import CopyableDateTime from "../components/CopyableDateTime.jsx";
import "./ActionHistoryPage.css";

export default function ActionHistoryPage({ filters, devices, onFilterChange, onRefresh, result, error, page, size, onPageChange, onSizeChange }) {
  const rows = result.content || [];
  return (
    <section className="page">
      <header className="page-head"><h1>Action History</h1></header>
      <form className="filters" id="historyFilters" onSubmit={(event) => { event.preventDefault(); onRefresh(); }}>
        <label className="field">
          <span className="sr-only">Thời gian</span><i className="fa-solid fa-calendar" />
          <input className="control" type="text" placeholder="YYYY-MM-DD HH:mm:ss" title="Nhập đến đâu lọc đến đó" value={filters.date} onChange={(event) => onFilterChange("date", event.target.value)} />
        </label>
        <label>
          <span className="sr-only">Thiết bị</span>
          <select className="control" value={filters.deviceId} onChange={(event) => onFilterChange("deviceId", event.target.value)}>
            <option value="">Tất cả thiết bị</option>
            {devices.map((device) => <option key={device.id} value={device.id}>{device.id === 4 ? "Cảnh báo" : device.name}</option>)}
          </select>
        </label>
        <label>
          <span className="sr-only">Hành động</span>
          <select className="control" value={filters.action} onChange={(event) => onFilterChange("action", event.target.value)}>
            <option value="">Tất cả hành động</option><option value="ON">TURN ON</option><option value="OFF">TURN OFF</option>
          </select>
        </label>
        <label>
          <span className="sr-only">Trạng thái</span>
          <select className="control" value={filters.status} onChange={(event) => onFilterChange("status", event.target.value)}>
            <option value="">Tất cả trạng thái</option><option>LOADING</option><option>ON</option><option>OFF</option><option>FAILED</option>
          </select>
        </label>
        <button className="primary-button" type="submit">Tìm kiếm</button>
      </form>
      {error && <p className="data-error" role="alert">{error}</p>}
      <article className="card table-card">
        <div className="table-scroll">
          <table>
            <thead><tr><th>ID</th><th>Thiết bị</th><th>User</th><th>Hành động</th><th>Trạng thái</th><th>Thời gian</th></tr></thead>
            <tbody>
              {rows.length ? rows.map((item) => (
                <tr key={item.id}>
                  <td>{item.id}</td><td>{item.deviceId === 4 ? "Cảnh báo" : item.deviceName}</td><td>{item.username === "iot-system" ? "Hệ thống" : item.username}</td>
                  <td className={`status ${["ON", "OFF"].includes(item.action) ? item.action.toLowerCase() : ""}`}>{item.action}</td>
                  <td className={`status ${["ON", "OFF", "LOADING", "FAILED"].includes(item.status) ? item.status.toLowerCase() : ""}`}>{item.status}</td>
                  <td><CopyableDateTime value={item.performedAt} /></td>
                </tr>
              )) : <tr><td className="empty-state" colSpan={6}>Không tìm thấy dữ liệu phù hợp.</td></tr>}
            </tbody>
          </table>
        </div>
        <TableFooter page={page} totalPages={result.totalPages || 1} size={size} onPageChange={onPageChange} onSizeChange={onSizeChange} />
      </article>
    </section>
  );
}

import TableFooter from "../components/TableFooter.jsx";
import CopyableDateTime from "../components/CopyableDateTime.jsx";

export default function DataSensorPage({ search, filterMode, onSearchChange, onFilterModeChange, onRefresh, result, error, page, size, onPageChange, onSizeChange }) {
  const rows = result.content || [];
  return (
    <section className="page">
      <header className="page-head"><h1>Data Sensor</h1></header>
      <form className="filters" id="sensorFilters" onSubmit={(event) => { event.preventDefault(); onRefresh(); }}>
        <label className="field">
          <span className="sr-only">{filterMode === "time" ? "Ngày đo" : "Từ khóa"}</span>
          <i className="fa-solid fa-magnifying-glass" />
          <input
            className="control"
            type="text"
            placeholder={filterMode === "time" ? "YYYY-MM-DD HH:mm:ss" : "Nhập giá trị, cảm biến hoặc thời gian"}
            title="Thời gian nhập theo YYYY-MM-DD HH:mm:ss; có thể nhập năm, tháng, ngày hoặc đầy đủ"
            value={search}
            onChange={(event) => onSearchChange(event.target.value)}
          />
        </label>
        <label>
          <span className="sr-only">Bộ lọc dữ liệu</span>
          <select className="control" value={filterMode} onChange={(event) => onFilterModeChange(event.target.value)}>
            <option value="">Tất cả</option>
            <option>Nhiệt độ (°C)</option>
            <option>Độ ẩm (%)</option>
            <option>Độ sáng (lux)</option>
            <option value="time">Theo thời gian</option>
          </select>
        </label>
        <button className="primary-button" type="submit">Tìm kiếm</button>
      </form>
      {error && <p className="data-error" role="alert">{error}</p>}
      <article className="card table-card">
        <div className="table-scroll">
          <table>
            <thead><tr><th>ID</th><th>Sensor</th><th>Giá trị</th><th>Thời gian</th></tr></thead>
            <tbody>
              {rows.length ? rows.map((item) => (
                <tr key={item.id}><td>{item.id}</td><td>{item.sensorName}</td><td>{item.value}</td><td><CopyableDateTime value={item.recordedAt} /></td></tr>
              )) : <tr><td className="empty-state" colSpan={4}>Không tìm thấy dữ liệu phù hợp.</td></tr>}
            </tbody>
          </table>
        </div>
        <TableFooter page={page} totalPages={result.totalPages || 1} size={size} onPageChange={onPageChange} onSizeChange={onSizeChange} />
      </article>
    </section>
  );
}

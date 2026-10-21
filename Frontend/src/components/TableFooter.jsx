import Pagination from "./Pagination.jsx";

const PAGE_SIZES = [5, 10, 15, 20, 30, 40, 50];

export default function TableFooter({ page, totalPages, size, onPageChange, onSizeChange }) {
  return (
    <footer className="table-footer">
      <label className="page-size">
        <span>Số dòng mỗi trang</span>
        <select className="control page-size-select" value={size} onChange={(event) => onSizeChange(Number(event.target.value))}>
          {PAGE_SIZES.map((value) => <option key={value} value={value}>{value}</option>)}
        </select>
      </label>
      <Pagination page={page} totalPages={totalPages} onPageChange={onPageChange} />
    </footer>
  );
}

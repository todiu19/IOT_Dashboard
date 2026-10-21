export default function Pagination({ page, totalPages, onPageChange }) {
  const count = Math.max(1, totalPages);
  const visiblePages = [];
  for (let number = 1; number <= count; number++) {
    if (number === 1 || number === count || Math.abs(number - page) <= 2) {
      visiblePages.push(number);
    }
  }

  return (
    <nav className="pagination" aria-label="Phân trang">
      <button className="page-button" type="button" disabled={page === 1} onClick={() => onPageChange(page - 1)} aria-label="Trang trước">‹</button>
      {visiblePages.map((number, index) => (
        <span className="pagination-item" key={number}>
          {index > 0 && number > visiblePages[index - 1] + 1 && <span className="pagination-ellipsis">…</span>}
          <button
            className={`page-button${page === number ? " active" : ""}`}
            type="button"
            aria-current={page === number ? "page" : undefined}
            onClick={() => onPageChange(number)}
          >
            {number}
          </button>
        </span>
      ))}
      <button className="page-button" type="button" disabled={page === count} onClick={() => onPageChange(page + 1)} aria-label="Trang sau">›</button>
    </nav>
  );
}

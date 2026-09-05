import EmptyState from './EmptyState';
import ErrorState from './ErrorState';

/**
 * Tabel generik: columns = [{ key, label, render? }]. `error` (dari
 * useCollection) ditampilkan sebagai state terpisah dari empty — sebelumnya
 * error diam-diam ditampilkan sebagai "belum ada data" yang menyesatkan.
 */
export default function DataTable({ columns, rows, loading, error, onRetry, emptyTitle = 'Belum ada data', onRowClick }) {
  if (loading) return <div className="loading">Memuat data...</div>;
  if (error) return <ErrorState error={error} onRetry={onRetry} />;
  if (!rows || rows.length === 0) return <EmptyState title={emptyTitle} />;

  return (
    <table>
      <thead>
        <tr>
          {columns.map((c) => (
            <th key={c.key}>{c.label}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.id} onClick={() => onRowClick && onRowClick(row)} style={onRowClick ? { cursor: 'pointer' } : undefined}>
            {columns.map((c) => (
              <td key={c.key}>{c.render ? c.render(row) : row[c.key] ?? '-'}</td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

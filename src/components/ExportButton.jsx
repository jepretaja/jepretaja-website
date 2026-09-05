import { exportToCsv } from '../utils/csv';

export default function ExportButton({ filename, columns, rows }) {
  return (
    <button className="btn btn-outline btn-sm" onClick={() => exportToCsv(filename, columns, rows)} disabled={!rows?.length}>
      ⬇ Export CSV
    </button>
  );
}

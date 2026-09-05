/**
 * Export CSV client-side (section 62). Cukup untuk data ukuran wajar (ratusan
 * hingga beberapa ribu baris yang sudah ter-load di tabel). Untuk export
 * jutaan baris, pindahkan ke Cloud Function terjadwal yang menulis file ke
 * Storage lalu kirim link download — belum diimplementasikan di sini.
 */
export function exportToCsv(filename, columns, rows) {
  const escape = (val) => {
    const s = val === null || val === undefined ? '' : String(val);
    if (s.includes(',') || s.includes('"') || s.includes('\n')) {
      return `"${s.replace(/"/g, '""')}"`;
    }
    return s;
  };

  const header = columns.map((c) => escape(c.label)).join(',');
  const lines = rows.map((row) => columns.map((c) => escape(c.csvValue ? c.csvValue(row) : row[c.key])).join(','));
  const csv = [header, ...lines].join('\n');

  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

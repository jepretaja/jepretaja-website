/**
 * Pengurutan baris di sisi klien.
 *
 * Ada dua alasan panel ini mengurutkan di browser alih-alih memakai
 * `orderBy` Firestore:
 *
 *  1. Kombinasi `where(...)` + `orderBy(field lain)` menuntut composite
 *     index. Selama index itu belum dibuat di Firebase Console, query-nya
 *     bukan sekadar lambat — GAGAL TOTAL dengan `failed-precondition`, dan
 *     halaman cuma menampilkan pesan error.
 *  2. `orderBy` Firestore DIAM-DIAM MEMBUANG dokumen yang tidak punya field
 *     tersebut. Untuk panel admin — yang justru bertugas menampilkan seluruh
 *     data, termasuk data lama, data dari versi APK sebelumnya, atau data
 *     yang field-nya cacat — itu berbahaya: barisnya hilang tanpa jejak dan
 *     admin mengira datanya memang tidak ada.
 *
 * Jumlah baris di panel ini masih dalam orde ribuan, jadi mengurutkan di
 * browser jauh lebih aman ketimbang kehilangan baris diam-diam.
 */

/** Ubah nilai waktu apa pun (Timestamp Firestore, Date, angka, string) ke milidetik. */
export function toMillis(value) {
  if (!value) return 0;
  if (typeof value.toMillis === 'function') return value.toMillis();
  if (typeof value.toDate === 'function') return value.toDate().getTime();
  if (value instanceof Date) return value.getTime();
  if (typeof value === 'number') return value;
  const parsed = Date.parse(value);
  return Number.isNaN(parsed) ? 0 : parsed;
}

/** Terbaru di atas. Baris tanpa field waktu tetap ikut, ditaruh paling bawah. */
export function byNewest(rows, field = 'createdAt') {
  return [...rows].sort((a, b) => toMillis(b[field]) - toMillis(a[field]));
}

/** Angka terbesar di atas (mis. rating). */
export function byNumberDesc(rows, field) {
  return [...rows].sort((a, b) => (Number(b[field]) || 0) - (Number(a[field]) || 0));
}

/** Angka terkecil di atas (mis. urutan tampil kategori). */
export function byNumberAsc(rows, field) {
  return [...rows].sort((a, b) => (Number(a[field]) || 0) - (Number(b[field]) || 0));
}

/**
 * Kosakata status booking — SATU sumber untuk seluruh panel.
 *
 * Harus sinkron persis dengan object BookingStatus di
 * app/src/main/java/com/jepretaja/app/data/model/BookingModel.kt pada APK, yang
 * pada gilirannya mengikuti state machine di backend.
 *
 * Dikumpulkan di sini karena daftar yang sama sebelumnya ditulis ulang di
 * Dashboard, Analytics, dan BookingList dengan isi yang berbeda-beda — masing-
 * masing memakai istilah yang tidak pernah ditulis backend ('accepted',
 * 'payment_pending'). Akibatnya kartu "Completed Bookings" selalu 0 dan
 * conversion rate selalu 0%, padahal datanya ada.
 */

/** Seluruh status yang mungkin, berurutan sesuai alur hidup booking. */
export const SEMUA_STATUS = [
  'draft', 'pending_payment', 'paid', 'confirmed', 'upcoming', 'in_progress',
  'completed', 'customer_confirmed', 'funds_released', 'reviewed',
  'refund_requested', 'cancelled', 'rejected', 'disputed',
];

/** Sedang berjalan — sudah dipesan, belum tuntas. */
export const STATUS_AKTIF = ['pending_payment', 'paid', 'confirmed', 'upcoming', 'in_progress'];

/** Sudah tuntas. Booking tidak berhenti di 'completed': ia lanjut ke
 *  customer_confirmed -> funds_released -> reviewed. */
export const STATUS_SELESAI = ['completed', 'customer_confirmed', 'funds_released', 'reviewed'];

/** Berakhir tanpa penyelesaian normal. */
export const STATUS_BATAL = ['cancelled', 'refund_requested', 'disputed'];

/** Uangnya sudah benar-benar masuk — dipakai menghitung GMV. Booking yang
 *  dibatalkan atau belum dibayar TIDAK boleh ikut dijumlahkan. */
export const STATUS_BERBAYAR = ['paid', 'confirmed', 'upcoming', 'in_progress', ...STATUS_SELESAI];

/** Total nilai transaksi yang benar-benar terjadi. */
export function hitungGmv(bookings) {
  return bookings
    .filter((b) => STATUS_BERBAYAR.includes(b.status))
    .reduce((jumlah, b) => jumlah + (Number(b.total) || 0), 0);
}

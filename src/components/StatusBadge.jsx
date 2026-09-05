/**
 * Warna per status. Kosakata booking mengikuti object BookingStatus di
 * BookingModel.kt pada APK — status yang tidak terdaftar di sini tetap tampil,
 * hanya saja abu-abu, jadi menambahkannya murni soal keterbacaan.
 */
const MAP = {
  active: 'success', published: 'success', completed: 'success', confirmed: 'success',
  paid: 'success', released: 'success', success: 'success', resolved: 'success', approved: 'success',
  // Tahap setelah pekerjaan selesai — semuanya kabar baik.
  customer_confirmed: 'success', funds_released: 'success', reviewed: 'success',
  pending: 'warning', payment_pending: 'warning', pending_payment: 'warning',
  funds_held: 'warning', processing: 'warning', upcoming: 'warning',
  requested: 'warning', draft: 'neutral', pending_review: 'warning', release_pending: 'warning',
  awaiting_transfer: 'warning', reviewing: 'warning', refund_requested: 'warning',
  cancelled: 'danger', failed: 'danger', rejected: 'danger', disputed: 'danger', suspended: 'danger',
  refunded: 'info', hidden: 'neutral', open: 'warning', deleted: 'neutral',
};

export default function StatusBadge({ status }) {
  if (!status) return <span className="badge badge-neutral">-</span>;
  const tone = MAP[status.toLowerCase()] || 'neutral';
  return <span className={`badge badge-${tone}`}>{status.replace(/_/g, ' ')}</span>;
}

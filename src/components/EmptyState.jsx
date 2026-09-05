import { Link } from 'react-router-dom';
import Icon from './Icon';

/**
 * Keadaan kosong.
 *
 * Versi sebelumnya hanya menampilkan satu tanda hubung dan sebaris teks di
 * tengah kotak besar — yang membuat halaman baru terlihat seperti gagal
 * memuat, bukan seperti halaman yang memang belum ada isinya. Sekarang ada
 * ikon dalam lingkaran, kalimat penjelas, dan — kalau memang ada yang bisa
 * dilakukan — satu tombol menuju langkah berikutnya.
 *
 * `glyph` masih diterima demi pemanggil lama; kalau `icon` diberikan, ikon
 * garis yang dipakai.
 */
export default function EmptyState({
  glyph,
  icon = 'inbox',
  title = 'Belum ada data',
  hint,
  actionLabel,
  actionTo,
  onAction,
}) {
  return (
    <div className="empty-state">
      <div className="empty-lingkar">
        {glyph ? <span className="glyph">{glyph}</span> : <Icon name={icon} size={24} />}
      </div>
      <div className="empty-judul">{title}</div>
      {hint && <div className="empty-hint">{hint}</div>}
      {actionLabel && (actionTo ? (
        <Link className="btn btn-primary btn-sm empty-aksi" to={actionTo}>{actionLabel}</Link>
      ) : onAction ? (
        <button type="button" className="btn btn-primary btn-sm empty-aksi" onClick={onAction}>
          {actionLabel}
        </button>
      ) : null)}
    </div>
  );
}

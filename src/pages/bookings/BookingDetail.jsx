import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { doc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useDocument } from '../../hooks/useDocument';
import { usePermission } from '../../hooks/usePermission';
import { useConfirm } from '../../components/ConfirmDialog';
import { adminUpdateBookingStatus } from '../../utils/adminActions';
import StatusBadge from '../../components/StatusBadge';
import ErrorState from '../../components/ErrorState';
import { formatCurrency, formatDateTime } from '../../utils/format';

/** Booking Detail — override status admin lewat Cloud Function
 * `adminUpdateBookingStatus` (memvalidasi graf transisi & mencatat audit log),
 * BUKAN update Firestore langsung seperti sebelumnya. */
export default function BookingDetail() {
  const { id } = useParams();
  const { data: booking, loading, error: loadError } = useDocument(doc(db, PATHS.bookings, id));
  const { can } = usePermission();
  const { confirm, dialog } = useConfirm();
  const [processing, setProcessing] = useState(null);
  const [error, setError] = useState(null);

  const setStatus = async (toStatus) => {
    const ok = await confirm({
      title: `Ubah status ke "${toStatus}"?`,
      message: 'Override manual ini akan tercatat di Audit Logs.',
      danger: toStatus === 'cancelled',
    });
    if (!ok) return;
    setProcessing(toStatus);
    setError(null);
    try {
      await adminUpdateBookingStatus({ bookingId: id, toStatus, reason: 'manual override by admin' });
    } catch (err) {
      setError(err.message || 'Transisi status tidak valid.');
    } finally {
      setProcessing(null);
    }
  };

  if (loading) return <div className="loading">Memuat...</div>;
  if (loadError) return <ErrorState error={loadError} onRetry={() => window.location.reload()} />;
  if (!booking) return <div className="empty-state">Booking tidak ditemukan</div>;

  const canManage = can('manage_booking');

  return (
    <div>
      {dialog}
      <div className="breadcrumb"><Link to="/bookings">Bookings</Link> / #{id.slice(0, 8).toUpperCase()}</div>
      <div className="booking-detail-hero">
        <div>
          <div className="tracking-eyebrow">BOOKING CONTROL · #{id.slice(0, 8).toUpperCase()}</div>
          <h1>{booking.packageName || 'Booking'}</h1>
          <p>{booking.location || 'Lokasi belum diisi'} · {booking.time || 'Jam belum diisi'}</p>
        </div>
        <StatusBadge status={booking.status} />
      </div>
      <div className="grid grid-2">
        <div className="card">
          <div className="section-title">Ringkasan perjalanan</div>
          <div className="detail-row"><span className="k">Paket</span><span className="v">{booking.packageName}</span></div>
          <div className="detail-row"><span className="k">Lokasi</span><span className="v">{booking.location}</span></div>
          <div className="detail-row"><span className="k">Jam</span><span className="v">{booking.time}</span></div>
          <div className="detail-row"><span className="k">Catatan</span><span className="v">{booking.note || '-'}</span></div>
          <div className="detail-row"><span className="k">Total</span><span className="v num">{formatCurrency(booking.total)}</span></div>
          <div className="detail-row"><span className="k">Status</span><span className="v"><StatusBadge status={booking.status} /></span></div>
          <div className="detail-row"><span className="k">Dibuat</span><span className="v">{formatDateTime(booking.createdAt)}</span></div>
          {booking.latitude && booking.longitude && <div className="detail-row"><span className="k">Koordinat</span><span className="v num">{Number(booking.latitude).toFixed(5)}, {Number(booking.longitude).toFixed(5)}</span></div>}
        </div>
        <div className="card">
          <div className="section-title">Override Status (Admin)</div>
          <p style={{ fontSize: 12.5, color: 'var(--text-secondary)', marginTop: 0 }}>
            Hanya untuk kasus dukungan pelanggan khusus. Transisi tetap divalidasi terhadap state machine — permintaan yang tidak valid akan ditolak server.
          </p>
          {!canManage && <p style={{ fontSize: 12.5, color: 'var(--danger)' }}>Role Anda tidak memiliki izin mengelola booking.</p>}
          {error && <p style={{ fontSize: 12.5, color: 'var(--danger)' }}>{error}</p>}
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {['confirmed', 'rejected', 'in_progress', 'completed', 'customer_confirmed', 'cancelled'].map((s) => (
              <button key={s} className="btn btn-outline btn-sm" disabled={!canManage || processing} onClick={() => setStatus(s)}>
                {processing === s ? '...' : s}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

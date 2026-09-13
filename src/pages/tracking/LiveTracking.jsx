import { useMemo } from 'react';
import { collection, orderBy, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { formatDateTime } from '../../utils/format';

export default function LiveTracking() {
  const { data: bookings, loading: bookingsLoading, error: bookingsError } = useCollection(
    collection(db, PATHS.bookings),
    [where('status', '==', 'in_progress'), orderBy('updatedAt', 'desc')],
  );
  const { data: positions, loading: positionsLoading, error: positionsError } = useCollection(
    collection(db, PATHS.liveLocations),
    [orderBy('updatedAt', 'desc')],
  );
  const { data: users, loading: usersLoading } = useCollection(collection(db, PATHS.users));
  const { data: creators, loading: creatorsLoading } = useCollection(collection(db, PATHS.creators));
  const rows = useMemo(() => bookings.map((booking) => ({
    booking: {
      ...booking,
      customerName: users.find((user) => user.id === booking.customerId)?.name,
      creatorName: creators.find((creator) => creator.id === booking.creatorId)?.displayName,
    },
    customer: positions.find((position) => position.bookingId === booking.id && position.role === 'customer'),
    creator: positions.find((position) => position.bookingId === booking.id && position.role === 'creator'),
  })), [bookings, positions, users, creators]);
  const loading = bookingsLoading || positionsLoading || usersLoading || creatorsLoading;
  const error = bookingsError || positionsError;

  return (
    <div>
      <h1 className="page-title">Perjalanan Live</h1>
      <p className="text-meta">Posisi customer dan creator diperbarui otomatis saat booking berjalan.</p>
      {error && <div className="alert alert-danger">Gagal memuat posisi realtime.</div>}
      {loading && <div className="loading">Memuat perjalanan...</div>}
      {!loading && rows.length === 0 && <div className="empty-state">Belum ada perjalanan aktif.</div>}
      <div className="grid grid-2">
        {rows.map(({ booking, customer, creator }) => (
          <article className="card" key={booking.id}>
            <div className="section-title">#{booking.id.slice(0, 8).toUpperCase()} · {booking.packageName || 'Booking'}</div>
            <div className="detail-row"><span className="k">Customer</span><span className="v">{booking.customerName || booking.customerId || '-'}</span></div>
            <div className="detail-row"><span className="k">Creator</span><span className="v">{booking.creatorName || booking.creatorId || '-'}</span></div>
            <div className="detail-row"><span className="k">Lokasi tujuan</span><span className="v">{booking.location || '-'}</span></div>
            <div className="tracking-points"><Point label="Customer" point={customer} /><Point label="Creator" point={creator} /></div>
          </article>
        ))}
      </div>
    </div>
  );
}

function Point({ label, point }) {
  if (!point) return <div className="tracking-point"><strong>{label}</strong><span>Menunggu lokasi...</span></div>;
  const url = `https://www.google.com/maps?q=${point.latitude},${point.longitude}`;
  return <div className="tracking-point"><strong>{label}</strong><span>{Number(point.latitude).toFixed(5)}, {Number(point.longitude).toFixed(5)}</span><a href={url} target="_blank" rel="noreferrer">Buka peta</a><small>{formatDateTime(point.updatedAt)}</small></div>;
}
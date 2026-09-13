import { useMemo } from 'react';
import { collection, orderBy, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { formatDateTime } from '../../utils/format';

import { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

export default function LiveTracking() {
  const { data: bookings, loading: bookingsLoading, error: bookingsError } = useCollection(
    collection(db, PATHS.bookings), [where('status', '==', 'in_progress'), orderBy('updatedAt', 'desc')],
  );
  const { data: positions, loading: positionsLoading, error: positionsError } = useCollection(
    collection(db, PATHS.liveLocations), [orderBy('updatedAt', 'desc')],
  );
  const { data: tracks, loading: tracksLoading } = useCollection(
    collection(db, PATHS.locationTracks), [orderBy('recordedAt', 'desc')],
  );
  const { data: alerts, loading: alertsLoading } = useCollection(
    collection(db, PATHS.supportAlerts), [orderBy('createdAt', 'desc')],
  );
  const { data: users, loading: usersLoading } = useCollection(collection(db, PATHS.users));
  const { data: creators, loading: creatorsLoading } = useCollection(collection(db, PATHS.creators));
  const [selectedId, setSelectedId] = useState(null);
  const [playbackIndex, setPlaybackIndex] = useState(-1);
  const [query, setQuery] = useState('');

  const rows = useMemo(() => bookings.map((booking) => ({
    booking: {
      ...booking,
      customerName: users.find((user) => user.id === booking.customerId)?.name,
      creatorName: creators.find((creator) => creator.id === booking.creatorId)?.displayName,
    },
    customer: positions.find((point) => point.bookingId === booking.id && point.role === 'customer'),
    creator: positions.find((point) => point.bookingId === booking.id && point.role === 'creator'),
    history: tracks.filter((point) => point.bookingId === booking.id).sort((a, b) => timeOf(a.recordedAt) - timeOf(b.recordedAt)),
  })), [bookings, positions, tracks, users, creators]);
  const selected = rows.find((row) => row.booking.id === selectedId) || rows[0];
  const visibleRows = useMemo(() => rows.filter(({ booking }) => {
    const needle = query.trim().toLowerCase();
    return !needle || [booking.id, booking.packageName, booking.customerName, booking.creatorName]
      .filter(Boolean).some((value) => value.toLowerCase().includes(needle));
  }), [rows, query]);
  const loading = bookingsLoading || positionsLoading || tracksLoading || alertsLoading || usersLoading || creatorsLoading;
  const error = bookingsError || positionsError;

  useEffect(() => {
    if (!selectedId && rows[0]) setSelectedId(rows[0].booking.id);
  }, [rows, selectedId]);

  const activePeople = rows.reduce((total, row) => total + (row.customer ? 1 : 0) + (row.creator ? 1 : 0), 0);
  const arrived = rows.reduce((total, row) => total + [row.customer, row.creator].filter((point) => point?.geofenceStatus === 'arrived').length, 0);

  return (
    <div>
      <div className="tracking-hero">
        <div>
          <div className="tracking-eyebrow"><span className="tracking-pulse" /> OPERATIONS · LIVE MONITORING</div>
          <h1 className="page-title">Perjalanan Live</h1>
          <p className="text-meta">Pantau creator dan customer dalam satu peta, dengan jejak perjalanan dan alert support.</p>
        </div>
        <span className="tracking-live-badge">LIVE <span>●</span></span>
      </div>
      <div className="grid grid-4 tracking-stats">
        <div className="card"><div className="text-meta">Booking aktif</div><strong>{rows.length}</strong></div>
        <div className="card"><div className="text-meta">Orang terlacak</div><strong>{activePeople}</strong></div>
        <div className="card"><div className="text-meta">Sudah tiba geofence</div><strong>{arrived}</strong></div>
        <div className="card stat-alert"><div className="text-meta">SOS terbuka</div><strong>{alerts.filter((alert) => alert.status === 'open').length}</strong></div>
      </div>
      {error && <div className="alert alert-danger">Gagal memuat perjalanan realtime.</div>}
      {loading && <div className="loading">Memuat perjalanan...</div>}
      {!loading && rows.length === 0 && <div className="empty-state">Belum ada perjalanan aktif.</div>}
      {selected && <>
        <div className="card tracking-map-card"><MapView row={selected} playbackIndex={playbackIndex} /></div>
        <div className="table-wrap tracking-list">
          <div className="table-toolbar">
            <div><strong>Perjalanan aktif</strong><span className="text-meta"> {visibleRows.length} dari {rows.length} booking</span></div>
            <input className="input tracking-search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Cari booking / nama..." />
          </div>
          {visibleRows.map((row) => <TrackingRow key={row.booking.id} row={row} selected={row.booking.id === selected.booking.id} onSelect={() => { setSelectedId(row.booking.id); setPlaybackIndex(-1); }} />)}
        </div>
        {selected.history.length > 1 && <div className="card tracking-playback">
          <div className="section-title">Playback rute</div>
          <input type="range" min="-1" max={selected.history.length - 1} value={playbackIndex} onChange={(event) => setPlaybackIndex(Number(event.target.value))} />
          <span className="text-meta">{playbackIndex < 0 ? 'Live sekarang' : `${playbackIndex + 1} dari ${selected.history.length} titik`}</span>
        </div>}
      </>}
      {alerts.length > 0 && <div className="card tracking-alerts">
        <div className="section-title">Alert support terbaru</div>
        {alerts.slice(0, 5).map((alert) => <div className="detail-row" key={alert.id}><span className="k">{alert.role} · #{alert.bookingId?.slice(0, 8)}</span><span className="v">{alert.message || 'SOS'} · {formatDateTime(alert.createdAt)}</span></div>)}
      </div>}
    </div>
  );
}

function TrackingRow({ row, selected, onSelect }) {
  return <button className={`tracking-row${selected ? ' selected' : ''}`} onClick={onSelect}>
    <span><strong>#{row.booking.id.slice(0, 8).toUpperCase()}</strong><small>{row.booking.packageName || 'Booking'} · {row.booking.customerName || row.booking.customerId}</small></span>
    <span><small>Customer: {statusPoint(row.customer)} · Creator: {statusPoint(row.creator)}</small><small>Update: {formatDateTime(row.customer?.updatedAt || row.creator?.updatedAt)}</small></span>
  </button>;
}

function MapView({ row, playbackIndex }) {
  const ref = useRef(null);
  useEffect(() => {
    if (!ref.current) return undefined;
    const livePoints = [row.customer, row.creator].filter(Boolean).map((point) => [point.latitude, point.longitude]);
    const history = row.history.slice(0, playbackIndex < 0 ? row.history.length : playbackIndex + 1).map((point) => [point.latitude, point.longitude]);
    const center = livePoints[0] || (row.booking.latitude && row.booking.longitude ? [row.booking.latitude, row.booking.longitude] : [-6.2, 106.816666]);
    const map = L.map(ref.current).setView(center, 13);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { attribution: '&copy; OpenStreetMap contributors' }).addTo(map);
    livePoints.forEach((point, index) => L.circleMarker(point, { radius: 9, color: index === 0 ? '#2F6FED' : '#DC2626', fillOpacity: 0.85 }).addTo(map).bindTooltip(index === 0 ? 'Customer' : 'Creator'));
    if (row.booking.latitude && row.booking.longitude) L.circleMarker([row.booking.latitude, row.booking.longitude], { radius: 8, color: '#16A34A', fillOpacity: 0.4 }).addTo(map).bindTooltip('Lokasi booking');
    if (history.length > 1) L.polyline(history, { color: '#2F6FED', weight: 4 }).addTo(map);
    if (livePoints.length > 1) map.fitBounds(livePoints, { padding: [30, 30] });
    return () => map.remove();
  }, [row, playbackIndex]);
  return <div ref={ref} className="tracking-map" aria-label="Peta perjalanan realtime" />;
}

function timeOf(value) {
  return value?.toDate ? value.toDate().getTime() : value?.seconds ? value.seconds * 1000 : 0;
}

function statusPoint(point) {
  if (!point) return 'menunggu lokasi';
  if (point.geofenceStatus === 'arrived') return 'sudah tiba';
  if (point.etaSeconds == null) return 'berjalan';
  return `ETA ${Math.ceil(point.etaSeconds / 60)} mnt`;
}

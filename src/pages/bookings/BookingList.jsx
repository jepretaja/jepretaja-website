import { useMemo, useState } from 'react';
import { collection, orderBy } from 'firebase/firestore';
import { useNavigate } from 'react-router-dom';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import SearchInput from '../../components/SearchInput';
import ExportButton from '../../components/ExportButton';
import { formatCurrency, formatDate } from '../../utils/format';

// Daftar ini HARUS sama persis dengan object BookingStatus di BookingModel.kt
// pada APK. Sebelumnya di sini ada 'pending', 'accepted', dan 'payment_pending'
// yang tidak pernah ditulis aplikasi mana pun — jadi memilihnya selalu
// menghasilkan tabel kosong — sementara status yang benar-benar dipakai
// ('pending_payment', 'paid', 'upcoming', 'customer_confirmed',
// 'funds_released', 'reviewed', 'refund_requested') tidak bisa disaring
// sama sekali.
const STATUS_OPTIONS = [
  'all', 'draft', 'pending_payment', 'paid', 'confirmed', 'upcoming',
  'in_progress', 'completed', 'customer_confirmed', 'funds_released',
  'reviewed', 'cancelled', 'rejected', 'refund_requested', 'disputed',
];

/** Booking List (section 29). */
export default function BookingList() {
  const { data, loading, error } = useCollection(collection(db, PATHS.bookings), [orderBy('createdAt', 'desc')]);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('all');
  const navigate = useNavigate();

  const filtered = useMemo(() => {
    return data.filter((b) => {
      const matchSearch = (b.packageName || '').toLowerCase().includes(search.toLowerCase()) || b.id.includes(search);
      const matchStatus = status === 'all' || b.status === status;
      return matchSearch && matchStatus;
    });
  }, [data, search, status]);

  return (
    <div>
      <h1 className="page-title">Bookings</h1>
      <div className="table-wrap">
        <div className="table-toolbar">
          <SearchInput value={search} onChange={setSearch} placeholder="Cari paket / ID booking..." />
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <select className="input" style={{ width: 180 }} value={status} onChange={(e) => setStatus(e.target.value)}>
              {STATUS_OPTIONS.map((s) => <option key={s} value={s}>{s === 'all' ? 'Semua Status' : s}</option>)}
            </select>
            <ExportButton
              filename="bookings.csv"
              columns={[
                { key: 'id', label: 'ID' }, { key: 'packageName', label: 'Paket' }, { key: 'location', label: 'Lokasi' },
                { key: 'total', label: 'Total' }, { key: 'status', label: 'Status' },
              ]}
              rows={filtered}
            />
          </div>
        </div>
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          onRowClick={(row) => navigate(`/bookings/${row.id}`)}
          columns={[
            { key: 'id', label: 'ID', render: (r) => r.id.slice(0, 8).toUpperCase() },
            { key: 'packageName', label: 'Paket' },
            { key: 'location', label: 'Lokasi' },
            { key: 'total', label: 'Total', render: (r) => formatCurrency(r.total) },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            { key: 'createdAt', label: 'Dibuat', render: (r) => formatDate(r.createdAt) },
          ]}
          rows={filtered}
        />
      </div>
    </div>
  );
}

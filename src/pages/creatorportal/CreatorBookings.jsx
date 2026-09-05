import { useNavigate } from 'react-router-dom';
import { collection, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { useAuth } from '../../auth/AuthContext';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import ExportButton from '../../components/ExportButton';
import { formatCurrency, formatDateTime } from '../../utils/format';
import { byNewest } from '../../utils/sort';

/** Seluruh booking milik creator ini saja — difilter di query, bukan di klien. */
export default function CreatorBookings() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const uid = user?.uid;
  // Urutan terbaru dihitung di klien (lihat utils/sort.js) — where + orderBy
  // di field berbeda menuntut composite index, dan tanpa index itu query
  // gagal total sehingga halaman ini hanya menampilkan pesan error.
  const { data: rows, loading, error } = useCollection(
    collection(db, PATHS.bookings),
    uid ? [where('creatorId', '==', uid)] : []
  );
  const data = byNewest(rows);

  return (
    <div>
      <h1 className="page-title">Booking Saya</h1>
      <p className="text-meta" style={{ marginBottom: 16 }}>
        Klik satu baris untuk membuka detail dan menjalankan aksi seperti memulai sesi atau menandai selesai.
      </p>
      <div className="table-wrap">
        <div className="table-toolbar">
          <div />
          <ExportButton
            filename="booking-saya.csv"
            columns={[
              { key: 'customerName', label: 'Pelanggan' },
              { key: 'date', label: 'Tanggal' },
              { key: 'total', label: 'Nilai' },
              { key: 'status', label: 'Status' },
            ]}
            rows={data}
          />
        </div>
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          columns={[
            { key: 'customerName', label: 'Pelanggan', render: (r) => r.customerName || r.customerId || '-' },
            { key: 'packageName', label: 'Paket', render: (r) => r.packageName || '-' },
            { key: 'date', label: 'Tanggal Acara', render: (r) => formatDateTime(r.date) },
            { key: 'total', label: 'Nilai', render: (r) => formatCurrency(r.total) },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
          ]}
          rows={data}
          onRowClick={(r) => navigate(`/creator/bookings/${r.id}`)}
          emptyTitle="Belum ada booking"
        />
      </div>
    </div>
  );
}

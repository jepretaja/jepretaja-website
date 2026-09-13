import { collection, orderBy, where } from 'firebase/firestore';
import { useNavigate } from 'react-router-dom';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import { formatDateTime } from '../../utils/format';

export default function CreatorVerification() {
  const navigate = useNavigate();
  const { data, loading, error } = useCollection(
    collection(db, PATHS.creators),
    [where('verified', '==', false), orderBy('createdAt', 'desc')],
  );

  return (
    <div>
      <div className="page-title-row">
        <div>
          <h1 className="page-title">Creator Verification</h1>
          <p className="text-meta">Tinjau creator yang belum terverifikasi dan buka detail profilnya.</p>
        </div>
        <span className="badge badge-warning">{data.length} menunggu</span>
      </div>
      <div className="card verification-intro">
        <strong>Review profil dengan teliti</strong>
        <span className="text-meta">Klik baris untuk melihat portofolio, data profil, dan status verifikasi.</span>
      </div>
      <div className="table-wrap">
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          emptyTitle="Tidak ada creator menunggu verifikasi"
          onRowClick={(row) => navigate(`/creators/${row.id}`)}
          columns={[
            { key: 'displayName', label: 'Creator', render: (row) => row.displayName || row.userId || row.id },
            { key: 'city', label: 'Kota', render: (row) => row.city || '-' },
            { key: 'categories', label: 'Kategori', render: (row) => row.categories?.join(', ') || '-' },
            { key: 'status', label: 'Status', render: (row) => <StatusBadge status={row.status || 'pending'} /> },
            { key: 'createdAt', label: 'Terdaftar', render: (row) => formatDateTime(row.createdAt) },
          ]}
          rows={data}
        />
      </div>
    </div>
  );
}

import { collection, orderBy } from 'firebase/firestore';
import { useNavigate } from 'react-router-dom';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import { formatDateTime } from '../../utils/format';

/** Disputes (section 15 & 29). */
export default function Disputes() {
  const { data, loading, error } = useCollection(collection(db, PATHS.disputes), [orderBy('createdAt', 'desc')]);
  const navigate = useNavigate();

  return (
    <div>
      <h1 className="page-title">Disputes</h1>
      <div className="table-wrap">
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          onRowClick={(row) => navigate(`/disputes/${row.id}`)}
          emptyTitle="Tidak ada dispute"
          columns={[
            { key: 'bookingId', label: 'Booking ID', render: (r) => (r.bookingId || '').slice(0, 8).toUpperCase() },
            { key: 'openedBy', label: 'Dibuka Oleh' },
            { key: 'reason', label: 'Alasan' },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            { key: 'createdAt', label: 'Dibuka', render: (r) => formatDateTime(r.createdAt) },
          ]}
          rows={data}
        />
      </div>
    </div>
  );
}

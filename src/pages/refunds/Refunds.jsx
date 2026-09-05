import { collection, orderBy } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import { formatCurrency } from '../../utils/format';

/** Refunds (section 22 & 29). */
export default function Refunds() {
  const { data, loading, error } = useCollection(collection(db, PATHS.refunds));

  return (
    <div>
      <h1 className="page-title">Refunds</h1>
      <div className="table-wrap">
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          emptyTitle="Belum ada permintaan refund"
          columns={[
            { key: 'bookingId', label: 'Booking ID', render: (r) => (r.bookingId || '').slice(0, 8).toUpperCase() },
            { key: 'amount', label: 'Jumlah', render: (r) => formatCurrency(r.amount) },
            { key: 'reason', label: 'Alasan' },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            { key: 'providerReference', label: 'Referensi' },
          ]}
          rows={data}
        />
      </div>
    </div>
  );
}

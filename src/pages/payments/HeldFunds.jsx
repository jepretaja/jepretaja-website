import { collection, orderBy, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import StatCard from '../../components/StatCard';
import { formatCurrency } from '../../utils/format';

/** Escrow / Held Funds (section 22 & 29): dana yang masih tertahan. */
export default function HeldFunds() {
  const { data, loading, error } = useCollection(collection(db, PATHS.escrowTransactions), [where('releaseStatus', '==', 'pending')]);
  const total = data.reduce((sum, e) => sum + (Number(e.amount) || 0), 0);

  return (
    <div>
      <h1 className="page-title">Escrow / Held Funds</h1>
      <div className="grid grid-4" style={{ marginBottom: 16 }}>
        <StatCard label="Total Held Funds" value={formatCurrency(total)} />
        <StatCard label="Jumlah Transaksi Tertahan" value={data.length} />
      </div>
      <div className="table-wrap">
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          emptyTitle="Tidak ada dana tertahan"
          columns={[
            { key: 'bookingId', label: 'Booking ID', render: (r) => (r.bookingId || '').slice(0, 8).toUpperCase() },
            { key: 'amount', label: 'Jumlah', render: (r) => formatCurrency(r.amount) },
            { key: 'holdStatus', label: 'Hold Status', render: (r) => <StatusBadge status={r.holdStatus} /> },
            { key: 'releaseStatus', label: 'Release Status', render: (r) => <StatusBadge status={r.releaseStatus} /> },
            { key: 'providerReference', label: 'Referensi Provider' },
          ]}
          rows={data}
        />
      </div>
    </div>
  );
}

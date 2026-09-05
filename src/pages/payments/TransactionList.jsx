import { collection, orderBy } from 'firebase/firestore';
import { useNavigate } from 'react-router-dom';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import ExportButton from '../../components/ExportButton';
import { formatCurrency, formatDateTime } from '../../utils/format';

/** Transaction List (section 22 & 29): semua transaksi, filter status. */
export default function TransactionList() {
  const { data, loading, error } = useCollection(collection(db, PATHS.payments), [orderBy('createdAt', 'desc')]);
  const navigate = useNavigate();

  return (
    <div>
      <h1 className="page-title">Payments — Transactions</h1>
      <div className="table-wrap">
        <div className="table-toolbar">
          <div />
          <ExportButton
            filename="transactions.csv"
            columns={[
              { key: 'id', label: 'Payment ID' }, { key: 'bookingId', label: 'Booking ID' },
              { key: 'provider', label: 'Provider' }, { key: 'amount', label: 'Jumlah' }, { key: 'status', label: 'Status' },
            ]}
            rows={data}
          />
        </div>
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          onRowClick={(row) => navigate(`/payments/${row.id}`)}
          columns={[
            { key: 'id', label: 'Payment ID', render: (r) => r.id.slice(0, 10).toUpperCase() },
            { key: 'bookingId', label: 'Booking ID', render: (r) => (r.bookingId || '').slice(0, 8).toUpperCase() },
            { key: 'provider', label: 'Provider' },
            { key: 'amount', label: 'Jumlah', render: (r) => formatCurrency(r.amount) },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            { key: 'createdAt', label: 'Waktu', render: (r) => formatDateTime(r.createdAt) },
          ]}
          rows={data}
        />
      </div>
    </div>
  );
}

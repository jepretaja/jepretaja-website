import { collection, orderBy } from 'firebase/firestore';
import { useNavigate } from 'react-router-dom';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import ExportButton from '../../components/ExportButton';
import { formatCurrency, formatDateTime } from '../../utils/format';

/** Withdrawal List (section 29). */
export default function WithdrawalList() {
  const { data, loading, error } = useCollection(collection(db, PATHS.withdrawals), [orderBy('createdAt', 'desc')]);
  const navigate = useNavigate();

  return (
    <div>
      <h1 className="page-title">Withdrawals</h1>
      <div className="table-wrap">
        <div className="table-toolbar">
          <div />
          <ExportButton
            filename="withdrawals.csv"
            columns={[
              { key: 'creatorId', label: 'Creator ID' }, { key: 'amount', label: 'Jumlah' },
              { key: 'bankCode', label: 'Bank' }, { key: 'bankAccountNumber', label: 'No. Rekening' }, { key: 'status', label: 'Status' },
            ]}
            rows={data}
          />
        </div>
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          onRowClick={(row) => navigate(`/withdrawals/${row.id}`)}
          columns={[
            { key: 'creatorId', label: 'Creator ID' },
            { key: 'amount', label: 'Jumlah', render: (r) => formatCurrency(r.amount) },
            { key: 'bankReference', label: 'Referensi Bank', render: (r) => r.bankReference || '-' },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            { key: 'createdAt', label: 'Diajukan', render: (r) => formatDateTime(r.createdAt) },
          ]}
          rows={data}
        />
      </div>
    </div>
  );
}

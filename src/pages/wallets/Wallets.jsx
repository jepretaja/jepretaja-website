import { useState } from 'react';
import { collection } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import SearchInput from '../../components/SearchInput';
import StatCard from '../../components/StatCard';
import { formatCurrency } from '../../utils/format';

/** Wallets (section 22 & 29): saldo pending/available seluruh creator. */
export default function Wallets() {
  const { data, loading, error } = useCollection(collection(db, PATHS.wallets));
  const [search, setSearch] = useState('');

  const totalAvailable = data.reduce((s, w) => s + (Number(w.availableBalance) || 0), 0);
  const totalPending = data.reduce((s, w) => s + (Number(w.pendingBalance) || 0), 0);
  const filtered = data.filter((w) => w.id.toLowerCase().includes(search.toLowerCase()));

  return (
    <div>
      <h1 className="page-title">Creator Wallets</h1>
      <div className="grid grid-3" style={{ marginBottom: 16 }}>
        <StatCard label="Total Available Balance" value={formatCurrency(totalAvailable)} />
        <StatCard label="Total Pending Balance" value={formatCurrency(totalPending)} />
        <StatCard label="Jumlah Wallet" value={data.length} />
      </div>
      <div className="table-wrap">
        <div className="table-toolbar">
          <SearchInput value={search} onChange={setSearch} placeholder="Cari Creator ID..." />
        </div>
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          columns={[
            { key: 'id', label: 'Creator ID' },
            { key: 'pendingBalance', label: 'Pending', render: (r) => formatCurrency(r.pendingBalance) },
            { key: 'availableBalance', label: 'Available', render: (r) => formatCurrency(r.availableBalance) },
            { key: 'totalEarnings', label: 'Total Earnings', render: (r) => formatCurrency(r.totalEarnings) },
            { key: 'withdrawn', label: 'Withdrawn', render: (r) => formatCurrency(r.withdrawn) },
          ]}
          rows={filtered}
        />
      </div>
    </div>
  );
}

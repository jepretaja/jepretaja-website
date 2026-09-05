import { useMemo, useState } from 'react';
import { collection, orderBy } from 'firebase/firestore';
import { useNavigate } from 'react-router-dom';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import SearchInput from '../../components/SearchInput';

/** Creator List (section 20 & 29): search, filter kota/kategori/status. */
export default function CreatorList() {
  const { data, loading, error } = useCollection(collection(db, PATHS.creators), [orderBy('rating', 'desc')]);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const navigate = useNavigate();

  const filtered = useMemo(() => {
    return data.filter((c) => {
      const matchSearch = (c.displayName || '').toLowerCase().includes(search.toLowerCase()) || (c.city || '').toLowerCase().includes(search.toLowerCase());
      const matchStatus = statusFilter === 'all' || c.status === statusFilter;
      return matchSearch && matchStatus;
    });
  }, [data, search, statusFilter]);

  return (
    <div>
      <h1 className="page-title">Creators</h1>
      <div className="table-wrap">
        <div className="table-toolbar">
          <SearchInput value={search} onChange={setSearch} placeholder="Cari nama / kota..." />
          <select className="input" style={{ width: 160 }} value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="all">Semua Status</option>
            <option value="active">Active</option>
            <option value="suspended">Suspended</option>
          </select>
        </div>
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          onRowClick={(row) => navigate(`/creators/${row.id}`)}
          columns={[
            { key: 'displayName', label: 'Nama' },
            { key: 'city', label: 'Kota' },
            { key: 'categories', label: 'Kategori', render: (r) => (r.categories || []).slice(0, 2).map((c) => <span className="tag" key={c}>{c}</span>) },
            { key: 'rating', label: 'Rating', render: (r) => `${(r.rating || 0).toFixed(1)} (${r.reviewCount || 0})` },
            { key: 'verified', label: 'Verified', render: (r) => (r.verified ? <StatusBadge status="active" /> : <StatusBadge status="pending" />) },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
          ]}
          rows={filtered}
        />
      </div>
    </div>
  );
}

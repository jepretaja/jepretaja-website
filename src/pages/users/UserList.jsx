import { useMemo, useState } from 'react';
import { collection, orderBy, query } from 'firebase/firestore';
import { useNavigate } from 'react-router-dom';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import SearchInput from '../../components/SearchInput';
import ExportButton from '../../components/ExportButton';
import { formatDate } from '../../utils/format';

/** User List (section 29). */
export default function UserList() {
  const { data, loading, error } = useCollection(collection(db, PATHS.users), [orderBy('createdAt', 'desc')]);
  const [search, setSearch] = useState('');
  const navigate = useNavigate();

  const filtered = useMemo(
    () => data.filter((u) => (u.name || '').toLowerCase().includes(search.toLowerCase()) || (u.email || '').toLowerCase().includes(search.toLowerCase())),
    [data, search]
  );

  return (
    <div>
      <h1 className="page-title">Users</h1>
      <div className="table-wrap">
        <div className="table-toolbar">
          <SearchInput value={search} onChange={setSearch} placeholder="Cari nama / email..." />
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span style={{ fontSize: 12.5, color: 'var(--text-secondary)' }}>{filtered.length} pengguna</span>
            <ExportButton
              filename="users.csv"
              columns={[
                { key: 'name', label: 'Nama' }, { key: 'email', label: 'Email' }, { key: 'phone', label: 'No. HP' },
                { key: 'role', label: 'Role' }, { key: 'status', label: 'Status' },
              ]}
              rows={filtered}
            />
          </div>
        </div>
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          onRowClick={(row) => navigate(`/users/${row.id}`)}
          columns={[
            { key: 'name', label: 'Nama' },
            { key: 'email', label: 'Email' },
            { key: 'phone', label: 'No. HP' },
            { key: 'role', label: 'Role', render: (r) => <StatusBadge status={r.role} /> },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            { key: 'createdAt', label: 'Bergabung', render: (r) => formatDate(r.createdAt) },
          ]}
          rows={filtered}
        />
      </div>
    </div>
  );
}

import { useState } from 'react';
import { collection, orderBy } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';

/** Notifications (section 17 & 29): riwayat notifikasi sistem yang terkirim. */
export default function Notifications() {
  const { data, loading, error } = useCollection(collection(db, PATHS.notifications), [orderBy('createdAt', 'desc')]);
  const [typeFilter, setTypeFilter] = useState('all');

  const types = ['all', ...new Set(data.map((n) => n.type).filter(Boolean))];
  const filtered = typeFilter === 'all' ? data : data.filter((n) => n.type === typeFilter);

  return (
    <div>
      <h1 className="page-title">Notifications</h1>
      <div className="table-wrap">
        <div className="table-toolbar">
          <select className="input" style={{ width: 200 }} value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)}>
            {types.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          emptyTitle="Belum ada notifikasi terkirim"
          columns={[
            { key: 'userId', label: 'User ID' },
            { key: 'type', label: 'Tipe' },
            { key: 'title', label: 'Judul' },
            { key: 'body', label: 'Isi', render: (r) => (r.body || '').slice(0, 60) },
          ]}
          rows={filtered.slice(0, 100)}
        />
      </div>
    </div>
  );
}

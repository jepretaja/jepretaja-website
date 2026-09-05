import { useState } from 'react';
import { collection, orderBy } from 'firebase/firestore';
import { useNavigate } from 'react-router-dom';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import { formatDateTime } from '../../utils/format';

const TABS = ['pending_review', 'published', 'rejected', 'hidden'];

/** Explore Content Moderation (section 21 & 29). */
export default function ExploreModeration() {
  const { data, loading, error } = useCollection(collection(db, PATHS.explorePosts), [orderBy('createdAt', 'desc')]);
  const [tab, setTab] = useState('pending_review');
  const navigate = useNavigate();

  const filtered = data.filter((p) => p.status === tab);

  // Jumlah per status ditampilkan di tab. Tanpa ini, tab default
  // "pending_review" yang kebetulan kosong membuat halaman tampak rusak
  // padahal kontennya ada, hanya berada di status lain.
  const jumlahPerStatus = data.reduce((acc, p) => {
    acc[p.status] = (acc[p.status] || 0) + 1;
    return acc;
  }, {});

  return (
    <div>
      <h1 className="page-title">Explore Content Moderation</h1>
      <div className="pill-tabs">
        {TABS.map((t) => (
          <button key={t} className={`pill-tab${tab === t ? ' active' : ''}`} onClick={() => setTab(t)}>
            {t.replace('_', ' ')} ({jumlahPerStatus[t] || 0})
          </button>
        ))}
      </div>
      <div className="table-wrap">
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          onRowClick={(row) => navigate(`/explore/${row.id}`)}
          emptyTitle="Tidak ada konten pada status ini"
          columns={[
            { key: 'creatorName', label: 'Creator' },
            { key: 'caption', label: 'Caption', render: (r) => (r.caption || '').slice(0, 50) },
            { key: 'category', label: 'Kategori' },
            { key: 'type', label: 'Tipe' },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            { key: 'createdAt', label: 'Dibuat', render: (r) => formatDateTime(r.createdAt) },
          ]}
          rows={filtered}
        />
      </div>
    </div>
  );
}

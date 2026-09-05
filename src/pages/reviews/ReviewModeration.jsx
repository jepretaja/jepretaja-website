import { collection, doc, orderBy, updateDoc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import { useConfirm } from '../../components/ConfirmDialog';
import { logAdminAction } from '../../utils/adminActions';

/** Review Moderation (section 16 & 29). */
export default function ReviewModeration() {
  const { data, loading, error } = useCollection(collection(db, PATHS.reviews), [orderBy('createdAt', 'desc')]);
  const { confirm, dialog } = useConfirm();

  const setStatus = async (id, status, label) => {
    const ok = await confirm({ title: `${label}?`, message: 'Perubahan ini langsung terlihat oleh publik.' });
    if (!ok) return;
    await updateDoc(doc(db, PATHS.reviews, id), { status });
    await logAdminAction({ action: `review_${status}`, targetType: 'review', targetId: id }).catch(() => {});
  };

  return (
    <div>
      {dialog}
      <h1 className="page-title">Review Moderation</h1>
      <div className="table-wrap">
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          columns={[
            { key: 'customerName', label: 'Customer' },
            { key: 'creatorId', label: 'Creator ID' },
            { key: 'rating', label: 'Rating', render: (r) => '★'.repeat(r.rating || 0) },
            { key: 'text', label: 'Review', render: (r) => (r.text || '').slice(0, 60) },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            {
              key: 'actions',
              label: 'Aksi',
              render: (r) => (
                <div style={{ display: 'flex', gap: 8 }}>
                  <button className="btn btn-outline btn-sm" onClick={() => setStatus(r.id, 'hidden', 'Sembunyikan review')}>Hide</button>
                  <button className="btn btn-outline btn-sm" onClick={() => setStatus(r.id, 'published', 'Tampilkan review')}>Publish</button>
                </div>
              ),
            },
          ]}
          rows={data}
        />
      </div>
    </div>
  );
}

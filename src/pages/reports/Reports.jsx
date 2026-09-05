import { collection, doc, orderBy, updateDoc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import { useConfirm } from '../../components/ConfirmDialog';
import { logAdminAction } from '../../utils/adminActions';
import { formatDateTime } from '../../utils/format';

/** Reports (section 29): laporan konten/user yang masuk. */
export default function Reports() {
  const { data, loading, error } = useCollection(collection(db, PATHS.reports), [orderBy('createdAt', 'desc')]);
  const { confirm, dialog } = useConfirm();

  const setStatus = async (r, status, label) => {
    const ok = await confirm({ title: `${label}?`, message: `Laporan terhadap ${r.targetType} ini akan ditandai ${status}.` });
    if (!ok) return;
    await updateDoc(doc(db, PATHS.reports, r.id), { status });
    await logAdminAction({ action: `report_${status}`, targetType: r.targetType, targetId: r.targetId, reason: r.reason }).catch(() => {});
  };

  return (
    <div>
      {dialog}
      <h1 className="page-title">Reports</h1>
      <div className="table-wrap">
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          emptyTitle="Tidak ada laporan masuk"
          columns={[
            { key: 'reporterId', label: 'Pelapor' },
            { key: 'targetType', label: 'Target' },
            { key: 'targetId', label: 'Target ID', render: (r) => (r.targetId || '').slice(0, 8).toUpperCase() },
            { key: 'reason', label: 'Alasan' },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            { key: 'createdAt', label: 'Waktu', render: (r) => formatDateTime(r.createdAt) },
            {
              key: 'actions',
              label: 'Aksi',
              render: (r) => (
                <div style={{ display: 'flex', gap: 8 }}>
                  <button className="btn btn-success btn-sm" onClick={() => setStatus(r, 'resolved', 'Selesaikan laporan')}>Selesaikan</button>
                  <button className="btn btn-outline btn-sm" onClick={() => setStatus(r, 'dismissed', 'Abaikan laporan')}>Abaikan</button>
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

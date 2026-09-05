import { collection, doc, updateDoc, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import { useConfirm } from '../../components/ConfirmDialog';
import { logAdminAction } from '../../utils/adminActions';
import { formatDate } from '../../utils/format';

/** Creator Verification (section 14, 20 & 29): approve/reject dokumen verifikasi. */
export default function CreatorVerification() {
  const { data, loading, error } = useCollection(collection(db, PATHS.creatorVerifications), [where('status', '==', 'pending')]);
  const { confirm, dialog } = useConfirm();

  const decide = async (verifId, creatorId, approve) => {
    const ok = await confirm({
      title: approve ? 'Approve verifikasi creator?' : 'Reject verifikasi creator?',
      message: approve ? 'Creator akan mendapatkan badge terverifikasi.' : 'Creator perlu mengunggah ulang dokumen.',
      danger: !approve,
      confirmLabel: approve ? 'Ya, Approve' : 'Ya, Reject',
    });
    if (!ok) return;
    await updateDoc(doc(db, PATHS.creatorVerifications, verifId), {
      status: approve ? 'approved' : 'rejected',
      reviewedAt: new Date(),
    });
    if (approve) {
      await updateDoc(doc(db, PATHS.creators, creatorId), { verified: true });
    }
    await logAdminAction({ action: approve ? 'approve_creator_verification' : 'reject_creator_verification', targetType: 'creator', targetId: creatorId }).catch(() => {});
  };

  return (
    <div>
      {dialog}
      <h1 className="page-title">Creator Verification</h1>
      <div className="table-wrap">
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          emptyTitle="Tidak ada pengajuan verifikasi tertunda"
          columns={[
            { key: 'creatorId', label: 'Creator ID' },
            {
              key: 'documents', label: 'Dokumen',
              render: (r) => (r.documents || []).length
                ? <div style={{ display: 'flex', gap: 4 }}>{r.documents.slice(0, 3).map((url, i) => (
                    <a key={i} href={url} target="_blank" rel="noreferrer">
                      <img src={url} alt="dokumen" style={{ width: 36, height: 36, objectFit: 'cover', borderRadius: 4, border: '1px solid var(--border)' }} />
                    </a>
                  ))}</div>
                : <span style={{ color: 'var(--text-secondary)' }}>Tidak ada</span>,
            },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            { key: 'submittedAt', label: 'Diajukan', render: (r) => formatDate(r.submittedAt) },
            {
              key: 'actions',
              label: 'Aksi',
              render: (r) => (
                <div style={{ display: 'flex', gap: 8 }}>
                  <button className="btn btn-success btn-sm" onClick={() => decide(r.id, r.creatorId, true)}>Approve</button>
                  <button className="btn btn-danger btn-sm" onClick={() => decide(r.id, r.creatorId, false)}>Reject</button>
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

import { collection, orderBy } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import { formatCurrency } from '../../utils/format';
import { processRefund } from '../../utils/adminActions';
import { useState } from 'react';

/** Refunds (section 22 & 29). */
export default function Refunds() {
  const { data, loading, error } = useCollection(collection(db, PATHS.refunds));
  const [busy, setBusy] = useState(null);

  async function proses(refundId, action) {
    const reason = window.prompt(action === 'approve' ? 'Catatan refund (opsional)' : 'Alasan penolakan refund');
    if (action === 'reject' && !reason?.trim()) return;
    setBusy(refundId);
    try {
      await processRefund({ refundId, action, reason: reason?.trim() || null });
      window.location.reload();
    } catch (err) {
      window.alert(err.message || 'Refund gagal diproses.');
    } finally {
      setBusy(null);
    }
  }

  return (
    <div>
      <h1 className="page-title">Refunds</h1>
      <div className="table-wrap">
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          emptyTitle="Belum ada permintaan refund"
          columns={[
            { key: 'bookingId', label: 'Booking ID', render: (r) => (r.bookingId || '').slice(0, 8).toUpperCase() },
            { key: 'amount', label: 'Jumlah', render: (r) => formatCurrency(r.amount) },
            { key: 'reason', label: 'Alasan' },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            { key: 'providerReference', label: 'Referensi' },
            {
              key: 'actions', label: 'Aksi', render: (r) => r.status === 'pending' ? (
                <div style={{ display: 'flex', gap: 8 }}>
                  <button className="btn btn-primary btn-sm" disabled={busy === r.id} onClick={() => proses(r.id, 'approve')}>Setujui</button>
                  <button className="btn btn-danger btn-sm" disabled={busy === r.id} onClick={() => proses(r.id, 'reject')}>Tolak</button>
                </div>
              ) : null,
            },
          ]}
          rows={data}
        />
      </div>
    </div>
  );
}

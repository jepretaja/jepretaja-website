import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { doc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useDocument } from '../../hooks/useDocument';
import { usePermission } from '../../hooks/usePermission';
import { useConfirm } from '../../components/ConfirmDialog';
import { resolveDispute } from '../../utils/adminActions';
import StatusBadge from '../../components/StatusBadge';
import ErrorState from '../../components/ErrorState';
import { formatDateTime } from '../../utils/format';

/**
 * Dispute Detail — semua keputusan SEKARANG memanggil Cloud Function
 * `resolveDispute` (release/refund lewat escrow.js secara atomik & tercatat
 * di audit_logs), bukan update Firestore langsung.
 */
export default function DisputeDetail() {
  const { id } = useParams();
  const { data: dispute, loading, error: loadError } = useDocument(doc(db, PATHS.disputes, id));
  const { can } = usePermission();
  const { confirm, dialog } = useConfirm();
  const [processing, setProcessing] = useState(null);
  const [error, setError] = useState(null);
  const [partialAmount, setPartialAmount] = useState('');

  const decisions = [
    ['release_full', 'Release Penuh ke Creator', false],
    ['refund_full', 'Refund Penuh ke Customer', true],
    ['refund_partial', 'Refund Sebagian', true],
    ['reschedule', 'Re-schedule', false],
    ['request_evidence', 'Minta Bukti Tambahan', false],
    ['close_no_refund', 'Tutup Tanpa Refund', false],
  ];

  const decide = async (value, label, danger) => {
    if (value === 'refund_partial' && (!partialAmount || Number(partialAmount) <= 0)) {
      setError('Isi nominal refund sebagian terlebih dahulu.');
      return;
    }
    const ok = await confirm({
      title: label + '?',
      message: 'Keputusan ini akan langsung diproses dan tercatat di audit log.',
      danger,
      confirmLabel: 'Ya, Putuskan',
    });
    if (!ok) return;
    setProcessing(value);
    setError(null);
    try {
      await resolveDispute({ disputeId: id, decision: value, refundAmount: value === 'refund_partial' ? Number(partialAmount) : undefined });
    } catch (err) {
      setError(err.message || 'Gagal memproses dispute.');
    } finally {
      setProcessing(null);
    }
  };

  if (loading) return <div className="loading">Memuat...</div>;
  if (loadError) return <ErrorState error={loadError} onRetry={() => window.location.reload()} />;
  if (!dispute) return <div className="empty-state">Dispute tidak ditemukan</div>;

  const canDecide = can('manage_dispute') && dispute.status !== 'resolved';

  return (
    <div>
      {dialog}
      <div className="breadcrumb"><Link to="/disputes">Disputes</Link> / {id.slice(0, 8).toUpperCase()}</div>
      <div className="grid grid-2">
        <div className="card">
          <div className="section-title">Detail Dispute</div>
          <div className="detail-row"><span className="k">Booking ID</span><span className="v">{dispute.bookingId}</span></div>
          <div className="detail-row"><span className="k">Dibuka Oleh</span><span className="v">{dispute.openedBy}</span></div>
          <div className="detail-row"><span className="k">Alasan</span><span className="v">{dispute.reason}</span></div>
          <div className="detail-row"><span className="k">Status</span><span className="v"><StatusBadge status={dispute.status} /></span></div>
          {dispute.decision && <div className="detail-row"><span className="k">Keputusan</span><span className="v">{dispute.decision}</span></div>}
          <div className="detail-row"><span className="k">Dibuka</span><span className="v">{formatDateTime(dispute.createdAt)}</span></div>
        </div>
        <div className="card">
          <div className="section-title">Keputusan</div>
          {!can('manage_dispute') && <p style={{ fontSize: 12.5, color: 'var(--danger)' }}>Role Anda tidak memiliki izin memutuskan dispute.</p>}
          {error && <p style={{ fontSize: 12.5, color: 'var(--danger)' }}>{error}</p>}
          <input
            className="input" placeholder="Nominal refund sebagian (Rp)" style={{ marginBottom: 10 }}
            value={partialAmount} onChange={(e) => setPartialAmount(e.target.value)} type="number"
          />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {decisions.map(([value, label, danger]) => (
              <button key={value} className={`btn ${danger ? 'btn-danger' : 'btn-outline'}`} disabled={!canDecide || processing}
                onClick={() => decide(value, label, danger)}>
                {processing === value ? 'Memproses...' : label}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { doc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useDocument } from '../../hooks/useDocument';
import { usePermission } from '../../hooks/usePermission';
import { useConfirm } from '../../components/ConfirmDialog';
import { processWithdrawal, markWithdrawalManual } from '../../utils/adminActions';
import StatusBadge from '../../components/StatusBadge';
import ErrorState from '../../components/ErrorState';
import { formatCurrency, formatDateTime } from '../../utils/format';

/**
 * Withdrawal Detail — Approve/Reject memanggil Cloud Function
 * `processWithdrawal`. Untuk withdrawal yang sudah di-approve (status
 * 'processing') tapi disbursement otomatis Iris tidak tersedia (kasus
 * paling umum sebelum akun Iris disetujui Midtrans), admin bisa menandai
 * manual setelah transfer bank langsung — TANPA tombol ini, withdrawal
 * akan macet diam selamanya di status 'processing'.
 */
export default function WithdrawalDetail() {
  const { id } = useParams();
  const { data: withdrawal, loading, error: loadError } = useDocument(doc(db, PATHS.withdrawals, id));
  const { can } = usePermission();
  const { confirm, dialog } = useConfirm();
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState(null);
  const [bankReference, setBankReference] = useState('');
  const [failReason, setFailReason] = useState('');

  const handle = async (action) => {
    const ok = await confirm({
      title: action === 'approve' ? 'Approve Withdrawal?' : 'Reject Withdrawal?',
      message: action === 'approve'
        ? `Dana ${formatCurrency(withdrawal.amount)} akan dipotong dari saldo creator dan diproses ke rekening bank.`
        : 'Permintaan withdrawal ini akan ditolak dan tidak diproses.',
      danger: action === 'reject',
      confirmLabel: action === 'approve' ? 'Ya, Approve' : 'Ya, Reject',
    });
    if (!ok) return;
    setProcessing(true);
    setError(null);
    try {
      await processWithdrawal({ withdrawalId: id, action });
    } catch (err) {
      setError(err.message || 'Gagal memproses withdrawal.');
    } finally {
      setProcessing(false);
    }
  };

  const handleManual = async (action) => {
    const ok = await confirm({
      title: action === 'success' ? 'Tandai Transfer Selesai?' : 'Tandai Transfer Gagal?',
      message: action === 'success'
        ? 'Konfirmasi bahwa Anda sudah mentransfer dana ke rekening creator secara manual.'
        : `Saldo ${formatCurrency(withdrawal.amount)} akan DIKEMBALIKAN ke Available Balance creator.`,
      danger: action === 'failed',
      confirmLabel: action === 'success' ? 'Ya, Sudah Transfer' : 'Ya, Kembalikan Saldo',
    });
    if (!ok) return;
    setProcessing(true);
    setError(null);
    try {
      await markWithdrawalManual({
        withdrawalId: id, action,
        bankReference: action === 'success' ? bankReference : undefined,
        reason: action === 'failed' ? failReason : undefined,
      });
    } catch (err) {
      setError(err.message || 'Gagal memproses.');
    } finally {
      setProcessing(false);
    }
  };

  if (loading) return <div className="loading">Memuat...</div>;
  if (loadError) return <ErrorState error={loadError} onRetry={() => window.location.reload()} />;
  if (!withdrawal) return <div className="empty-state">Withdrawal tidak ditemukan</div>;

  const canProcess = can('manage_withdrawal');
  const isRequested = withdrawal.status === 'requested';
  const isProcessing = withdrawal.status === 'processing';

  return (
    <div>
      {dialog}
      <div className="breadcrumb"><Link to="/withdrawals">Withdrawals</Link> / {id.slice(0, 8).toUpperCase()}</div>
      <div className="grid grid-2">
        <div className="card">
          <div className="section-title">Detail</div>
          <div className="detail-row"><span className="k">Creator ID</span><span className="v">{withdrawal.creatorId}</span></div>
          <div className="detail-row"><span className="k">Jumlah</span><span className="v num">{formatCurrency(withdrawal.amount)}</span></div>
          <div className="detail-row"><span className="k">Bank</span><span className="v">{withdrawal.bankCode || '-'}</span></div>
          <div className="detail-row"><span className="k">No. Rekening</span><span className="v">{withdrawal.bankAccountNumber || '-'}</span></div>
          <div className="detail-row"><span className="k">Nama Pemilik</span><span className="v">{withdrawal.bankAccountName || '-'}</span></div>
          <div className="detail-row"><span className="k">Status</span><span className="v"><StatusBadge status={withdrawal.status} /></span></div>
          <div className="detail-row"><span className="k">Diajukan</span><span className="v">{formatDateTime(withdrawal.createdAt)}</span></div>
          {withdrawal.bankReference && <div className="detail-row"><span className="k">Referensi Transfer</span><span className="v">{withdrawal.bankReference}</span></div>}
          {withdrawal.failureReason && <div className="detail-row"><span className="k">Alasan Gagal</span><span className="v">{withdrawal.failureReason}</span></div>}
        </div>
        <div className="card">
          <div className="section-title">Proses</div>
          {!canProcess && <p className="text-danger-sm">Role Anda tidak memiliki izin memproses withdrawal.</p>}
          {error && <p className="text-danger-sm">{error}</p>}

          {isRequested && (
            <div className="flex-row">
              <button className="btn btn-success" disabled={!canProcess || processing} onClick={() => handle('approve')}>
                {processing ? 'Memproses...' : 'Approve'}
              </button>
              <button className="btn btn-danger" disabled={!canProcess || processing} onClick={() => handle('reject')}>Reject</button>
            </div>
          )}

          {isProcessing && (
            <div>
              <p className="text-meta mb-sm">
                Bila disbursement otomatis (Midtrans Iris) belum aktif, transfer manual lewat internet banking lalu tandai di sini.
              </p>
              <input className="input mb-sm" placeholder="Nomor referensi transfer (opsional)" value={bankReference} onChange={(e) => setBankReference(e.target.value)} />
              <button className="btn btn-success btn-block mb-md" disabled={!canProcess || processing} onClick={() => handleManual('success')}>
                Tandai Selesai (Transfer Manual)
              </button>
              <input className="input mb-sm" placeholder="Alasan gagal (mis. rekening tidak valid)" value={failReason} onChange={(e) => setFailReason(e.target.value)} />
              <button className="btn btn-danger btn-block" disabled={!canProcess || processing} onClick={() => handleManual('failed')}>
                Tandai Gagal (Kembalikan Saldo)
              </button>
            </div>
          )}

          {!isRequested && !isProcessing && (
            <p className="text-meta">Withdrawal ini sudah final ({withdrawal.status}), tidak ada aksi lagi.</p>
          )}
        </div>
      </div>
    </div>
  );
}

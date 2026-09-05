import { useState } from 'react';
import { collection, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { useAuth } from '../../auth/AuthContext';
import { hasPermission } from '../../auth/permissions';
import DataTable from '../../components/DataTable';
import { useConfirm } from '../../components/ConfirmDialog';
import { confirmManualPayment } from '../../utils/adminActions';
import { formatCurrency, formatDateTime } from '../../utils/format';
import { byNewest } from '../../utils/sort';

/**
 * Verifikasi transfer manual yang masuk.
 *
 * Ini titik penyambung alur pembayaran: aplikasi menerbitkan instruksi
 * transfer berstatus "awaiting_transfer", dan booking baru berpindah ke
 * "paid" setelah admin mencocokkan uang masuk di sini.
 *
 * Nominal yang dicocokkan adalah kolom "Transfer" (sudah termasuk kode
 * unik), bukan kolom "Nilai" — kode unik itulah yang membedakan dua
 * transfer bernominal sama dari dua pelanggan berbeda.
 */
export default function ManualTransfers() {
  const { adminProfile } = useAuth();
  const boleh = hasPermission(adminProfile?.role, 'manage_payment');

  // Pengurutan dilakukan di klien (lihat utils/sort.js): where + orderBy di
  // field berbeda menuntut composite index, dan tanpa index itu query gagal
  // total sehingga halaman ini hanya menampilkan pesan error.
  const { data: rows, loading, error } = useCollection(
    collection(db, PATHS.payments),
    [where('status', '==', 'awaiting_transfer')]
  );
  const data = byNewest(rows);

  const [prosesId, setProsesId] = useState(null);
  const [pesan, setPesan] = useState(null);
  const { confirm, dialog } = useConfirm();

  const jalankan = async (row, action) => {
    const paymentId = row.id;
    const ok = await confirm({
      title: action === 'confirm' ? 'Konfirmasi Pembayaran?' : 'Tolak Pembayaran?',
      message: action === 'confirm'
        ? `Pastikan dana sebesar ${formatCurrency(row.transferAmount || row.amount)} sudah benar-benar masuk ` +
          'ke rekening. Setelah dikonfirmasi, booking berpindah ke status Paid dan dana ditahan di escrow.'
        : 'Pembayaran ditandai gagal dan pelanggan diminta mengulang transfer.',
      danger: action === 'reject',
      confirmLabel: action === 'confirm' ? 'Ya, Dana Sudah Masuk' : 'Ya, Tolak',
    });
    if (!ok) return;

    setProsesId(paymentId);
    setPesan(null);
    try {
      await confirmManualPayment({ paymentId, action });
      setPesan({
        tipe: 'sukses',
        teks: action === 'confirm'
          ? 'Pembayaran dikonfirmasi. Dana ditahan di escrow sampai pekerjaan selesai.'
          : 'Pembayaran ditolak.',
      });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message });
    } finally {
      setProsesId(null);
    }
  };

  return (
    <div>
      <h1 className="page-title">Verifikasi Transfer Manual</h1>
      <p className="text-meta" style={{ marginBottom: 16 }}>
        Cocokkan nominal kolom <strong>Transfer</strong> (sudah termasuk kode unik)
        dengan mutasi rekening sebelum mengonfirmasi.
      </p>

      {pesan && (
        <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
          <div className="msg">{pesan.teks}</div>
        </div>
      )}

      <div className="table-wrap">
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          columns={[
            { key: 'bookingId', label: 'Booking' },
            { key: 'amount', label: 'Nilai', render: (r) => formatCurrency(r.amount) },
            {
              key: 'transferAmount',
              label: 'Transfer',
              render: (r) => (
                <strong>{formatCurrency(r.transferAmount || r.amount)}</strong>
              ),
            },
            { key: 'uniqueCode', label: 'Kode Unik', render: (r) => r.uniqueCode ?? '-' },
            { key: 'createdAt', label: 'Dibuat', render: (r) => formatDateTime(r.createdAt) },
            {
              key: 'aksi',
              label: 'Aksi',
              render: (r) => (
                <div style={{ display: 'flex', gap: 8 }}>
                  <button
                    className="btn btn-primary btn-sm"
                    disabled={!boleh || prosesId === r.id}
                    onClick={() => jalankan(r, 'confirm')}
                  >
                    {prosesId === r.id ? '...' : 'Konfirmasi'}
                  </button>
                  <button
                    className="btn btn-outline btn-sm"
                    disabled={!boleh || prosesId === r.id}
                    onClick={() => jalankan(r, 'reject')}
                  >
                    Tolak
                  </button>
                </div>
              ),
            },
          ]}
          rows={data}
          emptyTitle="Tidak ada transfer yang menunggu verifikasi"
        />
      </div>

      {!boleh && (
        <p className="text-meta" style={{ marginTop: 12 }}>
          Peran Anda tidak memiliki izin <code>manage_payment</code>, jadi tombol aksi dinonaktifkan.
        </p>
      )}
      {dialog}
    </div>
  );
}

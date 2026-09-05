import { useMemo, useState } from 'react';
import { collection, doc, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { useDocument } from '../../hooks/useDocument';
import { useAuth } from '../../auth/AuthContext';
import StatCard from '../../components/StatCard';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import EmptyState from '../../components/EmptyState';
import { useConfirm } from '../../components/ConfirmDialog';
import {
  cancelPlatformPayout, syncPlatformRevenue, withdrawPlatformBalance,
} from '../../utils/adminActions';
import { formatCurrency, formatDateTime } from '../../utils/format';
import { byNewest, toMillis } from '../../utils/sort';

const BANK = ['BCA', 'BNI', 'BRI', 'Mandiri', 'BSI', 'CIMB Niaga', 'Permata', 'Danamon', 'BTN'];

const FORM_KOSONG = { amount: '', bankCode: 'BCA', bankLain: '', bankAccountNumber: '', bankAccountName: '', note: '' };

/**
 * Kas platform: komisi yang terkumpul, dan pencairannya ke rekening pemilik.
 *
 * Komisi (`platformFee`) sudah lama dihitung di setiap booking, tapi saat dana
 * escrow dilepas hanya bagian creator yang berpindah — bagian platform hanya
 * tercatat sebagai angka di dokumen escrow, tanpa saldo dan tanpa satu pun cara
 * mencairkannya. Halaman ini menutup selisih itu.
 *
 * Angkanya sengaja disajikan berdampingan dengan hitungan mentah dari escrow,
 * dan tombol "Hitung Ulang" menyusun ulang saldo dari catatan aslinya. Untuk
 * uang, angka yang tidak bisa dibuktikan ulang dari sumbernya bukan angka yang
 * layak dipercaya.
 *
 * Halaman ini hanya bisa dibuka super_admin — server menolak seluruh aksinya
 * untuk peran lain, apa pun yang terlihat di UI.
 */
export default function PlatformRevenue() {
  const { adminProfile } = useAuth();
  const { confirm, dialog } = useConfirm();

  const { data: kas, loading: loadKas } = useDocument(doc(db, PATHS.platformWallet, 'main'));
  const { data: payouts, loading: loadPayout, error: errPayout } = useCollection(
    collection(db, PATHS.platformPayouts)
  );
  // Sumber asli komisi: escrow yang dananya sudah dilepas.
  const { data: escrow } = useCollection(
    collection(db, PATHS.escrowTransactions), [where('releaseStatus', '==', 'released')]
  );

  const [form, setForm] = useState(FORM_KOSONG);
  const [bukaForm, setBukaForm] = useState(false);
  const [sibuk, setSibuk] = useState(null);
  const [pesan, setPesan] = useState(null);

  const riwayat = byNewest(payouts);

  // Dihitung ulang di browser dari dokumen escrow yang sama yang dipakai
  // server. Kalau angkanya berbeda dari saldo tersimpan, itu tanda ada escrow
  // lama yang dilepas sebelum kas platform ada — dan tombol Hitung Ulang
  // yang memperbaikinya.
  const { komisiMentah, komisiBulanIni } = useMemo(() => {
    const awalBulan = new Date();
    awalBulan.setDate(1);
    awalBulan.setHours(0, 0, 0, 0);
    let total = 0;
    let bulanIni = 0;
    escrow.forEach((e) => {
      const fee = Math.floor(Number(e.platformFee) || 0);
      total += fee;
      if (toMillis(e.releasedAt) >= awalBulan.getTime()) bulanIni += fee;
    });
    return { komisiMentah: total, komisiBulanIni: bulanIni };
  }, [escrow]);

  const dicairkan = riwayat
    .filter((p) => p.status === 'paid')
    .reduce((t, p) => t + (Number(p.amount) || 0), 0);

  const tersedia = Number(kas?.availableBalance) || 0;
  const totalRevenue = Number(kas?.totalRevenue) || 0;
  const belumPernahDihitung = !loadKas && !kas;
  const selisih = !belumPernahDihitung && totalRevenue !== komisiMentah;

  const namaBank = () => (form.bankCode === 'Lainnya' ? form.bankLain.trim() : form.bankCode);

  const hitungUlang = async () => {
    setSibuk('sync');
    setPesan(null);
    try {
      const hasil = await syncPlatformRevenue({});
      setPesan({
        tipe: 'sukses',
        teks: `Kas platform dihitung ulang dari ${hasil.escrowDihitung} escrow: komisi ${formatCurrency(hasil.totalRevenue)}, sudah dicairkan ${formatCurrency(hasil.totalPaidOut)}, tersisa ${formatCurrency(hasil.availableBalance)}.`,
      });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message });
    } finally {
      setSibuk(null);
    }
  };

  const cairkan = async (e) => {
    e.preventDefault();
    setPesan(null);

    const jumlah = Math.floor(Number(form.amount) || 0);
    if (jumlah <= 0) { setPesan({ tipe: 'gagal', teks: 'Isi nominal pencairan.' }); return; }
    if (jumlah > tersedia) {
      setPesan({ tipe: 'gagal', teks: `Saldo platform hanya ${formatCurrency(tersedia)}.` });
      return;
    }
    const bank = namaBank();
    if (!bank) { setPesan({ tipe: 'gagal', teks: 'Nama bank wajib diisi.' }); return; }
    const nomor = form.bankAccountNumber.replace(/[\s-]/g, '');
    if (!/^\d{6,20}$/.test(nomor)) {
      setPesan({ tipe: 'gagal', teks: 'Nomor rekening harus 6–20 angka.' });
      return;
    }
    if (!form.bankAccountName.trim()) {
      setPesan({ tipe: 'gagal', teks: 'Nama pemilik rekening wajib diisi.' });
      return;
    }

    const ok = await confirm({
      title: `Cairkan ${formatCurrency(jumlah)} dari kas platform?`,
      message: `Dana dikirim ke ${bank} ${nomor} a.n. ${form.bankAccountName.trim()}. Transfer banknya Anda lakukan sendiri — panel hanya mencatat pembukuannya. Pencairan ini tercatat permanen di Audit Logs beserta nama Anda.`,
      confirmLabel: 'Ya, Cairkan',
    });
    if (!ok) return;

    setSibuk('cairkan');
    try {
      await withdrawPlatformBalance({
        amount: jumlah,
        bankCode: bank,
        bankAccountNumber: nomor,
        bankAccountName: form.bankAccountName.trim(),
        note: form.note.trim(),
      });
      setForm(FORM_KOSONG);
      setBukaForm(false);
      setPesan({ tipe: 'sukses', teks: `Pencairan ${formatCurrency(jumlah)} tercatat. Jangan lupa lakukan transfer banknya.` });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message });
    } finally {
      setSibuk(null);
    }
  };

  const batalkan = async (p) => {
    const ok = await confirm({
      title: `Batalkan catatan pencairan ${formatCurrency(p.amount)}?`,
      message: 'Nominalnya dikembalikan ke saldo platform. Gunakan ini hanya kalau transfernya memang tidak jadi dilakukan — barisnya tetap tersimpan sebagai riwayat, tidak dihapus.',
      danger: true,
      confirmLabel: 'Ya, Batalkan',
    });
    if (!ok) return;
    setSibuk(p.id);
    try {
      await cancelPlatformPayout({ payoutId: p.id, reason: 'dibatalkan dari panel' });
      setPesan({ tipe: 'sukses', teks: 'Pencairan dibatalkan dan saldo dikembalikan.' });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message });
    } finally {
      setSibuk(null);
    }
  };

  return (
    <div>
      {dialog}
      <h1 className="page-title">Kas Platform</h1>
      <p className="text-meta" style={{ marginBottom: 20 }}>
        Komisi dari setiap booking yang dananya sudah dilepas, dan pencairannya ke rekening pemilik.
        Halaman ini hanya bisa dibuka super admin.
      </p>

      {pesan && (
        <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
          <div className="msg">{pesan.teks}</div>
        </div>
      )}

      {belumPernahDihitung && (
        <div className="card banner-danger">
          <div className="msg">
            Kas platform belum pernah dihitung. Tekan “Hitung Ulang” untuk menyusunnya dari data escrow yang sudah ada.
          </div>
        </div>
      )}

      {selisih && (
        <div className="card banner-danger">
          <div className="msg">
            Saldo tersimpan ({formatCurrency(totalRevenue)}) berbeda dari jumlah komisi di data escrow
            ({formatCurrency(komisiMentah)}). Ini wajar kalau ada dana yang dilepas sebelum kas platform
            dibuat — tekan “Hitung Ulang” untuk menyamakannya.
          </div>
        </div>
      )}

      <div className="grid grid-4 mb-lg">
        <StatCard
          label="Saldo Bisa Dicairkan" value={formatCurrency(tersedia)}
          icon="wallet" tone="sukses" delta="Komisi terkumpul dikurangi pencairan"
        />
        <StatCard
          label="Total Komisi" value={formatCurrency(totalRevenue)}
          icon="money" tone="utama" delta={`${escrow.length} booking selesai`}
        />
        <StatCard
          label="Sudah Dicairkan" value={formatCurrency(dicairkan)}
          icon="download" tone="info" delta={`${riwayat.filter((p) => p.status === 'paid').length} pencairan`}
        />
        <StatCard
          label="Komisi Bulan Ini" value={formatCurrency(komisiBulanIni)}
          icon="chart" tone="peringatan"
        />
      </div>

      <div className="card mb-lg">
        <div className="flex-between mb-md">
          <div>
            <div className="section-title" style={{ marginBottom: 2 }}>Cairkan Saldo</div>
            <div className="text-meta">
              Terakhir dihitung ulang: {kas?.lastSyncAt ? formatDateTime(kas.lastSyncAt) : 'belum pernah'}
            </div>
          </div>
          <div className="flex-row">
            <button className="btn btn-outline btn-sm" onClick={hitungUlang} disabled={sibuk === 'sync'}>
              {sibuk === 'sync' ? 'Menghitung...' : 'Hitung Ulang'}
            </button>
            {!bukaForm && (
              <button className="btn btn-primary btn-sm" onClick={() => setBukaForm(true)} disabled={tersedia <= 0}>
                + Catat Pencairan
              </button>
            )}
          </div>
        </div>

        {bukaForm ? (
          <form onSubmit={cairkan}>
            <div className="form-row mb-md">
              <div style={{ flex: 1 }}>
                <label className="field-label">Nominal (Rp)</label>
                <input className="input" type="number" inputMode="numeric" value={form.amount}
                  onChange={(e) => setForm({ ...form, amount: e.target.value })} />
                <div className="text-meta" style={{ marginTop: 6 }}>
                  Tersedia {formatCurrency(tersedia)}
                  {tersedia > 0 && (
                    <button type="button" className="pemilih-tautan" style={{ marginLeft: 8 }}
                      onClick={() => setForm({ ...form, amount: String(tersedia) })}>
                      cairkan semua
                    </button>
                  )}
                </div>
              </div>
              <div style={{ flex: 1 }}>
                <label className="field-label">Bank</label>
                <select className="input" value={form.bankCode}
                  onChange={(e) => setForm({ ...form, bankCode: e.target.value })}>
                  {BANK.map((b) => <option key={b} value={b}>{b}</option>)}
                  <option value="Lainnya">Lainnya...</option>
                </select>
              </div>
              {form.bankCode === 'Lainnya' && (
                <div style={{ flex: 1 }}>
                  <label className="field-label">Nama Bank</label>
                  <input className="input" value={form.bankLain}
                    onChange={(e) => setForm({ ...form, bankLain: e.target.value })} />
                </div>
              )}
            </div>
            <div className="form-row mb-md">
              <div style={{ flex: 1 }}>
                <label className="field-label">Nomor Rekening</label>
                <input className="input" inputMode="numeric" value={form.bankAccountNumber}
                  onChange={(e) => setForm({ ...form, bankAccountNumber: e.target.value })} />
              </div>
              <div style={{ flex: 1 }}>
                <label className="field-label">Nama Pemilik Rekening</label>
                <input className="input" value={form.bankAccountName}
                  onChange={(e) => setForm({ ...form, bankAccountName: e.target.value })} />
              </div>
            </div>
            <div className="mb-md">
              <label className="field-label">Catatan (opsional)</label>
              <input className="input" value={form.note} placeholder="mis. penarikan komisi Agustus"
                onChange={(e) => setForm({ ...form, note: e.target.value })} />
            </div>
            <div className="flex-row">
              <button className="btn btn-primary" type="submit" disabled={sibuk === 'cairkan'}>
                {sibuk === 'cairkan' ? 'Menyimpan...' : 'Catat Pencairan'}
              </button>
              <button className="btn btn-outline" type="button" onClick={() => { setBukaForm(false); setPesan(null); }}>
                Batal
              </button>
            </div>
          </form>
        ) : (
          <p className="text-meta" style={{ margin: 0 }}>
            Transfer banknya dilakukan manual di luar sistem — panel ini mencatat pembukuannya:
            saldo dipotong, riwayat dibuat, dan pencairannya tercatat di Audit Logs atas nama{' '}
            <strong>{adminProfile?.name || adminProfile?.email || 'Anda'}</strong>.
          </p>
        )}
      </div>

      <h2 className="section-title" style={{ marginTop: 28 }}>Riwayat Pencairan</h2>
      <div className="table-wrap">
        {!loadPayout && !errPayout && riwayat.length === 0 ? (
          <EmptyState
            icon="download"
            title="Belum ada pencairan"
            hint="Setiap pencairan yang dicatat di sini akan muncul beserta rekening tujuan dan siapa yang mencatatnya."
          />
        ) : (
          <DataTable
            loading={loadPayout}
            error={errPayout}
            onRetry={() => window.location.reload()}
            columns={[
              { key: 'amount', label: 'Nominal', render: (r) => <span className="num">{formatCurrency(r.amount)}</span> },
              { key: 'bankCode', label: 'Bank', render: (r) => r.bankCode || '-' },
              { key: 'bankAccountNumber', label: 'Rekening', render: (r) => `${r.bankAccountNumber || '-'} · ${r.bankAccountName || '-'}` },
              { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
              { key: 'requestedByEmail', label: 'Dicatat Oleh', render: (r) => r.requestedByEmail || r.requestedBy || '-' },
              { key: 'createdAt', label: 'Waktu', render: (r) => formatDateTime(r.createdAt) },
              { key: 'note', label: 'Catatan', render: (r) => r.note || r.cancelReason || '-' },
              {
                key: 'aksi', label: 'Aksi',
                render: (r) => (r.status === 'paid' ? (
                  <button className="btn btn-outline btn-sm" disabled={sibuk === r.id} onClick={() => batalkan(r)}>
                    {sibuk === r.id ? '...' : 'Batalkan'}
                  </button>
                ) : <span className="text-meta">-</span>),
              },
            ]}
            rows={riwayat}
          />
        )}
      </div>
    </div>
  );
}

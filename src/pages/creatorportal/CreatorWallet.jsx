import { useEffect, useState } from 'react';
import { collection, doc, setDoc, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { useDocument } from '../../hooks/useDocument';
import { useAuth } from '../../auth/AuthContext';
import StatCard from '../../components/StatCard';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import { useConfirm } from '../../components/ConfirmDialog';
import { requestWithdrawal } from '../../utils/appActions';
import { formatCurrency, formatDateTime } from '../../utils/format';
import { byNewest } from '../../utils/sort';

/** Bank yang lazim dipakai. Selalu ada pilihan "Lainnya" karena daftar tetap
 *  seperti ini pasti ketinggalan bank baru, dan creator tidak boleh terjebak
 *  hanya karena banknya tidak ada di daftar. */
const BANK = ['BCA', 'BNI', 'BRI', 'Mandiri', 'BSI', 'CIMB Niaga', 'Permata', 'Danamon', 'BTN', 'Jago', 'SeaBank', 'Dana', 'OVO', 'GoPay', 'ShopeePay'];

const MIN_PENARIKAN_BAWAAN = 100000;

/** Status pengajuan yang masih berjalan — sama dengan yang diperiksa server
 *  di api/_lib/actions/appWallet.js. */
const STATUS_BERJALAN = ['requested', 'processing'];

const REKENING_KOSONG = { bankCode: 'BCA', bankLain: '', bankAccountNumber: '', bankAccountName: '' };

/**
 * Saldo, rekening penarikan, dan riwayat mutasi milik creator ini.
 *
 * Sebelumnya halaman ini hanya bisa DILIHAT: saldo tampil, riwayat tampil,
 * tapi tidak ada satu pun cara mencairkan uangnya dari web — dan tidak ada
 * tempat menyimpan nomor rekening sama sekali. Uang yang terlihat tapi tidak
 * bisa ditarik adalah kegagalan yang paling merugikan kepercayaan di sebuah
 * marketplace, jadi keduanya ditambahkan di sini.
 *
 * DUA HAL PENTING SOAL TEMPAT DATA:
 *
 *  1. Rekening disimpan di `users/{uid}`, BUKAN di `creators/{uid}`. Dokumen
 *     creator dibaca publik (`allow read: if true` — itu etalase marketplace),
 *     sehingga menaruh nomor rekening di sana berarti menyiarkannya ke siapa
 *     saja. Dokumen users hanya bisa dibaca pemiliknya sendiri dan admin.
 *  2. Pengajuan penarikan dikirim ke /api/app, bukan ditulis ke Firestore.
 *     Koleksi `withdrawals` tertutup total untuk klien (`allow write: if
 *     false`), dan server yang memeriksa nominal minimum, saldo yang benar-
 *     benar tersedia, serta memastikan tidak ada pengajuan lain yang masih
 *     berjalan. Ketiganya tidak bisa ditegakkan di security rules.
 */
export default function CreatorWallet() {
  const { user, creatorProfile } = useAuth();
  const uid = user?.uid;
  const { confirm, dialog } = useConfirm();

  const { data: wallet } = useDocument(uid ? doc(db, PATHS.wallets, uid) : null);
  // `loading` ikut dipakai, bukan hanya `data`: sebagian akun creator lama
  // belum punya dokumen users sama sekali, dan dokumen yang tidak ada juga
  // mengembalikan data null. Tanpa membedakan keduanya, formulir rekening
  // akan macet di "Memuat rekening..." selamanya untuk akun-akun itu —
  // tepatnya akun yang paling membutuhkan formulir ini.
  const { data: profilUser, loading: loadProfil } = useDocument(uid ? doc(db, PATHS.users, uid) : null);
  const { data: pengaturan } = useDocument(doc(db, PATHS.settings, 'general'));

  // Urutan terbaru dihitung di klien (lihat utils/sort.js) — where + orderBy
  // di field berbeda menuntut composite index, dan tanpa index itu query
  // gagal total sehingga halaman ini hanya menampilkan pesan error.
  const { data: mutasiRaw, loading: loadMutasi, error: errMutasi } = useCollection(
    collection(db, PATHS.walletTransactions),
    uid ? [where('creatorId', '==', uid)] : []
  );
  const { data: penarikanRaw, loading: loadTarik, error: errTarik } = useCollection(
    collection(db, PATHS.withdrawals),
    uid ? [where('creatorId', '==', uid)] : []
  );
  const mutasi = byNewest(mutasiRaw);
  const penarikan = byNewest(penarikanRaw);

  const [rekening, setRekening] = useState(null);
  const [ubahRekening, setUbahRekening] = useState(false);
  const [menyimpanRekening, setMenyimpanRekening] = useState(false);
  const [nominal, setNominal] = useState('');
  const [mengajukan, setMengajukan] = useState(false);
  const [pesan, setPesan] = useState(null);

  // Formulir hanya diisi ulang dari server selama creator belum menyentuhnya,
  // supaya ketikan yang sedang berjalan tidak tertimpa oleh snapshot baru.
  useEffect(() => {
    if (rekening || loadProfil) return;
    const tersimpan = profilUser?.payoutAccount;
    if (tersimpan) {
      setRekening({
        bankCode: BANK.includes(tersimpan.bankCode) ? tersimpan.bankCode : 'Lainnya',
        bankLain: BANK.includes(tersimpan.bankCode) ? '' : (tersimpan.bankCode || ''),
        bankAccountNumber: tersimpan.bankAccountNumber || '',
        bankAccountName: tersimpan.bankAccountName || '',
      });
    } else {
      setRekening({ ...REKENING_KOSONG, bankAccountName: creatorProfile?.displayName || '' });
    }
  }, [profilUser, loadProfil, rekening, creatorProfile]);

  const rekeningTersimpan = profilUser?.payoutAccount || null;
  const rekeningLengkap = Boolean(
    rekeningTersimpan?.bankCode && rekeningTersimpan?.bankAccountNumber && rekeningTersimpan?.bankAccountName
  );

  const tersedia = Number(wallet?.availableBalance) || 0;
  const minPenarikan = Number(pengaturan?.minWithdrawal) || MIN_PENARIKAN_BAWAAN;
  const adaYangBerjalan = penarikan.some((p) => STATUS_BERJALAN.includes(p.status));

  const namaBank = (r) => (r.bankCode === 'Lainnya' ? r.bankLain.trim() : r.bankCode);

  const simpanRekening = async (e) => {
    e.preventDefault();
    setPesan(null);

    const bank = namaBank(rekening);
    if (!bank) { setPesan({ tipe: 'gagal', teks: 'Nama bank wajib diisi.' }); return; }
    const nomor = rekening.bankAccountNumber.replace(/[\s-]/g, '');
    if (!/^\d{6,20}$/.test(nomor)) {
      setPesan({ tipe: 'gagal', teks: 'Nomor rekening harus 6–20 angka, tanpa huruf.' });
      return;
    }
    if (!rekening.bankAccountName.trim()) {
      setPesan({ tipe: 'gagal', teks: 'Nama pemilik rekening wajib diisi.' });
      return;
    }

    setMenyimpanRekening(true);
    try {
      // setDoc + merge, bukan updateDoc: sebagian akun creator lama belum
      // punya dokumen users sama sekali, dan updateDoc pada dokumen yang
      // belum ada gagal dengan 'not-found'.
      await setDoc(
        doc(db, PATHS.users, uid),
        {
          payoutAccount: {
            bankCode: bank,
            bankAccountNumber: nomor,
            bankAccountName: rekening.bankAccountName.trim(),
            updatedAt: new Date(),
          },
        },
        { merge: true }
      );
      setUbahRekening(false);
      setPesan({ tipe: 'sukses', teks: 'Rekening penarikan tersimpan.' });
    } catch (err) {
      setPesan({
        tipe: 'gagal',
        teks: err.code === 'permission-denied'
          ? 'Perubahan ditolak server. Coba keluar lalu masuk kembali.'
          : err.message || 'Gagal menyimpan rekening.',
      });
    } finally {
      setMenyimpanRekening(false);
    }
  };

  const ajukan = async (e) => {
    e.preventDefault();
    setPesan(null);

    const jumlah = Math.floor(Number(nominal) || 0);
    if (jumlah <= 0) { setPesan({ tipe: 'gagal', teks: 'Isi nominal penarikan.' }); return; }
    if (jumlah < minPenarikan) {
      setPesan({ tipe: 'gagal', teks: `Penarikan minimal ${formatCurrency(minPenarikan)}.` });
      return;
    }
    if (jumlah > tersedia) {
      setPesan({ tipe: 'gagal', teks: `Saldo tersedia hanya ${formatCurrency(tersedia)}.` });
      return;
    }

    const ok = await confirm({
      title: `Ajukan penarikan ${formatCurrency(jumlah)}?`,
      message: `Dana dikirim ke ${rekeningTersimpan.bankCode} ${rekeningTersimpan.bankAccountNumber} a.n. ${rekeningTersimpan.bankAccountName}. Pastikan datanya benar — transfer ke rekening yang salah tidak bisa ditarik kembali.`,
      confirmLabel: 'Ya, Ajukan',
    });
    if (!ok) return;

    setMengajukan(true);
    try {
      await requestWithdrawal({
        amount: jumlah,
        bankCode: rekeningTersimpan.bankCode,
        bankAccountNumber: rekeningTersimpan.bankAccountNumber,
        bankAccountName: rekeningTersimpan.bankAccountName,
      });
      setNominal('');
      setPesan({ tipe: 'sukses', teks: 'Pengajuan terkirim. Admin akan memprosesnya dalam 1–3 hari kerja.' });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message });
    } finally {
      setMengajukan(false);
    }
  };

  return (
    <div>
      {dialog}
      <h1 className="page-title">Saldo & Penarikan</h1>

      {pesan && (
        <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
          <div className="msg">{pesan.teks}</div>
        </div>
      )}

      <div className="grid grid-3 mb-lg">
        <StatCard label="Saldo Tersedia" value={formatCurrency(tersedia)} delta="Bisa ditarik sekarang" icon="wallet" tone="sukses" />
        <StatCard
          label="Saldo Tertahan"
          value={formatCurrency(wallet?.pendingBalance || 0)}
          delta="Menunggu pekerjaan selesai"
          icon="lock" tone="peringatan"
        />
        <StatCard label="Total Pendapatan" value={formatCurrency(wallet?.totalEarnings || 0)} icon="chart" tone="utama" />
      </div>

      <div className="grid grid-2 mb-lg">
        <div className="card">
          <div className="flex-between mb-md">
            <div className="section-title" style={{ marginBottom: 0 }}>Rekening Penarikan</div>
            {rekeningLengkap && !ubahRekening && (
              <button className="btn btn-outline btn-sm" onClick={() => setUbahRekening(true)}>Ubah</button>
            )}
          </div>

          {rekeningLengkap && !ubahRekening ? (
            <>
              <div className="detail-row"><span>Bank</span><span>{rekeningTersimpan.bankCode}</span></div>
              <div className="detail-row"><span>Nomor Rekening</span><span className="num">{rekeningTersimpan.bankAccountNumber}</span></div>
              <div className="detail-row"><span>Atas Nama</span><span>{rekeningTersimpan.bankAccountName}</span></div>
              <p className="text-meta" style={{ marginBottom: 0, marginTop: 12 }}>
                Nomor rekening ini hanya bisa dilihat oleh Anda dan tim JepretAja — tidak tampil di profil publik.
              </p>
            </>
          ) : rekening ? (
            <form onSubmit={simpanRekening}>
              {!rekeningLengkap && (
                <p className="text-meta" style={{ marginTop: 0 }}>
                  Isi rekening dulu sebelum bisa mengajukan penarikan. Pastikan nama pemilik sama persis dengan
                  buku tabungan — bank menolak transfer yang namanya tidak cocok.
                </p>
              )}
              <div className="form-row mb-md">
                <div style={{ flex: 1 }}>
                  <label className="field-label">Bank / E-Wallet</label>
                  <select className="input" value={rekening.bankCode}
                    onChange={(e) => setRekening({ ...rekening, bankCode: e.target.value })}>
                    {BANK.map((b) => <option key={b} value={b}>{b}</option>)}
                    <option value="Lainnya">Lainnya...</option>
                  </select>
                </div>
                {rekening.bankCode === 'Lainnya' && (
                  <div style={{ flex: 1 }}>
                    <label className="field-label">Nama Bank</label>
                    <input className="input" value={rekening.bankLain} placeholder="mis. Bank Jatim"
                      onChange={(e) => setRekening({ ...rekening, bankLain: e.target.value })} />
                  </div>
                )}
              </div>
              <div className="mb-md">
                <label className="field-label">Nomor Rekening</label>
                <input className="input" inputMode="numeric" value={rekening.bankAccountNumber}
                  placeholder="Tanpa spasi atau tanda hubung"
                  onChange={(e) => setRekening({ ...rekening, bankAccountNumber: e.target.value })} />
              </div>
              <div className="mb-md">
                <label className="field-label">Nama Pemilik Rekening</label>
                <input className="input" value={rekening.bankAccountName}
                  onChange={(e) => setRekening({ ...rekening, bankAccountName: e.target.value })} />
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                <button className="btn btn-primary" type="submit" disabled={menyimpanRekening}>
                  {menyimpanRekening ? 'Menyimpan...' : 'Simpan Rekening'}
                </button>
                {rekeningLengkap && (
                  <button className="btn btn-outline" type="button" onClick={() => { setRekening(null); setUbahRekening(false); }}>
                    Batal
                  </button>
                )}
              </div>
            </form>
          ) : (
            <div className="loading">Memuat rekening...</div>
          )}
        </div>

        <div className="card">
          <div className="section-title">Ajukan Penarikan</div>

          {!rekeningLengkap ? (
            <p className="text-meta" style={{ margin: 0 }}>
              Simpan rekening penarikan terlebih dahulu di sebelah kiri.
            </p>
          ) : adaYangBerjalan ? (
            <p className="text-meta" style={{ margin: 0 }}>
              Masih ada pengajuan penarikan yang sedang diproses. Satu pengajuan diselesaikan dulu
              sebelum bisa mengajukan yang berikutnya — lihat statusnya di tabel bawah.
            </p>
          ) : (
            <form onSubmit={ajukan}>
              <div className="mb-md">
                <label className="field-label">Nominal (Rp)</label>
                <input className="input" type="number" value={nominal} inputMode="numeric"
                  placeholder={String(minPenarikan)}
                  onChange={(e) => setNominal(e.target.value)} />
                <div className="text-meta" style={{ marginTop: 6 }}>
                  Minimal {formatCurrency(minPenarikan)} · tersedia {formatCurrency(tersedia)}
                </div>
              </div>
              <div className="flex-row mb-md" style={{ flexWrap: 'wrap' }}>
                <button type="button" className="btn btn-outline btn-sm"
                  onClick={() => setNominal(String(minPenarikan))}>
                  Minimal
                </button>
                <button type="button" className="btn btn-outline btn-sm"
                  disabled={tersedia < minPenarikan}
                  onClick={() => setNominal(String(Math.floor(tersedia)))}>
                  Tarik Semua
                </button>
              </div>
              <button className="btn btn-primary" type="submit" disabled={mengajukan || tersedia < minPenarikan}>
                {mengajukan ? 'Mengirim...' : 'Ajukan Penarikan'}
              </button>
              {tersedia < minPenarikan && (
                <p className="text-meta" style={{ marginBottom: 0, marginTop: 10 }}>
                  Saldo tersedia belum mencapai batas minimum penarikan.
                </p>
              )}
            </form>
          )}
        </div>
      </div>

      <h2 className="section-title" style={{ marginTop: 28 }}>Riwayat Penarikan</h2>
      <div className="table-wrap">
        <DataTable
          loading={loadTarik}
          error={errTarik}
          onRetry={() => window.location.reload()}
          columns={[
            { key: 'amount', label: 'Jumlah', render: (r) => formatCurrency(r.amount) },
            { key: 'bankCode', label: 'Bank', render: (r) => r.bankCode || '-' },
            { key: 'bankAccountNumber', label: 'Rekening', render: (r) => r.bankAccountNumber || '-' },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            { key: 'createdAt', label: 'Diajukan', render: (r) => formatDateTime(r.createdAt) },
            // Alasan gagal ditampilkan di sini karena tanpa itu creator hanya
            // melihat statusnya "failed" dan tidak tahu apa yang harus
            // diperbaiki pada pengajuan berikutnya.
            { key: 'failReason', label: 'Catatan', render: (r) => r.failReason || r.note || '-' },
          ]}
          rows={penarikan}
          emptyTitle="Belum ada penarikan"
        />
      </div>

      <h2 className="section-title" style={{ marginTop: 28 }}>Mutasi Saldo</h2>
      <div className="table-wrap">
        <DataTable
          loading={loadMutasi}
          error={errMutasi}
          onRetry={() => window.location.reload()}
          columns={[
            { key: 'type', label: 'Jenis', render: (r) => r.type || '-' },
            { key: 'amount', label: 'Jumlah', render: (r) => formatCurrency(r.amount) },
            { key: 'description', label: 'Keterangan', render: (r) => r.description || '-' },
            { key: 'createdAt', label: 'Waktu', render: (r) => formatDateTime(r.createdAt) },
          ]}
          rows={mutasi}
          emptyTitle="Belum ada mutasi saldo"
        />
      </div>
    </div>
  );
}

import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { doc, getDoc, setDoc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useConfirm } from '../../components/ConfirmDialog';
import { logAdminAction } from '../../utils/adminActions';

/** Settings (section 18 & 29): pengaturan platform + konfigurasi fee (section 22). */

const TIPE_PENGUMUMAN = [
  { nilai: 'pemeliharaan', label: 'Pemeliharaan' },
  { nilai: 'rilis', label: 'Rilis Baru' },
  { nilai: 'internal', label: 'Internal' },
  { nilai: 'penting', label: 'Penting' },
];

const KONTAK_KOSONG = { timSupport: '', daruratIT: '', jamOperasional: '' };

export default function Settings() {
  // payoutBankName/payoutAccountNumber/payoutAccountName dibaca server saat
  // menerbitkan instruksi transfer manual (api/_lib/actions/appPayment.js).
  // Selama tiga field ini kosong, aplikasi menampilkan rekening default
  // "BCA / -" yang tidak bisa ditransfer ke mana-mana.
  const [form, setForm] = useState({
    platformFeePercent: 10,
    minWithdrawal: 100000,
    autoReleaseDays: 3,
    payoutBankName: '',
    payoutAccountNumber: '',
    payoutAccountName: '',
  });
  const [saved, setSaved] = useState(false);
  const [errUmum, setErrUmum] = useState(null);

  // --- Papan pengumuman halaman depan -------------------------------------
  // Disimpan di settings/announcements karena koleksi settings memang boleh
  // dibaca publik — halaman depan perlu menampilkannya sebelum siapa pun
  // login, sementara penulisannya tetap terbatas pada admin.
  const [pengumuman, setPengumuman] = useState([]);
  const [kontak, setKontak] = useState(KONTAK_KOSONG);
  const [muatPengumuman, setMuatPengumuman] = useState(true);
  const [simpanPengumuman, setSimpanPengumuman] = useState(false);
  const [pesanPengumuman, setPesanPengumuman] = useState(null);
  const { confirm, dialog } = useConfirm();

  useEffect(() => {
    (async () => {
      const snap = await getDoc(doc(db, PATHS.settings, 'general'));
      if (snap.exists()) setForm((f) => ({ ...f, ...snap.data() }));
    })().catch(() => {});
  }, []);

  useEffect(() => {
    (async () => {
      try {
        const snap = await getDoc(doc(db, PATHS.settings, 'announcements'));
        if (snap.exists()) {
          const data = snap.data();
          setPengumuman(Array.isArray(data.items) ? data.items : []);
          setKontak({ ...KONTAK_KOSONG, ...(data.kontak || {}) });
        }
      } finally {
        setMuatPengumuman(false);
      }
    })();
  }, []);

  const save = async (e) => {
    e.preventDefault();
    setErrUmum(null);
    try {
      await setDoc(doc(db, PATHS.settings, 'general'), form, { merge: true });
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    } catch (err) {
      // Sebelumnya kegagalan penyimpanan tidak pernah ditampilkan sama sekali,
      // sehingga admin mengira pengaturannya tersimpan padahal ditolak.
      setErrUmum(err.message || 'Gagal menyimpan pengaturan.');
    }
  };

  const ubahItem = (i, bidang, nilai) => {
    setPengumuman((daftar) => daftar.map((p, idx) => (idx === i ? { ...p, [bidang]: nilai } : p)));
  };

  const tambahItem = () => {
    setPengumuman((daftar) => [
      ...daftar,
      { tipe: 'internal', judul: '', isi: '', tanggal: new Date().toISOString().slice(0, 10) },
    ]);
  };

  const hapusItem = async (i) => {
    const ok = await confirm({
      title: 'Hapus pengumuman ini?',
      message: 'Pengumuman akan hilang dari halaman depan setelah Anda menyimpan perubahan.',
      danger: true,
      confirmLabel: 'Ya, Hapus',
    });
    if (!ok) return;
    setPengumuman((daftar) => daftar.filter((_, idx) => idx !== i));
  };

  const pindah = (i, arah) => {
    const tujuan = i + arah;
    if (tujuan < 0 || tujuan >= pengumuman.length) return;
    setPengumuman((daftar) => {
      const salinan = [...daftar];
      [salinan[i], salinan[tujuan]] = [salinan[tujuan], salinan[i]];
      return salinan;
    });
  };

  const simpanPapan = async () => {
    const kosong = pengumuman.findIndex((p) => !p.judul.trim() || !p.isi.trim());
    if (kosong !== -1) {
      setPesanPengumuman({ tipe: 'gagal', teks: `Pengumuman ke-${kosong + 1} masih kosong judul atau isinya.` });
      return;
    }
    setSimpanPengumuman(true);
    setPesanPengumuman(null);
    try {
      await setDoc(
        doc(db, PATHS.settings, 'announcements'),
        {
          items: pengumuman.map((p) => ({
            tipe: p.tipe || 'internal',
            judul: p.judul.trim(),
            isi: p.isi.trim(),
            tanggal: p.tanggal || null,
          })),
          kontak,
          updatedAt: new Date().toISOString(),
        },
        { merge: true }
      );
      await logAdminAction({
        action: 'update_landing_announcements',
        targetType: 'settings',
        targetId: 'announcements',
        reason: `${pengumuman.length} pengumuman`,
      }).catch(() => {});
      setPesanPengumuman({ tipe: 'sukses', teks: 'Tersimpan. Halaman depan sudah diperbarui.' });
    } catch (err) {
      setPesanPengumuman({ tipe: 'gagal', teks: err.message || 'Gagal menyimpan pengumuman.' });
    } finally {
      setSimpanPengumuman(false);
    }
  };

  return (
    <div>
      {dialog}
      <h1 className="page-title">Settings</h1>

      <form className="card mb-lg" style={{ maxWidth: 480 }} onSubmit={save}>
        <div className="section-title">Pengaturan Platform</div>
        <div className="mb-md">
          <label className="field-label">Platform Fee (%)</label>
          <input className="input" type="number" value={form.platformFeePercent}
            onChange={(e) => setForm({ ...form, platformFeePercent: Number(e.target.value) })} />
        </div>
        <div className="mb-md">
          <label className="field-label">Minimum Withdrawal (Rp)</label>
          <input className="input" type="number" value={form.minWithdrawal}
            onChange={(e) => setForm({ ...form, minWithdrawal: Number(e.target.value) })} />
        </div>
        <div className="mb-md">
          <label className="field-label">Auto-release Funds Setelah (hari)</label>
          <input className="input" type="number" value={form.autoReleaseDays}
            onChange={(e) => setForm({ ...form, autoReleaseDays: Number(e.target.value) })} />
        </div>

        <div className="section-title" style={{ marginTop: 18 }}>Rekening Penerima Transfer</div>
        <p className="text-meta" style={{ marginTop: 0 }}>
          Rekening ini yang ditampilkan aplikasi sebagai tujuan transfer pelanggan.
          Wajib diisi sebelum pembayaran pertama.
        </p>
        <div className="mb-md">
          <label className="field-label">Nama Bank</label>
          <input className="input" value={form.payoutBankName} placeholder="BCA"
            onChange={(e) => setForm({ ...form, payoutBankName: e.target.value })} />
        </div>
        <div className="mb-md">
          <label className="field-label">Nomor Rekening</label>
          <input className="input" value={form.payoutAccountNumber} placeholder="1234567890"
            onChange={(e) => setForm({ ...form, payoutAccountNumber: e.target.value })} />
        </div>
        <div className="mb-md">
          <label className="field-label">Nama Pemilik Rekening</label>
          <input className="input" value={form.payoutAccountName} placeholder="PT JepretAja Indonesia"
            onChange={(e) => setForm({ ...form, payoutAccountName: e.target.value })} />
        </div>
        {errUmum && <p className="text-danger-sm">{errUmum}</p>}
        <button className="btn btn-primary" type="submit">Simpan Pengaturan</button>
        {saved && <span className="text-success-sm" style={{ marginLeft: 10 }}>Tersimpan ✓</span>}
      </form>

      {/* ---------------- Papan pengumuman halaman depan ---------------- */}
      <div className="card mb-lg">
        <div className="table-toolbar" style={{ padding: 0, border: 0, marginBottom: 14 }}>
          <div>
            <div className="section-title" style={{ marginBottom: 2 }}>Papan Pengumuman Halaman Depan</div>
            <p className="text-meta" style={{ margin: 0 }}>
              Tampil di <Link to="/">halaman depan</Link> sebelum admin login. Kosongkan seluruhnya
              untuk kembali memakai pengumuman bawaan.
            </p>
          </div>
          <button className="btn btn-outline btn-sm" onClick={tambahItem}>+ Tambah</button>
        </div>

        {pesanPengumuman && (
          <div className={`card ${pesanPengumuman.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
            <div className="msg">{pesanPengumuman.teks}</div>
          </div>
        )}

        {muatPengumuman ? (
          <div className="loading">Memuat data...</div>
        ) : pengumuman.length === 0 ? (
          <div className="empty-state">
            <div className="glyph">✎</div>
            Belum ada pengumuman khusus. Halaman depan menampilkan pengumuman bawaan.
          </div>
        ) : (
          pengumuman.map((p, i) => (
            <div key={i} className="notice-editor">
              <div className="notice-editor-head">
                <select className="input" style={{ width: 160 }} value={p.tipe}
                  onChange={(e) => ubahItem(i, 'tipe', e.target.value)}>
                  {TIPE_PENGUMUMAN.map((t) => <option key={t.nilai} value={t.nilai}>{t.label}</option>)}
                </select>
                <input className="input" type="date" style={{ width: 170 }} value={p.tanggal || ''}
                  onChange={(e) => ubahItem(i, 'tanggal', e.target.value)} />
                <div className="notice-editor-actions">
                  <button className="btn btn-outline btn-sm" disabled={i === 0} onClick={() => pindah(i, -1)}>↑</button>
                  <button className="btn btn-outline btn-sm" disabled={i === pengumuman.length - 1} onClick={() => pindah(i, 1)}>↓</button>
                  <button className="btn btn-danger btn-sm" onClick={() => hapusItem(i)}>Hapus</button>
                </div>
              </div>
              <input className="input mb-sm" placeholder="Judul pengumuman" value={p.judul}
                onChange={(e) => ubahItem(i, 'judul', e.target.value)} />
              <textarea className="input" rows={3} placeholder="Isi pengumuman" value={p.isi}
                onChange={(e) => ubahItem(i, 'isi', e.target.value)} />
            </div>
          ))
        )}

        <div className="section-title" style={{ marginTop: 22 }}>Kontak Bantuan IT</div>
        <div className="grid grid-3">
          <div>
            <label className="field-label">Email Tim Support</label>
            <input className="input" value={kontak.timSupport} placeholder="support@perusahaan.com"
              onChange={(e) => setKontak({ ...kontak, timSupport: e.target.value })} />
          </div>
          <div>
            <label className="field-label">Nomor Darurat IT</label>
            <input className="input" value={kontak.daruratIT} placeholder="+62 812-0000-0000"
              onChange={(e) => setKontak({ ...kontak, daruratIT: e.target.value })} />
          </div>
          <div>
            <label className="field-label">Jam Operasional</label>
            <input className="input" value={kontak.jamOperasional} placeholder="Senin–Jumat, 09.00–18.00 WIB"
              onChange={(e) => setKontak({ ...kontak, jamOperasional: e.target.value })} />
          </div>
        </div>

        <button className="btn btn-primary" style={{ marginTop: 18 }} disabled={simpanPengumuman} onClick={simpanPapan}>
          {simpanPengumuman ? 'Menyimpan...' : 'Simpan Papan Pengumuman'}
        </button>
      </div>
    </div>
  );
}

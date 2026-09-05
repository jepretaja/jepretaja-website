import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { collection, deleteDoc, doc, updateDoc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatCard from '../../components/StatCard';
import StatusBadge from '../../components/StatusBadge';
import SearchInput from '../../components/SearchInput';
import ExportButton from '../../components/ExportButton';
import { useConfirm } from '../../components/ConfirmDialog';
import { logAdminAction } from '../../utils/adminActions';
import { formatCurrency, compactNumber } from '../../utils/format';

const SARINGAN = [
  { id: 'semua', label: 'Semua' },
  { id: 'aktif', label: 'Aktif' },
  { id: 'nonaktif', label: 'Nonaktif' },
  { id: 'tanpa-harga', label: 'Harga bermasalah' },
];

/**
 * Katalog paket seluruh creator.
 *
 * Paket adalah barang yang sebenarnya dijual marketplace ini, tapi sampai
 * sekarang panel admin tidak punya satu pun layar untuk melihatnya: paket
 * hanya bisa diperiksa satu per satu lewat profil creator, sehingga paket
 * berharga Rp0 — atau paket yang harganya salah ketik jadi jutaan — baru
 * ketahuan setelah ada pelanggan yang memesannya.
 *
 * Karena itu saringan "Harga bermasalah" berdiri sendiri: itulah kondisi yang
 * paling perlu ditemukan admin, dan mencarinya dengan mata di daftar panjang
 * adalah persis pekerjaan yang seharusnya dikerjakan halaman ini.
 *
 * firestore.rules mengizinkan admin mengubah dan menghapus paket milik siapa
 * pun (`allow update, delete: if ... || isAdmin()`), jadi aksi di sini ditulis
 * langsung ke Firestore — sama seperti moderasi review dan kategori.
 */
export default function PackageCatalog() {
  const { data: packages, loading, error } = useCollection(collection(db, PATHS.packages));
  const { data: creators } = useCollection(collection(db, PATHS.creators));
  const { confirm, dialog } = useConfirm();

  const [cari, setCari] = useState('');
  const [saringan, setSaringan] = useState('semua');
  const [pesan, setPesan] = useState(null);

  const namaCreator = useMemo(() => {
    const peta = new Map();
    creators.forEach((c) => peta.set(c.id, c.displayName || c.name || c.id));
    return peta;
  }, [creators]);

  const baris = useMemo(() => {
    const kunci = cari.trim().toLowerCase();
    return packages
      .map((p) => ({ ...p, creatorName: namaCreator.get(p.creatorId) || p.creatorId || '-' }))
      .filter((p) => {
        if (saringan === 'aktif' && p.active === false) return false;
        if (saringan === 'nonaktif' && p.active !== false) return false;
        if (saringan === 'tanpa-harga' && Number(p.price) > 0) return false;
        if (!kunci) return true;
        return `${p.name || ''} ${p.creatorName}`.toLowerCase().includes(kunci);
      })
      .sort((a, b) => (a.creatorName || '').localeCompare(b.creatorName || ''));
  }, [packages, namaCreator, cari, saringan]);

  const aktif = packages.filter((p) => p.active !== false).length;
  const bermasalah = packages.filter((p) => !(Number(p.price) > 0)).length;
  const rataHarga = packages.length
    ? packages.reduce((t, p) => t + (Number(p.price) || 0), 0) / packages.length
    : 0;

  const ubahAktif = async (p) => {
    const aktifkan = p.active === false;
    const ok = await confirm({
      title: aktifkan ? `Aktifkan paket "${p.name}"?` : `Nonaktifkan paket "${p.name}"?`,
      message: aktifkan
        ? 'Paket akan kembali tampil dan bisa dipesan pelanggan di aplikasi.'
        : 'Paket langsung hilang dari etalase. Booking yang sudah berjalan tidak terpengaruh.',
      danger: !aktifkan,
    });
    if (!ok) return;
    try {
      await updateDoc(doc(db, PATHS.packages, p.id), { active: aktifkan });
      await logAdminAction({
        action: aktifkan ? 'activate_package' : 'deactivate_package',
        targetType: 'package', targetId: p.id,
      }).catch(() => {});
      setPesan({ tipe: 'sukses', teks: `Paket "${p.name}" ${aktifkan ? 'diaktifkan' : 'dinonaktifkan'}.` });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message || 'Gagal mengubah status paket.' });
    }
  };

  const hapus = async (p) => {
    const ok = await confirm({
      title: `Hapus paket "${p.name}"?`,
      message: 'Paket hilang permanen dari katalog. Kalau hanya ingin menyembunyikannya, pilih Nonaktifkan.',
      danger: true,
      confirmLabel: 'Ya, Hapus',
    });
    if (!ok) return;
    try {
      await deleteDoc(doc(db, PATHS.packages, p.id));
      await logAdminAction({ action: 'delete_package', targetType: 'package', targetId: p.id }).catch(() => {});
      setPesan({ tipe: 'sukses', teks: `Paket "${p.name}" dihapus.` });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message || 'Gagal menghapus paket.' });
    }
  };

  return (
    <div>
      {dialog}
      <h1 className="page-title">Katalog Paket</h1>
      <p className="text-meta" style={{ marginBottom: 20 }}>
        Seluruh paket jasa dari semua creator — yang benar-benar dibeli pelanggan di aplikasi.
      </p>

      {pesan && (
        <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
          <div className="msg">{pesan.teks}</div>
        </div>
      )}

      <div className="grid grid-4 mb-lg">
        <StatCard label="Total Paket" value={compactNumber(packages.length)} />
        <StatCard label="Aktif di Etalase" value={compactNumber(aktif)} />
        <StatCard
          label="Harga Bermasalah"
          value={compactNumber(bermasalah)}
          delta="Harga kosong atau nol"
          deltaDirection={bermasalah ? 'down' : 'up'}
        />
        <StatCard label="Harga Rata-rata" value={formatCurrency(rataHarga)} />
      </div>

      <div className="table-wrap">
        <div className="table-toolbar">
          <div className="flex-row-12">
            <SearchInput value={cari} onChange={setCari} placeholder="Cari paket atau creator..." />
            <div className="pill-tabs" style={{ marginBottom: 0 }}>
              {SARINGAN.map((s) => (
                <button
                  key={s.id}
                  className={`pill-tab${saringan === s.id ? ' active' : ''}`}
                  onClick={() => setSaringan(s.id)}
                >
                  {s.label}
                </button>
              ))}
            </div>
          </div>
          <ExportButton
            filename="katalog-paket.csv"
            columns={[
              { key: 'creatorName', label: 'Creator' },
              { key: 'name', label: 'Paket' },
              { key: 'price', label: 'Harga' },
              { key: 'duration', label: 'Durasi' },
              { key: 'active', label: 'Aktif' },
            ]}
            rows={baris}
          />
        </div>
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          columns={[
            {
              key: 'creatorName', label: 'Creator',
              render: (r) => (r.creatorId
                ? <Link to={`/creators/${r.creatorId}`}>{r.creatorName}</Link>
                : '-'),
            },
            { key: 'name', label: 'Paket', render: (r) => r.name || '(tanpa nama)' },
            {
              key: 'price', label: 'Harga',
              render: (r) => (Number(r.price) > 0
                ? <span className="num">{formatCurrency(r.price)}</span>
                : <span className="text-danger-sm">{formatCurrency(r.price)}</span>),
            },
            { key: 'duration', label: 'Durasi', render: (r) => r.duration || '-' },
            { key: 'active', label: 'Status', render: (r) => <StatusBadge status={r.active === false ? 'suspended' : 'active'} /> },
            {
              key: 'aksi', label: 'Aksi',
              render: (r) => (
                <div style={{ display: 'flex', gap: 8 }}>
                  <button className="btn btn-outline btn-sm" onClick={() => ubahAktif(r)}>
                    {r.active === false ? 'Aktifkan' : 'Nonaktifkan'}
                  </button>
                  <button className="btn btn-danger btn-sm" onClick={() => hapus(r)}>Hapus</button>
                </div>
              ),
            },
          ]}
          rows={baris}
          emptyTitle="Tidak ada paket yang cocok"
        />
      </div>
    </div>
  );
}

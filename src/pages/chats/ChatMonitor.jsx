import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { collection, deleteDoc, doc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatCard from '../../components/StatCard';
import SearchInput from '../../components/SearchInput';
import { useConfirm } from '../../components/ConfirmDialog';
import { logAdminAction } from '../../utils/adminActions';
import { formatDateTime, compactNumber } from '../../utils/format';
import { toMillis } from '../../utils/sort';

const TUJUH_HARI = 7 * 24 * 60 * 60 * 1000;

/**
 * Pemantauan percakapan untuk penanganan sengketa.
 *
 * BATAS YANG DISENGAJA: halaman ini menampilkan daftar percakapan dan
 * ringkasan pesan terakhirnya, TIDAK isi percakapannya. Bukan karena belum
 * sempat dibuat — firestore.rules memang hanya mengizinkan pembacaan pesan
 * oleh pesertanya sendiri (`uid() in resource.data.participants`), dan admin
 * bukan peserta. Melonggarkan aturan itu berarti setiap anggota admin_users
 * bisa membaca seluruh percakapan pribadi pengguna platform, yang jauh lebih
 * mahal daripada manfaat sesekali menelusuri satu sengketa.
 *
 * Untuk sengketa, jalur yang benar adalah meminta tangkapan layar dari pihak
 * yang melapor lewat halaman Disputes — bukan membaca diam-diam.
 *
 * Yang bisa dilihat di sini — siapa berbicara dengan siapa, kapan terakhir,
 * dan apakah percakapannya berhenti mendadak — sudah cukup untuk memeriksa
 * pola penyalahgunaan seperti satu akun yang menghubungi puluhan creator.
 */
export default function ChatMonitor() {
  const { data: chats, loading, error } = useCollection(collection(db, PATHS.chats));
  const { data: creators } = useCollection(collection(db, PATHS.creators));
  const { data: users } = useCollection(collection(db, PATHS.users));
  const { confirm, dialog } = useConfirm();

  const [cari, setCari] = useState('');
  const [pesan, setPesan] = useState(null);

  const namaCreator = useMemo(() => {
    const peta = new Map();
    creators.forEach((c) => peta.set(c.id, c.displayName || c.name || c.id));
    return peta;
  }, [creators]);

  const namaUser = useMemo(() => {
    const peta = new Map();
    users.forEach((u) => peta.set(u.id, u.name || u.displayName || u.email || u.id));
    return peta;
  }, [users]);

  const baris = useMemo(() => {
    const kunci = cari.trim().toLowerCase();
    return chats
      .map((c) => ({
        ...c,
        creatorName: namaCreator.get(c.creatorId) || c.creatorName || c.creatorId || '-',
        customerName: namaUser.get(c.customerId) || c.customerName || c.customerId || '-',
      }))
      .filter((c) => {
        if (!kunci) return true;
        return `${c.creatorName} ${c.customerName}`.toLowerCase().includes(kunci);
      })
      .sort((a, b) => toMillis(b.updatedAt) - toMillis(a.updatedAt));
  }, [chats, namaCreator, namaUser, cari]);

  const sekarang = Date.now();
  const aktifPekanIni = chats.filter((c) => sekarang - toMillis(c.updatedAt) < TUJUH_HARI).length;

  // Pelanggan yang menghubungi banyak creator sekaligus — pola yang paling
  // sering muncul pada penyebaran pesan tak diminta.
  const pelangganTerbanyak = useMemo(() => {
    const ember = {};
    chats.forEach((c) => { if (c.customerId) ember[c.customerId] = (ember[c.customerId] || 0) + 1; });
    const teratas = Object.entries(ember).sort((a, b) => b[1] - a[1])[0];
    return teratas ? { nama: namaUser.get(teratas[0]) || teratas[0], jumlah: teratas[1] } : null;
  }, [chats, namaUser]);

  const hapus = async (c) => {
    const ok = await confirm({
      title: 'Hapus percakapan ini?',
      message: 'Ruang percakapan hilang bagi kedua pihak dan tidak bisa dikembalikan. Isi pesannya sendiri tidak ikut terhapus. Lakukan hanya untuk kasus penyalahgunaan yang sudah terbukti.',
      danger: true,
      confirmLabel: 'Ya, Hapus',
    });
    if (!ok) return;
    try {
      await deleteDoc(doc(db, PATHS.chats, c.id));
      await logAdminAction({ action: 'delete_chat', targetType: 'chat', targetId: c.id }).catch(() => {});
      setPesan({ tipe: 'sukses', teks: 'Percakapan dihapus.' });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message || 'Gagal menghapus percakapan.' });
    }
  };

  return (
    <div>
      {dialog}
      <h1 className="page-title">Monitoring Chat</h1>
      <p className="text-meta" style={{ marginBottom: 20 }}>
        Daftar percakapan antara pelanggan dan creator, untuk menelusuri pola penyalahgunaan.
      </p>

      {pesan && (
        <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
          <div className="msg">{pesan.teks}</div>
        </div>
      )}

      <div className="card mb-lg">
        <div className="section-title">Isi percakapan tidak ditampilkan</div>
        <p className="text-meta" style={{ margin: 0 }}>
          Aturan Firestore hanya mengizinkan peserta percakapan membaca pesannya sendiri, dan admin
          bukan peserta. Untuk keperluan sengketa, mintalah tangkapan layar dari pihak pelapor melalui
          halaman <Link to="/disputes">Disputes</Link>.
        </p>
      </div>

      <div className="grid grid-3 mb-lg">
        <StatCard label="Total Percakapan" value={compactNumber(chats.length)} />
        <StatCard label="Aktif 7 Hari Terakhir" value={compactNumber(aktifPekanIni)} />
        <StatCard
          label="Pelanggan Paling Aktif"
          value={pelangganTerbanyak ? String(pelangganTerbanyak.jumlah) : '0'}
          delta={pelangganTerbanyak ? `${pelangganTerbanyak.nama} · percakapan` : 'Belum ada data'}
        />
      </div>

      <div className="table-wrap">
        <div className="table-toolbar">
          <SearchInput value={cari} onChange={setCari} placeholder="Cari nama pelanggan atau creator..." />
          <span className="text-meta">{baris.length} percakapan</span>
        </div>
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          columns={[
            {
              key: 'customerName', label: 'Pelanggan',
              render: (r) => (r.customerId ? <Link to={`/users/${r.customerId}`}>{r.customerName}</Link> : r.customerName),
            },
            {
              key: 'creatorName', label: 'Creator',
              render: (r) => (r.creatorId ? <Link to={`/creators/${r.creatorId}`}>{r.creatorName}</Link> : r.creatorName),
            },
            {
              key: 'lastMessage', label: 'Pesan Terakhir',
              render: (r) => (r.lastMessage ? String(r.lastMessage).slice(0, 60) : <em className="text-meta">belum ada</em>),
            },
            { key: 'updatedAt', label: 'Diperbarui', render: (r) => formatDateTime(r.updatedAt) },
            {
              key: 'aksi', label: 'Aksi',
              render: (r) => <button className="btn btn-danger btn-sm" onClick={() => hapus(r)}>Hapus</button>,
            },
          ]}
          rows={baris}
          emptyTitle="Belum ada percakapan"
        />
      </div>
    </div>
  );
}

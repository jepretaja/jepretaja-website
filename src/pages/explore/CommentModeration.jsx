import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { collection, deleteDoc, doc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import StatCard from '../../components/StatCard';
import SearchInput from '../../components/SearchInput';
import ExportButton from '../../components/ExportButton';
import { useConfirm } from '../../components/ConfirmDialog';
import { logAdminAction } from '../../utils/adminActions';
import { formatDateTime, compactNumber } from '../../utils/format';
import { byNewest } from '../../utils/sort';

/** Isi komentar bisa datang dengan nama field berbeda antar versi APK. */
function isiKomentar(k) {
  return k.text ?? k.comment ?? k.content ?? k.body ?? '';
}

/**
 * Moderasi komentar Explore.
 *
 * Komentar adalah satu-satunya konten Explore yang bisa ditulis SIAPA SAJA
 * yang punya akun — post hanya bisa dibuat creator, dan review hanya bisa
 * ditulis pelanggan yang benar-benar memesan. Justru bagian dengan ambang
 * masuk paling rendah inilah yang sampai sekarang tidak punya layar moderasi:
 * satu-satunya cara menurunkan komentar kasar adalah lewat Firebase Console.
 *
 * Aturan Firestore hanya memberi admin dua kemampuan di sini — mengubah dan
 * menghapus. Tidak ada field status pada komentar, jadi menyembunyikan bukan
 * pilihan yang tersedia dan halaman ini tidak berpura-pura sebaliknya:
 * penghapusan diminta konfirmasi tegas karena memang tidak bisa dibatalkan.
 */
export default function CommentModeration() {
  const { data: comments, loading, error } = useCollection(collection(db, PATHS.exploreComments));
  const { data: posts } = useCollection(collection(db, PATHS.explorePosts));
  const { data: users } = useCollection(collection(db, PATHS.users));
  const { confirm, dialog } = useConfirm();

  const [cari, setCari] = useState('');
  const [pesan, setPesan] = useState(null);

  const namaUser = useMemo(() => {
    const peta = new Map();
    users.forEach((u) => peta.set(u.id, u.name || u.displayName || u.email || u.id));
    return peta;
  }, [users]);

  const judulPost = useMemo(() => {
    const peta = new Map();
    posts.forEach((p) => peta.set(p.id, p.caption || p.title || p.id));
    return peta;
  }, [posts]);

  const baris = useMemo(() => {
    const kunci = cari.trim().toLowerCase();
    return byNewest(
      comments.map((k) => ({
        ...k,
        isi: isiKomentar(k),
        penulis: namaUser.get(k.userId) || k.userName || k.userId || '-',
        post: judulPost.get(k.postId) || k.postId || '-',
      }))
    ).filter((k) => {
      if (!kunci) return true;
      return `${k.isi} ${k.penulis} ${k.post}`.toLowerCase().includes(kunci);
    });
  }, [comments, namaUser, judulPost, cari]);

  // Komentar yang menunjuk post yang sudah tidak ada — sisa dari post yang
  // dihapus. Tidak berbahaya, tapi berguna diketahui saat menelusuri laporan.
  const yatim = comments.filter((k) => k.postId && !judulPost.has(k.postId)).length;

  const hapus = async (k) => {
    const ok = await confirm({
      title: 'Hapus komentar ini?',
      message: `"${(k.isi || '').slice(0, 120)}" — penghapusan bersifat permanen dan tidak bisa dibatalkan.`,
      danger: true,
      confirmLabel: 'Ya, Hapus',
    });
    if (!ok) return;
    try {
      await deleteDoc(doc(db, PATHS.exploreComments, k.id));
      await logAdminAction({ action: 'delete_explore_comment', targetType: 'comment', targetId: k.id }).catch(() => {});
      setPesan({ tipe: 'sukses', teks: 'Komentar dihapus.' });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message || 'Gagal menghapus komentar.' });
    }
  };

  return (
    <div>
      {dialog}
      <h1 className="page-title">Moderasi Komentar</h1>
      <p className="text-meta" style={{ marginBottom: 20 }}>
        Komentar pada post Explore — bagian Explore yang bisa ditulis semua pengguna, bukan hanya creator.
      </p>

      {pesan && (
        <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
          <div className="msg">{pesan.teks}</div>
        </div>
      )}

      <div className="grid grid-3 mb-lg">
        <StatCard label="Total Komentar" value={compactNumber(comments.length)} />
        <StatCard label="Post Dikomentari" value={compactNumber(new Set(comments.map((k) => k.postId)).size)} />
        <StatCard label="Post Sudah Terhapus" value={compactNumber(yatim)} delta="Komentar tanpa post induk" />
      </div>

      <div className="table-wrap">
        <div className="table-toolbar">
          <SearchInput value={cari} onChange={setCari} placeholder="Cari isi komentar atau penulis..." />
          <ExportButton
            filename="komentar-explore.csv"
            columns={[
              { key: 'penulis', label: 'Penulis' },
              { key: 'isi', label: 'Komentar' },
              { key: 'post', label: 'Post' },
            ]}
            rows={baris}
          />
        </div>
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          columns={[
            { key: 'penulis', label: 'Penulis' },
            { key: 'isi', label: 'Komentar', render: (r) => (r.isi ? r.isi.slice(0, 90) : <em className="text-meta">(kosong)</em>) },
            {
              key: 'post', label: 'Post',
              render: (r) => (r.postId
                ? <Link to={`/explore/${r.postId}`}>{String(r.post).slice(0, 40)}</Link>
                : '-'),
            },
            { key: 'likeCount', label: 'Suka', render: (r) => Number(r.likeCount) || 0 },
            { key: 'createdAt', label: 'Waktu', render: (r) => formatDateTime(r.createdAt) },
            {
              key: 'aksi', label: 'Aksi',
              render: (r) => <button className="btn btn-danger btn-sm" onClick={() => hapus(r)}>Hapus</button>,
            },
          ]}
          rows={baris}
          emptyTitle="Belum ada komentar"
        />
      </div>
    </div>
  );
}

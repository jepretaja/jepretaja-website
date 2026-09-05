import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { collection, deleteDoc, doc, updateDoc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import StatCard from '../../components/StatCard';
import StatusBadge from '../../components/StatusBadge';
import SearchInput from '../../components/SearchInput';
import EmptyState from '../../components/EmptyState';
import ErrorState from '../../components/ErrorState';
import { useConfirm } from '../../components/ConfirmDialog';
import { logAdminAction } from '../../utils/adminActions';
import { compactNumber } from '../../utils/format';

const SARINGAN = [
  { id: 'semua', label: 'Semua' },
  { id: 'active', label: 'Tampil' },
  { id: 'hidden', label: 'Disembunyikan' },
];

/**
 * Moderasi album portfolio seluruh creator.
 *
 * Portfolio adalah etalase yang dilihat calon pelanggan sebelum memesan, dan
 * `allow read: if true` di firestore.rules berarti isinya terbuka untuk siapa
 * saja — termasuk yang belum memasang aplikasi. Sampai sekarang panel admin
 * hanya bisa memoderasi post Explore dan review, sehingga foto bermasalah di
 * portfolio tidak punya jalur penanganan sama sekali.
 *
 * Menyembunyikan dipisahkan dari menghapus dengan sengaja: sebagian besar
 * kasus moderasi adalah "turunkan dulu sambil ditanyakan ke creator", dan
 * penghapusan permanen menghilangkan barang bukti untuk sengketa yang mungkin
 * menyusul.
 */
export default function PortfolioModeration() {
  const { data: portfolios, loading, error } = useCollection(collection(db, PATHS.portfolios));
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
    return portfolios
      .map((p) => ({
        ...p,
        // Album lama bisa saja belum punya field status sama sekali; itu
        // berarti tampil, bukan berarti kosong.
        statusEfektif: p.status || 'active',
        creatorName: namaCreator.get(p.creatorId) || p.creatorId || '-',
      }))
      .filter((p) => {
        if (saringan !== 'semua' && p.statusEfektif !== saringan) return false;
        if (!kunci) return true;
        return `${p.title || ''} ${p.category || ''} ${p.creatorName}`.toLowerCase().includes(kunci);
      });
  }, [portfolios, namaCreator, cari, saringan]);

  const tampil = portfolios.filter((p) => (p.status || 'active') === 'active').length;
  const disembunyikan = portfolios.filter((p) => p.status === 'hidden').length;
  const totalMedia = portfolios.reduce((t, p) => t + (p.media || []).length, 0);

  const ubahStatus = async (p, status) => {
    const sembunyikan = status === 'hidden';
    const ok = await confirm({
      title: sembunyikan ? `Sembunyikan album "${p.title}"?` : `Tampilkan kembali "${p.title}"?`,
      message: sembunyikan
        ? 'Album langsung hilang dari profil publik creator. Creator masih bisa melihatnya di portalnya sendiri.'
        : 'Album akan kembali terlihat oleh publik di aplikasi.',
      danger: sembunyikan,
    });
    if (!ok) return;
    try {
      await updateDoc(doc(db, PATHS.portfolios, p.id), { status });
      await logAdminAction({
        action: sembunyikan ? 'hide_portfolio' : 'unhide_portfolio',
        targetType: 'portfolio', targetId: p.id,
      }).catch(() => {});
      setPesan({ tipe: 'sukses', teks: sembunyikan ? 'Album disembunyikan.' : 'Album ditampilkan kembali.' });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message || 'Gagal mengubah status album.' });
    }
  };

  const hapus = async (p) => {
    const ok = await confirm({
      title: `Hapus album "${p.title}"?`,
      message: 'Album hilang permanen. Untuk kasus yang masih diperiksa, pilih Sembunyikan agar isinya tetap bisa ditinjau.',
      danger: true,
      confirmLabel: 'Ya, Hapus',
    });
    if (!ok) return;
    try {
      await deleteDoc(doc(db, PATHS.portfolios, p.id));
      await logAdminAction({ action: 'delete_portfolio', targetType: 'portfolio', targetId: p.id }).catch(() => {});
      setPesan({ tipe: 'sukses', teks: 'Album dihapus.' });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message || 'Gagal menghapus album.' });
    }
  };

  return (
    <div>
      {dialog}
      <h1 className="page-title">Portfolio Creator</h1>
      <p className="text-meta" style={{ marginBottom: 20 }}>
        Album karya yang tampil di profil publik creator — terbuka untuk siapa pun di aplikasi.
      </p>

      {pesan && (
        <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
          <div className="msg">{pesan.teks}</div>
        </div>
      )}

      <div className="grid grid-4 mb-lg">
        <StatCard label="Total Album" value={compactNumber(portfolios.length)} />
        <StatCard label="Tampil Publik" value={compactNumber(tampil)} />
        <StatCard label="Disembunyikan" value={compactNumber(disembunyikan)} deltaDirection="down" />
        <StatCard label="Total Media" value={compactNumber(totalMedia)} />
      </div>

      <div className="flex-between mb-md">
        <SearchInput value={cari} onChange={setCari} placeholder="Cari judul, kategori, creator..." />
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

      {loading ? (
        <div className="loading">Memuat data...</div>
      ) : error ? (
        <ErrorState error={error} onRetry={() => window.location.reload()} />
      ) : baris.length === 0 ? (
        <EmptyState title="Tidak ada album yang cocok" />
      ) : (
        <div className="grid grid-3">
          {baris.map((p) => (
            <div className="card media-card" key={p.id}>
              {p.media?.[0] ? (
                <img
                  className="media-thumb"
                  src={p.media[0]}
                  alt=""
                  onError={(e) => { e.target.style.display = 'none'; }}
                />
              ) : (
                <div className="media-thumb" style={{ display: 'grid', placeItems: 'center', color: 'var(--text-secondary)' }}>
                  tanpa media
                </div>
              )}
              <div className="media-meta">
                <StatusBadge status={p.statusEfektif} />
                <span className="text-meta">{(p.media || []).length} media</span>
              </div>
              <div style={{ fontWeight: 700, marginBottom: 2 }}>{p.title || '(tanpa judul)'}</div>
              <div className="text-meta" style={{ marginBottom: 10 }}>
                {p.category || 'Tanpa kategori'} ·{' '}
                {p.creatorId ? <Link to={`/creators/${p.creatorId}`}>{p.creatorName}</Link> : p.creatorName}
              </div>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {p.statusEfektif === 'hidden' ? (
                  <button className="btn btn-outline btn-sm" onClick={() => ubahStatus(p, 'active')}>Tampilkan</button>
                ) : (
                  <button className="btn btn-outline btn-sm" onClick={() => ubahStatus(p, 'hidden')}>Sembunyikan</button>
                )}
                <button className="btn btn-danger btn-sm" onClick={() => hapus(p)}>Hapus</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

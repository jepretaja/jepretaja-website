import { useEffect, useMemo, useState } from 'react';
import { addDoc, collection, deleteDoc, doc, serverTimestamp, updateDoc, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { useAuth } from '../../auth/AuthContext';
import ImagePicker from '../../components/ImagePicker';
import StatusBadge from '../../components/StatusBadge';
import StatCard from '../../components/StatCard';
import EmptyState from '../../components/EmptyState';
import ErrorState from '../../components/ErrorState';
import { useConfirm } from '../../components/ConfirmDialog';
import { formatDateTime, compactNumber } from '../../utils/format';
import { byNewest } from '../../utils/sort';
import CreatorPortfolio from './CreatorPortfolio';

const KOSONG = {
  caption: '', category: '', location: '', tags: '',
  media: [], type: 'photo', commentPolicy: 'all', allowSave: true,
};

const TAB = [
  { id: 'semua', label: 'Semua' },
  { id: 'pending_review', label: 'Menunggu Review' },
  { id: 'published', label: 'Tayang' },
  { id: 'rejected', label: 'Ditolak' },
  { id: 'hidden', label: 'Disembunyikan' },
];

const KEBIJAKAN_KOMENTAR = [
  { id: 'all', label: 'Semua orang boleh berkomentar' },
  { id: 'followers', label: 'Hanya pengikut' },
  { id: 'off', label: 'Tutup kolom komentar' },
];

/**
 * "Unggahan Saya" — karya Explore milik creator yang sedang login.
 *
 * Sampai sekarang portal creator tidak punya satu pun layar untuk ini: karya
 * hanya bisa diunggah dan dilihat lewat aplikasi Android, sementara admin
 * justru punya layar penuh untuk memoderasinya. Akibatnya seorang creator
 * yang karyanya ditolak tidak punya cara apa pun untuk mengetahui alasannya
 * dari web, apalagi memperbaikinya.
 *
 * Karena itu `moderationNote` — catatan yang WAJIB diisi admin saat menolak
 * atau menyembunyikan karya (lihat pages/explore/PostDetail.jsx) — ditampilkan
 * mencolok di kartu karyanya. Catatan itu memang ditulis untuk dibaca creator;
 * tanpa layar ini, ia tidak pernah sampai ke tujuannya.
 *
 * Batas yang datang dari firestore.rules dan sengaja tidak dilawan:
 * creator boleh mengubah dan menghapus karyanya sendiri, TAPI tidak boleh
 * menyentuh `status` maupun `moderationStatus`. Jadi tidak ada tombol
 * "terbitkan ulang" di sini — memindahkan karya yang ditolak kembali ke antrian
 * adalah keputusan admin, bukan keputusan pengunggahnya.
 */
export default function CreatorPosts() {
  const { user, creatorProfile } = useAuth();
  const uid = user?.uid;
  const { confirm, dialog } = useConfirm();

  const { data: posts, loading, error } = useCollection(
    collection(db, PATHS.explorePosts),
    uid ? [where('creatorId', '==', uid)] : []
  );
  const { data: kategori } = useCollection(collection(db, PATHS.categories));

  const [tab, setTab] = useState('semua');
  const [mode, setMode] = useState('karya');
  const [form, setForm] = useState(() => ({
    ...KOSONG,
    category: 'Wedding',
  }));
  const [menyimpan, setMenyimpan] = useState(false);
  const [pesan, setPesan] = useState(null);

  const draftKey = uid ? `creator-post-draft-${uid}` : null;
  useEffect(() => {
    if (!draftKey) return;
    try {
      const tersimpan = JSON.parse(localStorage.getItem(draftKey) || 'null');
      if (tersimpan && !form.id) setForm((saatIni) => ({ ...saatIni, ...tersimpan }));
    } catch {
      localStorage.removeItem(draftKey);
    }
  }, [draftKey]);

  const daftar = useMemo(() => {
    const urut = byNewest(posts);
    // Karya berstatus 'deleted' sudah dibuang admin; menampilkannya di sini
    // hanya membingungkan karena creator tidak bisa berbuat apa-apa atasnya.
    const terlihat = urut.filter((p) => p.status !== 'deleted');
    return tab === 'semua' ? terlihat : terlihat.filter((p) => p.status === tab);
  }, [posts, tab]);

  const jumlahPerStatus = posts.reduce((acc, p) => {
    acc[p.status] = (acc[p.status] || 0) + 1;
    return acc;
  }, {});

  const totalTayang = jumlahPerStatus.published || 0;
  const totalSuka = posts.reduce((t, p) => t + (Number(p.metrics?.like) || 0), 0);
  const totalDilihat = posts.reduce((t, p) => t + (Number(p.metrics?.view) || 0), 0);

  const bukaBaru = () => {
    setPesan(null);
    setForm({ ...KOSONG, category: kategori[0]?.name || kategori[0]?.id || '' });
  };

  const bukaUbah = (p) => {
    setPesan(null);
    setForm({
      id: p.id,
      status: p.status,
      caption: p.caption || '',
      category: p.category || '',
      location: p.location || '',
      tags: (p.tags || []).join(', '),
      media: [...(p.mediaUrls || [])],
      type: p.type || 'photo',
      commentPolicy: p.commentPolicy || 'all',
      allowSave: p.allowSave !== false,
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const simpan = async (e) => {
    e.preventDefault();
    setPesan(null);

    const media = form.media.filter(Boolean);
    if (media.length === 0) {
      setPesan({ tipe: 'gagal', teks: 'Tambahkan minimal satu foto.' });
      return;
    }
    if (!form.caption.trim()) {
      setPesan({ tipe: 'gagal', teks: 'Caption wajib diisi — itu yang dibaca calon pelanggan di feed.' });
      return;
    }

    const isi = {
      caption: form.caption.trim(),
      category: form.category.trim() || null,
      location: form.location.trim() || null,
      tags: form.tags.split(',').map((s) => s.trim().replace(/^#/, '')).filter(Boolean),
      mediaUrls: media,
      type: form.type || 'photo',
      thumbnailUrl: media[0],
      commentPolicy: form.commentPolicy,
      allowSave: form.allowSave,
    };

    setMenyimpan(true);
    try {
      if (form.id) {
        // `status` sengaja TIDAK ikut dikirim: firestore.rules menolak setiap
        // perubahan yang menyentuhnya, dan mengirim ulang nilai yang sama
        // hanya menambah risiko kalau suatu saat aturannya diperketat.
        await updateDoc(doc(db, PATHS.explorePosts, form.id), isi);
        setPesan({ tipe: 'sukses', teks: 'Karya diperbarui.' });
      } else {
        await addDoc(collection(db, PATHS.explorePosts), {
          ...isi,
          creatorId: uid,
          creatorName: creatorProfile?.displayName || null,
          status: 'pending_review',
          moderationNote: null,
          metrics: { like: 0, comment: 0, save: 0, view: 0, share: 0 },
          createdAt: serverTimestamp(),
        });
        setPesan({ tipe: 'sukses', teks: 'Karya dikirim untuk review moderator.' });
      }
      setForm({ ...KOSONG, category: kategori[0]?.name || kategori[0]?.id || '' });
    } catch (err) {
      setPesan({
        tipe: 'gagal',
        teks: err.code === 'permission-denied'
          ? 'Perubahan ditolak server. Status dan hasil moderasi hanya bisa diubah admin.'
          : err.message || 'Gagal menyimpan karya.',
      });
    } finally {
      setMenyimpan(false);
    }
  };

  const hapus = async (p) => {
    const ok = await confirm({
      title: 'Hapus karya ini?',
      message: 'Karya beserta suka dan komentarnya hilang dari aplikasi dan tidak bisa dikembalikan.',
      danger: true,
      confirmLabel: 'Ya, Hapus',
    });
    if (!ok) return;
    try {
      await deleteDoc(doc(db, PATHS.explorePosts, p.id));
      setPesan({ tipe: 'sukses', teks: 'Karya dihapus.' });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message || 'Gagal menghapus karya.' });
    }
  };

  const simpanDraft = () => {
    if (!draftKey) return;
    const { id, status, ...draft } = form;
    localStorage.setItem(draftKey, JSON.stringify(draft));
    setPesan({ tipe: 'sukses', teks: 'Draft tersimpan di perangkat ini.' });
  };

  const mediaTabs = (
    <div className="creator-mode-switcher">
      <button className={`creator-mode-btn ${mode === 'karya' ? 'active' : ''}`} onClick={() => setMode('karya')} type="button">
        Karya Explore
      </button>
      <button className={`creator-mode-btn ${mode === 'portfolio' ? 'active' : ''}`} onClick={() => setMode('portfolio')} type="button">
        Portfolio
      </button>
    </div>
  );

  const uploadCard = (
    <div className="creator-upload-shell">
      <header className="creator-upload-header">
        <button type="button" className="creator-back-btn" aria-label="Kembali" onClick={() => window.history.back()}>←</button>
        <h1>Creator Studio</h1>
        <span className="creator-draft-badge" aria-label="Status draft">Draft</span>
      </header>

      <section className="creator-hero-card">
        <div className="creator-hero-copy">
          <h2>Publikasikan karya terbaikmu</h2>
          <p>Atur media, cerita, dan detail booking dalam satualur yang rapi.</p>
          <div className="creator-hero-pills">
            <span className="creator-pill muted">• Belum ada media</span>
            <span className="creator-pill primary">• Explore</span>
          </div>
        </div>
      </section>

      <div className="creator-upload-subheader">
        <span>Antrian unggah</span>
      </div>

      <div className="creator-upload-flow">
        <span className="creator-flow-muted">Tersimpan dan tayang</span>
        <button type="button" className="creator-link-btn" onClick={() => setPesan(null)}>Bersihkan pesan</button>
      </div>

      <section className="creator-media-section">
        <div className="creator-section-head">
          <h3>Media</h3>
          <span>Pilih foto, video, atau ambil langsung dari kamera</span>
        </div>

        <div className="creator-media-dropzone">
          <ImagePicker
            value={form.media}
            onChange={(media) => setForm({ ...form, media })}
            multiple
            max={8}
            folder={`explore/${uid || 'creator'}`}
            label="Media karya"
            hint="Gunakan foto atau video dengan rasio 16:9."
            izinkanVideo
            onMediaTypeChange={(type) => setForm({ ...form, type })}
          />
        </div>
      </section>

      <div className="creator-footer-actions">
        <button type="button" className="creator-footer-btn secondary" onClick={simpanDraft}>Simpan Draft</button>
        <button type="submit" className="creator-footer-btn primary" form="creator-post-form">Publikasikan</button>
      </div>
    </div>
  );

  return (
    <div className="creator-shell">
      {dialog}
      {mediaTabs}

      {mode === 'portfolio' ? (
        <CreatorPortfolio embedded />
      ) : (
        <>
          <form id="creator-post-form" onSubmit={simpan} className="creator-form-modern">
            {uploadCard}

            {pesan && (
              <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'} creator-message-wrap`}>
                <div className="msg">{pesan.teks}</div>
              </div>
            )}

            <div className="creator-extra-form">
              <div className="mb-md">
                <label className="field-label">Caption</label>
                <textarea className="input" rows={3} value={form.caption}
                  placeholder="Ceritakan sedikit tentang karya ini."
                  onChange={(e) => setForm({ ...form, caption: e.target.value })} />
              </div>

              <div className="form-row mb-md creator-form-row">
                <div style={{ flex: 1 }}>
                  <label className="field-label">Kategori</label>
                  {kategori.length > 0 ? (
                    <select className="input" value={form.category}
                      onChange={(e) => setForm({ ...form, category: e.target.value })}>
                      <option value="">(tanpa kategori)</option>
                      {kategori.map((k) => (
                        <option key={k.id} value={k.name || k.id}>{k.name || k.id}</option>
                      ))}
                    </select>
                  ) : (
                    <input className="input" value={form.category} placeholder="Wedding"
                      onChange={(e) => setForm({ ...form, category: e.target.value })} />
                  )}
                </div>
                <div style={{ flex: 1 }}>
                  <label className="field-label">Lokasi</label>
                  <input className="input" value={form.location} placeholder="Yogyakarta"
                    onChange={(e) => setForm({ ...form, location: e.target.value })} />
                </div>
              </div>

              <div className="mb-md">
                <label className="field-label">Tagar (pisahkan koma)</label>
                <input className="input" value={form.tags} placeholder="prewedding, outdoor, golden hour"
                  onChange={(e) => setForm({ ...form, tags: e.target.value })} />
              </div>

              <div className="form-row mb-md creator-form-row">
                <div style={{ flex: 1 }}>
                  <label className="field-label">Kebijakan Komentar</label>
                  <select className="input" value={form.commentPolicy}
                    onChange={(e) => setForm({ ...form, commentPolicy: e.target.value })}>
                    {KEBIJAKAN_KOMENTAR.map((k) => <option key={k.id} value={k.id}>{k.label}</option>)}
                  </select>
                </div>
              </div>

              <label className="field-label creator-checkbox" style={{ marginBottom: 16 }}>
                <input type="checkbox" checked={form.allowSave}
                  onChange={(e) => setForm({ ...form, allowSave: e.target.checked })} />
                Izinkan orang menyimpan karya ini
              </label>
            </div>
          </form>

          <div className="creator-grid-summary">
            <div className="grid grid-3 mb-lg creator-stats">
              <StatCard label="Karya Tayang" value={compactNumber(totalTayang)} delta={`${posts.length} total unggahan`} icon="image" tone="utama" />
              <StatCard label="Total Suka" value={compactNumber(totalSuka)} icon="star" tone="peringatan" />
              <StatCard label="Total Dilihat" value={compactNumber(totalDilihat)} icon="chart" tone="info" />
            </div>
            <div className="section-title creator-posts-title">Unggahan Saya</div>
            {daftar.length === 0 ? (
              <EmptyState title="Belum ada unggahan" hint="Karya yang dikirim akan muncul di sini beserta status review-nya." />
            ) : (
              <div className="grid grid-3 creator-post-list">
                {daftar.map((post) => (
                  <article className="card creator-post-card" key={post.id}>
                    {post.thumbnailUrl || post.mediaUrls?.[0] ? (
                      <img src={post.thumbnailUrl || post.mediaUrls[0]} alt="" className="creator-post-thumb" />
                    ) : null}
                    <div className="creator-post-card-head">
                      <strong>{post.caption || 'Tanpa caption'}</strong>
                      <StatusBadge status={post.status} />
                    </div>
                    <div className="text-meta">{post.category || 'Tanpa kategori'} · {post.mediaUrls?.length || 0} media</div>
                    {post.moderationNote && (
                      <div className="creator-moderation-note">Catatan admin: {post.moderationNote}</div>
                    )}
                    <div className="creator-post-card-actions">
                      <button className="btn btn-outline btn-sm" type="button" onClick={() => bukaUbah(post)}>Ubah</button>
                      <button className="btn btn-danger btn-sm" type="button" onClick={() => hapus(post)}>Hapus</button>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}

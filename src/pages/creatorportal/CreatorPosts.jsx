import { useMemo, useState } from 'react';
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
  { id: 'published', label: 'Tayang' },
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
  const [form, setForm] = useState(null); // null = tertutup
  const [menyimpan, setMenyimpan] = useState(false);
  const [pesan, setPesan] = useState(null);

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
          status: 'published',
          moderationNote: null,
          metrics: { like: 0, comment: 0, save: 0, view: 0, share: 0 },
          createdAt: serverTimestamp(),
        });
        setPesan({ tipe: 'sukses', teks: 'Karya terunggah dan langsung tayang.' });
      }
      setForm(null);
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

  const mediaTabs = (
    <div className="pill-tabs" style={{ marginBottom: 20 }}>
      <button className={`pill-tab${mode === 'karya' ? ' active' : ''}`} onClick={() => setMode('karya')}>
        Karya Explore
      </button>
      <button className={`pill-tab${mode === 'portfolio' ? ' active' : ''}`} onClick={() => setMode('portfolio')}>
        Portfolio
      </button>
    </div>
  );

  if (mode === 'portfolio') {
    return (
      <div>
        {dialog}
        <h1 className="page-title">Unggah Karya</h1>
        {mediaTabs}
        <CreatorPortfolio embedded />
      </div>
    );
  }

  return (
    <div>
      {dialog}
      <h1 className="page-title">Unggah Karya</h1>
      {mediaTabs}
      <p className="text-meta" style={{ marginBottom: 20 }}>
        Karya yang Anda unggah ke Explore. Karya baru ditinjau admin dulu sebelum tayang di aplikasi.
      </p>

      {pesan && (
        <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
          <div className="msg">{pesan.teks}</div>
        </div>
      )}

      <div className="grid grid-3 mb-lg">
        <StatCard label="Karya Tayang" value={compactNumber(totalTayang)} delta={`${posts.length} total unggahan`} icon="image" tone="utama" />
        <StatCard label="Total Suka" value={compactNumber(totalSuka)} icon="star" tone="peringatan" />
        <StatCard label="Total Dilihat" value={compactNumber(totalDilihat)} icon="chart" tone="info" />
      </div>

      {form && (
        <form className="card mb-lg" onSubmit={simpan}>
          <div className="section-title">{form.id ? 'Ubah Karya' : 'Unggah Karya Baru'}</div>

          <div className="mb-md">
            <ImagePicker
              label="Foto Karya"
              multiple
              max={10}
              izinkanVideo
              folder={`explore/${uid}`}
              value={form.media}
              onChange={(media) => setForm({ ...form, media })}
              onMediaTypeChange={(type) => setForm((sebelumnya) => ({ ...sebelumnya, type }))}
              hint="Pilih foto atau video dari perangkat. Media pertama menjadi sampul di feed."
            />
          </div>

          <div className="mb-md">
            <label className="field-label">Caption</label>
            <textarea className="input" rows={3} value={form.caption}
              placeholder="Ceritakan sedikit tentang karya ini."
              onChange={(e) => setForm({ ...form, caption: e.target.value })} />
          </div>

          <div className="form-row mb-md">
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

          <div className="form-row mb-md">
            <div style={{ flex: 1 }}>
              <label className="field-label">Kebijakan Komentar</label>
              <select className="input" value={form.commentPolicy}
                onChange={(e) => setForm({ ...form, commentPolicy: e.target.value })}>
                {KEBIJAKAN_KOMENTAR.map((k) => <option key={k.id} value={k.id}>{k.label}</option>)}
              </select>
            </div>
          </div>

          <label className="field-label" style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
            <input type="checkbox" checked={form.allowSave}
              onChange={(e) => setForm({ ...form, allowSave: e.target.checked })} />
            Izinkan orang menyimpan karya ini
          </label>

          {form.id && form.status === 'published' && (
            <p className="text-meta" style={{ marginTop: 0 }}>
              Karya ini sudah tayang — perubahan langsung terlihat di aplikasi tanpa ditinjau ulang.
            </p>
          )}

          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-primary" type="submit" disabled={menyimpan}>
              {menyimpan ? 'Menyimpan...' : form.id ? 'Simpan Perubahan' : 'Unggah Karya'}
            </button>
            <button className="btn btn-outline" type="button" onClick={() => setForm(null)}>Batal</button>
          </div>
        </form>
      )}

      <div className="flex-between mb-md">
        <div className="pill-tabs" style={{ marginBottom: 0 }}>
          {TAB.map((t) => (
            <button key={t.id} className={`pill-tab${tab === t.id ? ' active' : ''}`} onClick={() => setTab(t.id)}>
              {t.label}
              {t.id !== 'semua' && ` (${jumlahPerStatus[t.id] || 0})`}
            </button>
          ))}
        </div>
        {!form && <button className="btn btn-primary" onClick={bukaBaru}>+ Unggah Karya</button>}
      </div>

      {loading ? (
        <div className="loading">Memuat karya...</div>
      ) : error ? (
        <ErrorState error={error} onRetry={() => window.location.reload()} />
      ) : daftar.length === 0 ? (
        <EmptyState
          glyph="▦"
          title={tab === 'semua' ? 'Belum ada karya yang diunggah' : 'Tidak ada karya pada status ini'}
          hint={tab === 'semua' ? 'Tekan "Unggah Karya" untuk menampilkan hasil kerja Anda di Explore.' : undefined}
        />
      ) : (
        <div className="grid grid-3">
          {daftar.map((p) => (
            <div className="card media-card" key={p.id}>
              {p.mediaUrls?.[0] ? (
                p.type === 'video' ? <video className="media-thumb" src={p.mediaUrls[0]} controls preload="metadata" /> : <img className="media-thumb" src={p.thumbnailUrl || p.mediaUrls[0]} alt=""
                  onError={(e) => { e.target.style.visibility = 'hidden'; }} />
              ) : (
                <div className="media-thumb" style={{ display: 'grid', placeItems: 'center', color: 'var(--text-secondary)' }}>
                  tanpa media
                </div>
              )}

              <div className="media-meta">
                <StatusBadge status={p.status} />
                <span className="text-meta">{(p.mediaUrls || []).length} {p.type === 'video' ? 'video' : 'foto'}</span>
              </div>

              <div className="media-caption">{p.caption || '(tanpa caption)'}</div>
              <div className="text-meta" style={{ marginBottom: 8 }}>
                {p.category || 'Tanpa kategori'} · {formatDateTime(p.createdAt)}
              </div>

              {/* Alasan penolakan ditulis admin khusus untuk dibaca di sini. */}
              {p.moderationNote && (
                <div className="banner-danger" style={{ padding: '10px 12px', borderRadius: 'var(--radius-md)', marginBottom: 10 }}>
                  <div className="msg"><strong>Catatan admin:</strong> {p.moderationNote}</div>
                </div>
              )}

              <div className="text-meta" style={{ marginBottom: 10 }}>
                ♥ {p.metrics?.like ?? 0} · 💬 {p.metrics?.comment ?? 0} · 👁 {p.metrics?.view ?? 0}
              </div>

              <div style={{ display: 'flex', gap: 8 }}>
                <button className="btn btn-outline btn-sm" onClick={() => bukaUbah(p)}>Ubah</button>
                <button className="btn btn-danger btn-sm" onClick={() => hapus(p)}>Hapus</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

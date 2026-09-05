import { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { addDoc, collection, deleteDoc, doc, updateDoc, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useDocument } from '../../hooks/useDocument';
import { useCollection } from '../../hooks/useCollection';
import { usePermission } from '../../hooks/usePermission';
import StatusBadge from '../../components/StatusBadge';
import DataTable from '../../components/DataTable';
import ErrorState from '../../components/ErrorState';
import EmptyState from '../../components/EmptyState';
import LocationPicker from '../../components/LocationPicker';
import ImagePicker from '../../components/ImagePicker';
import { useConfirm } from '../../components/ConfirmDialog';
import { logAdminAction, adminDeleteUser } from '../../utils/adminActions';
import { formatCurrency, formatDate } from '../../utils/format';
import { byNewest } from '../../utils/sort';

const TABS = ['Profil', 'Media', 'Paket', 'Booking', 'Ulasan'];
const PAKET_KOSONG = { name: '', price: '', duration: '', personnel: '', output: '', description: '', active: true };

/**
 * Pusat kendali admin atas satu creator.
 *
 * Aturan Firestore sudah memberi admin hak penuh atas creators, packages,
 * portfolios, dan explore_posts sejak awal — yang belum ada hanyalah
 * antarmukanya. Halaman ini menutup selisih itu: seluruh milik creator bisa
 * dilihat, diubah, dan dihapus dari satu tempat.
 */
export default function CreatorDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { can } = usePermission();
  const { confirm, dialog } = useConfirm();

  const { data: creator, loading, error } = useDocument(doc(db, PATHS.creators, id));
  const { data: paketRaw, error: paketError } = useCollection(collection(db, PATHS.packages), [where('creatorId', '==', id)]);
  const { data: postRaw, error: postError } = useCollection(collection(db, PATHS.explorePosts), [where('creatorId', '==', id)]);
  const { data: portfolioRaw, error: portfolioError } = useCollection(collection(db, PATHS.portfolios), [where('creatorId', '==', id)]);
  const { data: bookingRaw, error: bookingError } = useCollection(collection(db, PATHS.bookings), [where('creatorId', '==', id)]);
  const { data: ulasanRaw, error: ulasanError } = useCollection(collection(db, PATHS.reviews), [where('creatorId', '==', id)]);
  const { data: wallet } = useCollection(collection(db, PATHS.wallets));

  const dompet = wallet.find((w) => w.id === id);
  const posts = byNewest(postRaw);
  const bookings = byNewest(bookingRaw);
  const ulasan = byNewest(ulasanRaw);

  const bolehUbah = can('manage_users');
  const bolehModerasi = can('moderate_content');
  const bolehVerifikasi = can('verify_creator');

  const [tab, setTab] = useState('Profil');
  const [form, setForm] = useState(null);
  const [paketForm, setPaketForm] = useState(null);
  const [sibuk, setSibuk] = useState(false);
  const [pesan, setPesan] = useState(null);

  useEffect(() => {
    if (creator && !form) {
      setForm({
        displayName: creator.displayName || '',
        bio: creator.bio || '',
        city: creator.city || '',
        categories: (creator.categories || []).join(', '),
        equipment: (creator.equipment || []).join(', '),
        minPrice: creator.minPrice ?? '',
        photoUrl: creator.photoUrl || '',
        coverUrl: creator.coverUrl || '',
        serviceLat: creator.serviceLat ?? '',
        serviceLng: creator.serviceLng ?? '',
        serviceAddress: creator.serviceAddress || '',
      });
    }
  }, [creator, form]);

  const daftar = (t) => t.split(',').map((s) => s.trim()).filter(Boolean);
  const sukses = (teks) => setPesan({ tipe: 'sukses', teks });
  const gagal = (err, bawaan) => setPesan({ tipe: 'gagal', teks: err?.message || bawaan });

  // ---------------- Profil ----------------
  const simpanProfil = async (e) => {
    e.preventDefault();
    if (!form.displayName.trim()) { gagal(null, 'Nama tampilan wajib diisi.'); return; }
    setSibuk(true); setPesan(null);
    try {
      await updateDoc(doc(db, PATHS.creators, id), {
        displayName: form.displayName.trim(),
        bio: form.bio.trim() || null,
        city: form.city.trim() || null,
        categories: daftar(form.categories),
        equipment: daftar(form.equipment),
        minPrice: form.minPrice === '' ? null : Number(form.minPrice),
        photoUrl: form.photoUrl.trim() || null,
        coverUrl: form.coverUrl.trim() || null,
        serviceLat: form.serviceLat === '' ? null : Number(form.serviceLat),
        serviceLng: form.serviceLng === '' ? null : Number(form.serviceLng),
        serviceAddress: form.serviceAddress?.trim() || null,
      });
      await logAdminAction({ action: 'update_creator', targetType: 'creator', targetId: id, reason: form.displayName.trim() }).catch(() => {});
      sukses('Profil creator tersimpan.');
    } catch (err) { gagal(err, 'Gagal menyimpan profil.'); } finally { setSibuk(false); }
  };

  const toggleVerified = async () => {
    const nyalakan = !creator.verified;
    if (!(await confirm({
      title: nyalakan ? 'Tandai terverifikasi?' : 'Cabut badge verifikasi?',
      message: nyalakan ? 'Creator akan mendapat badge terverifikasi di aplikasi.' : 'Badge terverifikasi akan hilang.',
      danger: !nyalakan,
    }))) return;
    await updateDoc(doc(db, PATHS.creators, id), { verified: nyalakan });
    await logAdminAction({ action: nyalakan ? 'verify_creator' : 'unverify_creator', targetType: 'creator', targetId: id }).catch(() => {});
  };

  const toggleSuspend = async () => {
    const suspend = creator.status === 'active';
    if (!(await confirm({
      title: suspend ? 'Suspend creator ini?' : 'Aktifkan kembali creator ini?',
      message: suspend ? 'Creator tidak tampil di Explore/Search dan tidak bisa menerima booking baru.' : 'Creator akan tampil & bisa menerima booking kembali.',
      danger: suspend,
    }))) return;
    await updateDoc(doc(db, PATHS.creators, id), { status: suspend ? 'suspended' : 'active' });
    await logAdminAction({ action: suspend ? 'suspend_creator' : 'activate_creator', targetType: 'creator', targetId: id }).catch(() => {});
  };

  const hapusCreator = async () => {
    if (!(await confirm({
      title: `Hapus creator ${creator.displayName}?`,
      message: 'Profil creator dihapus dan akun loginnya dinonaktifkan. Ditolak bila masih ada booking berjalan atau saldo dompet tersisa.',
      danger: true, confirmLabel: 'Ya, Hapus Creator',
    }))) return;
    setSibuk(true); setPesan(null);
    try {
      await adminDeleteUser({ userId: id, reason: 'creator dihapus lewat panel admin' });
      navigate('/creators');
    } catch (err) { gagal(err, 'Gagal menghapus creator.'); } finally { setSibuk(false); }
  };

  // ---------------- Media ----------------
  const ubahStatusPost = async (post, status) => {
    if (!(await confirm({
      title: `Ubah status konten ke "${status}"?`,
      message: 'Perubahan langsung terlihat di aplikasi dan tercatat di Audit Logs.',
      danger: status === 'rejected' || status === 'hidden',
    }))) return;
    try {
      await updateDoc(doc(db, PATHS.explorePosts, post.id), { status, moderationStatus: status });
      await logAdminAction({ action: `explore_${status}`, targetType: 'explore_post', targetId: post.id }).catch(() => {});
      sukses(`Konten ditandai "${status}".`);
    } catch (err) { gagal(err, 'Gagal mengubah status konten.'); }
  };

  const hapusPost = async (post) => {
    if (!(await confirm({
      title: 'Hapus konten ini permanen?',
      message: 'Konten hilang dari feed Explore dan tidak bisa dikembalikan.',
      danger: true, confirmLabel: 'Ya, Hapus',
    }))) return;
    try {
      await deleteDoc(doc(db, PATHS.explorePosts, post.id));
      await logAdminAction({ action: 'delete_explore_post', targetType: 'explore_post', targetId: post.id }).catch(() => {});
      sukses('Konten dihapus.');
    } catch (err) { gagal(err, 'Gagal menghapus konten.'); }
  };

  const hapusPortfolio = async (p) => {
    if (!(await confirm({
      title: `Hapus album "${p.title}"?`,
      message: 'Album ini akan hilang dari profil creator di aplikasi.',
      danger: true, confirmLabel: 'Ya, Hapus',
    }))) return;
    try {
      await deleteDoc(doc(db, PATHS.portfolios, p.id));
      await logAdminAction({ action: 'delete_portfolio', targetType: 'portfolio', targetId: p.id }).catch(() => {});
      sukses('Album portfolio dihapus.');
    } catch (err) { gagal(err, 'Gagal menghapus album.'); }
  };

  // ---------------- Paket ----------------
  const simpanPaket = async (e) => {
    e.preventDefault();
    if (!paketForm.name.trim()) { gagal(null, 'Nama paket wajib diisi.'); return; }
    if (!paketForm.price || Number(paketForm.price) <= 0) { gagal(null, 'Harga wajib diisi dan lebih dari 0.'); return; }
    setSibuk(true); setPesan(null);
    const isi = {
      creatorId: id,
      name: paketForm.name.trim(),
      price: Number(paketForm.price),
      duration: paketForm.duration.trim() || null,
      personnel: paketForm.personnel.trim() || null,
      output: paketForm.output.trim() || null,
      description: paketForm.description.trim() || null,
      active: paketForm.active,
    };
    try {
      if (paketForm.id) await updateDoc(doc(db, PATHS.packages, paketForm.id), isi);
      else await addDoc(collection(db, PATHS.packages), isi);
      await logAdminAction({
        action: paketForm.id ? 'update_package' : 'create_package',
        targetType: 'package', targetId: paketForm.id || null, reason: isi.name,
      }).catch(() => {});
      setPaketForm(null);
      sukses(paketForm.id ? 'Paket diperbarui.' : 'Paket ditambahkan.');
    } catch (err) {
      // Aturan Firestore versi lama hanya mengizinkan CREATE paket oleh creator
      // pemiliknya, sementara UPDATE dan DELETE sudah mengizinkan admin. Selama
      // firestore.rules terbaru belum di-deploy, tombol tambah akan ditolak —
      // pesan bawaan "Missing or insufficient permissions" tidak menjelaskan
      // apa pun kepada admin yang menekannya.
      if (err?.code === 'permission-denied' && !paketForm.id) {
        gagal(null, 'Membuat paket dari panel admin masih diblokir aturan Firestore yang aktif. '
          + 'Jalankan "firebase deploy --only firestore:rules" untuk menerapkan perbaikannya. '
          + 'Mengubah dan menghapus paket sudah bisa dilakukan sekarang.');
      } else {
        gagal(err, 'Gagal menyimpan paket.');
      }
    } finally { setSibuk(false); }
  };

  const hapusPaket = async (p) => {
    if (!(await confirm({
      title: `Hapus paket "${p.name}"?`,
      message: 'Paket tidak bisa dipesan lagi. Booking lama tidak terpengaruh.',
      danger: true, confirmLabel: 'Ya, Hapus',
    }))) return;
    try {
      await deleteDoc(doc(db, PATHS.packages, p.id));
      await logAdminAction({ action: 'delete_package', targetType: 'package', targetId: p.id, reason: p.name }).catch(() => {});
      sukses('Paket dihapus.');
    } catch (err) { gagal(err, 'Gagal menghapus paket.'); }
  };

  // ---------------- Ulasan ----------------
  const ubahStatusUlasan = async (r, status) => {
    if (!(await confirm({ title: status === 'hidden' ? 'Sembunyikan ulasan?' : 'Tampilkan ulasan?', message: 'Perubahan langsung terlihat publik.' }))) return;
    try {
      await updateDoc(doc(db, PATHS.reviews, r.id), { status });
      await logAdminAction({ action: `review_${status}`, targetType: 'review', targetId: r.id }).catch(() => {});
    } catch (err) { gagal(err, 'Gagal mengubah status ulasan.'); }
  };

  if (loading) return <div className="loading">Memuat...</div>;
  if (error) return <ErrorState error={error} onRetry={() => window.location.reload()} />;
  if (!creator) return <div className="empty-state">Creator tidak ditemukan</div>;

  const jumlah = { Media: posts.length + portfolioRaw.length, Paket: paketRaw.length, Booking: bookings.length, Ulasan: ulasan.length };

  return (
    <div>
      {dialog}
      <div className="breadcrumb"><Link to="/creators">Creators</Link> / {creator.displayName}</div>

      {pesan && (
        <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
          <div className="msg">{pesan.teks}</div>
        </div>
      )}

      <div className="pill-tabs">
        {TABS.map((t) => (
          <button key={t} className={`pill-tab${tab === t ? ' active' : ''}`} onClick={() => setTab(t)}>
            {t}{jumlah[t] !== undefined ? ` (${jumlah[t]})` : ''}
          </button>
        ))}
      </div>

      {/* ============ PROFIL ============ */}
      {tab === 'Profil' && (
        <div className="grid grid-2">
          <form className="card" onSubmit={simpanProfil}>
            <div className="section-title">Ubah Profil Creator</div>
            <div className="mb-md">
              <label className="field-label">Nama Tampilan</label>
              <input className="input" value={form?.displayName ?? ''} disabled={!bolehUbah}
                onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
            </div>
            <div className="mb-md">
              <label className="field-label">Bio</label>
              <textarea className="input" rows={3} value={form?.bio ?? ''} disabled={!bolehUbah}
                onChange={(e) => setForm({ ...form, bio: e.target.value })} />
            </div>
            <div className="mb-md">
              <label className="field-label">Harga Mulai (Rp)</label>
              <input className="input" type="number" value={form?.minPrice ?? ''} disabled={!bolehUbah}
                onChange={(e) => setForm({ ...form, minPrice: e.target.value })} />
            </div>
            <div className="mb-md">
              <label className="field-label">Kota & Titik Layanan</label>
              <LocationPicker
                disabled={!bolehUbah}
                nilai={{
                  city: form?.city ?? '',
                  lat: form?.serviceLat ?? '',
                  lng: form?.serviceLng ?? '',
                  address: form?.serviceAddress ?? '',
                }}
                onChange={(v) => setForm({
                  ...form, city: v.city, serviceLat: v.lat, serviceLng: v.lng, serviceAddress: v.address,
                })}
              />
            </div>
            <div className="mb-md">
              <label className="field-label">Kategori (pisahkan koma)</label>
              <input className="input" value={form?.categories ?? ''} disabled={!bolehUbah}
                onChange={(e) => setForm({ ...form, categories: e.target.value })} />
            </div>
            <div className="mb-md">
              <label className="field-label">Peralatan (pisahkan koma)</label>
              <input className="input" value={form?.equipment ?? ''} disabled={!bolehUbah}
                onChange={(e) => setForm({ ...form, equipment: e.target.value })} />
            </div>
            <div className="mb-md">
              <ImagePicker
                label="Foto Profil"
                // Folder mengikuti UID CREATOR, bukan admin yang sedang login —
                // storage.rules memberi admin izin menulis ke folder creator
                // mana pun, dan menaruhnya di folder si creator membuat
                // berkasnya tetap ikut terhapus saat akunnya dibersihkan.
                folder={`profiles/${id}`}
                value={form?.photoUrl ?? ''}
                onChange={(photoUrl) => setForm({ ...form, photoUrl })}
                disabled={!bolehUbah}
                sisiMaks={800}
                targetByte={180 * 1024}
              />
            </div>
            <div className="mb-md">
              <ImagePicker
                label="Foto Sampul"
                folder={`profiles/${id}`}
                value={form?.coverUrl ?? ''}
                onChange={(coverUrl) => setForm({ ...form, coverUrl })}
                disabled={!bolehUbah}
              />
            </div>
            {!bolehUbah && <p className="text-danger-sm">Role Anda tidak memiliki izin <code>manage_users</code>.</p>}
            <button className="btn btn-primary" type="submit" disabled={!bolehUbah || sibuk}>
              {sibuk ? 'Menyimpan...' : 'Simpan Perubahan'}
            </button>
          </form>

          <div>
            <div className="card mb-lg">
              <div className="section-title">Ringkasan</div>
              <div className="detail-row"><span className="k">Creator ID</span><span className="v">{id}</span></div>
              <div className="detail-row"><span className="k">Rating</span><span className="v">{(creator.rating || 0).toFixed(1)} ({creator.reviewCount || 0})</span></div>
              <div className="detail-row"><span className="k">Pengikut</span><span className="v num">{creator.followerCount || 0}</span></div>
              <div className="detail-row"><span className="k">Verified</span><span className="v"><StatusBadge status={creator.verified ? 'active' : 'pending'} /></span></div>
              <div className="detail-row"><span className="k">Status</span><span className="v"><StatusBadge status={creator.status} /></span></div>
              <div className="detail-row"><span className="k">Saldo Tersedia</span><span className="v num">{formatCurrency(dompet?.availableBalance || 0)}</span></div>
              <div className="detail-row"><span className="k">Saldo Tertahan</span><span className="v num">{formatCurrency(dompet?.pendingBalance || 0)}</span></div>
              <div className="detail-row"><span className="k">Total Pendapatan</span><span className="v num">{formatCurrency(dompet?.totalEarnings || 0)}</span></div>
            </div>
            <div className="card">
              <div className="section-title">Aksi</div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <button className="btn btn-outline" disabled={!bolehVerifikasi || sibuk} onClick={toggleVerified}>
                  {creator.verified ? 'Cabut Verifikasi' : 'Tandai Terverifikasi'}
                </button>
                <button className={`btn ${creator.status === 'active' ? 'btn-danger' : 'btn-success'}`}
                  disabled={!bolehUbah || sibuk} onClick={toggleSuspend}>
                  {creator.status === 'active' ? 'Suspend Creator' : 'Aktifkan Kembali'}
                </button>
                <button className="btn btn-danger" disabled={!bolehUbah || sibuk} onClick={hapusCreator}>
                  Hapus Creator
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ============ MEDIA ============ */}
      {tab === 'Media' && (
        <div>
          <div className="section-title">Konten Explore ({posts.length})</div>
          {postError ? <ErrorState error={postError} onRetry={() => window.location.reload()} />
            : posts.length === 0 ? <EmptyState title="Belum ada konten Explore" />
            : (
              <div className="grid grid-3 mb-lg">
                {posts.map((p) => (
                  <div className="card media-card" key={p.id}>
                    {/* Video diputar sungguhan di sini — admin perlu MENONTON isinya
                        untuk memoderasi, bukan sekadar melihat gambar sampulnya. */}
                    {p.type === 'video' && p.mediaUrls?.[0] ? (
                      <video className="media-thumb" controls preload="none" poster={p.thumbnailUrl || undefined} src={p.mediaUrls[0]} />
                    ) : (
                      <img className="media-thumb" src={p.thumbnailUrl || p.mediaUrls?.[0]} alt=""
                        onError={(e) => { e.target.style.visibility = 'hidden'; }} />
                    )}
                    <div className="media-meta">
                      <span className={`badge badge-${p.type === 'video' ? 'info' : 'neutral'}`}>{p.type}</span>
                      <StatusBadge status={p.status} />
                    </div>
                    <div className="media-caption">{p.caption || '(tanpa caption)'}</div>
                    <div className="text-meta" style={{ marginBottom: 8 }}>
                      {p.category || '-'} · {p.location || 'tanpa lokasi'} · {formatDate(p.createdAt)}
                    </div>
                    <div className="text-meta" style={{ marginBottom: 10 }}>
                      ♥ {p.metrics?.like ?? 0} · 💬 {p.metrics?.comment ?? 0} · 👁 {p.metrics?.view ?? 0}
                    </div>
                    <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                      <Link className="btn btn-outline btn-sm" to={`/explore/${p.id}`}>Detail</Link>
                      {p.status !== 'published' && (
                        <button className="btn btn-success btn-sm" disabled={!bolehModerasi}
                          onClick={() => ubahStatusPost(p, 'published')}>Tayangkan</button>
                      )}
                      {p.status !== 'hidden' && (
                        <button className="btn btn-outline btn-sm" disabled={!bolehModerasi}
                          onClick={() => ubahStatusPost(p, 'hidden')}>Sembunyikan</button>
                      )}
                      <button className="btn btn-danger btn-sm" disabled={!bolehModerasi}
                        onClick={() => hapusPost(p)}>Hapus</button>
                    </div>
                  </div>
                ))}
              </div>
            )}

          <div className="section-title">Album Portfolio ({portfolioRaw.length})</div>
          {portfolioError ? <ErrorState error={portfolioError} onRetry={() => window.location.reload()} />
            : portfolioRaw.length === 0 ? <EmptyState title="Belum ada album portfolio" />
            : (
              <div className="grid grid-3">
                {portfolioRaw.map((p) => (
                  <div className="card media-card" key={p.id}>
                    {p.media?.[0] && (
                      <img className="media-thumb" src={p.media[0]} alt=""
                        onError={(e) => { e.target.style.visibility = 'hidden'; }} />
                    )}
                    <div style={{ fontWeight: 700, marginBottom: 2 }}>{p.title}</div>
                    <div className="text-meta" style={{ marginBottom: 10 }}>
                      {p.category || 'Tanpa kategori'} · {(p.media || []).length} media
                    </div>
                    <button className="btn btn-danger btn-sm" disabled={!bolehModerasi} onClick={() => hapusPortfolio(p)}>
                      Hapus Album
                    </button>
                  </div>
                ))}
              </div>
            )}
        </div>
      )}

      {/* ============ PAKET ============ */}
      {tab === 'Paket' && (
        <div>
          {paketForm && (
            <form className="card mb-lg" onSubmit={simpanPaket}>
              <div className="section-title">{paketForm.id ? 'Ubah Paket' : 'Paket Baru'}</div>
              <div className="mb-md" style={{ display: 'flex', gap: 12 }}>
                <div style={{ flex: 2 }}>
                  <label className="field-label">Nama Paket</label>
                  <input className="input" value={paketForm.name}
                    onChange={(e) => setPaketForm({ ...paketForm, name: e.target.value })} />
                </div>
                <div style={{ flex: 1 }}>
                  <label className="field-label">Harga (Rp)</label>
                  <input className="input" type="number" value={paketForm.price}
                    onChange={(e) => setPaketForm({ ...paketForm, price: e.target.value })} />
                </div>
              </div>
              <div className="mb-md" style={{ display: 'flex', gap: 12 }}>
                <div style={{ flex: 1 }}>
                  <label className="field-label">Durasi</label>
                  <input className="input" value={paketForm.duration}
                    onChange={(e) => setPaketForm({ ...paketForm, duration: e.target.value })} />
                </div>
                <div style={{ flex: 1 }}>
                  <label className="field-label">Personel</label>
                  <input className="input" value={paketForm.personnel}
                    onChange={(e) => setPaketForm({ ...paketForm, personnel: e.target.value })} />
                </div>
              </div>
              <div className="mb-md">
                <label className="field-label">Hasil yang Didapat</label>
                <input className="input" value={paketForm.output}
                  onChange={(e) => setPaketForm({ ...paketForm, output: e.target.value })} />
              </div>
              <div className="mb-md">
                <label className="field-label">Deskripsi</label>
                <textarea className="input" rows={3} value={paketForm.description}
                  onChange={(e) => setPaketForm({ ...paketForm, description: e.target.value })} />
              </div>
              <label className="field-label" style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
                <input type="checkbox" checked={paketForm.active}
                  onChange={(e) => setPaketForm({ ...paketForm, active: e.target.checked })} />
                Paket aktif (bisa dipesan pelanggan)
              </label>
              <div style={{ display: 'flex', gap: 8 }}>
                <button className="btn btn-primary" type="submit" disabled={sibuk}>
                  {sibuk ? 'Menyimpan...' : 'Simpan Paket'}
                </button>
                <button className="btn btn-outline" type="button" onClick={() => setPaketForm(null)}>Batal</button>
              </div>
            </form>
          )}

          <div className="table-wrap">
            <div className="table-toolbar">
              <span className="text-meta">{paketRaw.length} paket</span>
              {!paketForm && (
                <button className="btn btn-primary btn-sm" disabled={!bolehUbah}
                  onClick={() => { setPesan(null); setPaketForm({ ...PAKET_KOSONG }); }}>+ Tambah Paket</button>
              )}
            </div>
            <DataTable
              error={paketError}
              onRetry={() => window.location.reload()}
              emptyTitle="Belum ada paket"
              columns={[
                { key: 'name', label: 'Nama' },
                { key: 'price', label: 'Harga', render: (r) => formatCurrency(r.price) },
                { key: 'duration', label: 'Durasi', render: (r) => r.duration || '-' },
                { key: 'active', label: 'Status', render: (r) => <StatusBadge status={r.active ? 'active' : 'suspended'} /> },
                {
                  key: 'aksi', label: 'Aksi',
                  render: (r) => (
                    <div style={{ display: 'flex', gap: 8 }}>
                      <button className="btn btn-outline btn-sm" disabled={!bolehUbah} onClick={() => {
                        setPesan(null);
                        setPaketForm({
                          id: r.id, name: r.name || '', price: r.price ?? '', duration: r.duration || '',
                          personnel: r.personnel || '', output: r.output || '',
                          description: r.description || '', active: r.active !== false,
                        });
                      }}>Ubah</button>
                      <button className="btn btn-danger btn-sm" disabled={!bolehUbah} onClick={() => hapusPaket(r)}>Hapus</button>
                    </div>
                  ),
                },
              ]}
              rows={paketRaw}
            />
          </div>
        </div>
      )}

      {/* ============ BOOKING ============ */}
      {tab === 'Booking' && (
        <div className="table-wrap">
          <div className="table-toolbar"><span className="text-meta">{bookings.length} booking</span></div>
          <DataTable
            error={bookingError}
            onRetry={() => window.location.reload()}
            emptyTitle="Belum ada booking"
            onRowClick={(row) => navigate(`/bookings/${row.id}`)}
            columns={[
              { key: 'packageName', label: 'Paket' },
              { key: 'customerName', label: 'Pelanggan', render: (r) => r.customerName || r.customerId || '-' },
              { key: 'location', label: 'Lokasi', render: (r) => r.location || '-' },
              { key: 'total', label: 'Total', render: (r) => formatCurrency(r.total) },
              { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
              { key: 'createdAt', label: 'Tanggal', render: (r) => formatDate(r.createdAt) },
            ]}
            rows={bookings}
          />
        </div>
      )}

      {/* ============ ULASAN ============ */}
      {tab === 'Ulasan' && (
        <div className="table-wrap">
          <div className="table-toolbar"><span className="text-meta">{ulasan.length} ulasan</span></div>
          <DataTable
            error={ulasanError}
            onRetry={() => window.location.reload()}
            emptyTitle="Belum ada ulasan"
            columns={[
              { key: 'customerName', label: 'Pelanggan' },
              { key: 'rating', label: 'Rating', render: (r) => '★'.repeat(r.rating || 0) },
              { key: 'text', label: 'Ulasan', render: (r) => (r.text || '').slice(0, 70) },
              { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
              {
                key: 'aksi', label: 'Aksi',
                render: (r) => (
                  <div style={{ display: 'flex', gap: 8 }}>
                    <button className="btn btn-outline btn-sm" disabled={!bolehModerasi}
                      onClick={() => ubahStatusUlasan(r, r.status === 'hidden' ? 'published' : 'hidden')}>
                      {r.status === 'hidden' ? 'Tampilkan' : 'Sembunyikan'}
                    </button>
                  </div>
                ),
              },
            ]}
            rows={ulasan}
          />
        </div>
      )}
    </div>
  );
}

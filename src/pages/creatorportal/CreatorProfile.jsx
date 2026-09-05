import { useEffect, useState } from 'react';
import { doc, updateDoc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useDocument } from '../../hooks/useDocument';
import { useAuth } from '../../auth/AuthContext';
import StatusBadge from '../../components/StatusBadge';
import ErrorState from '../../components/ErrorState';
import LocationPicker from '../../components/LocationPicker';
import ImagePicker from '../../components/ImagePicker';

/**
 * Pengaturan profil milik creator sendiri.
 *
 * Field yang bisa diubah di sini dibatasi oleh firestore.rules: creator boleh
 * menyunting dokumennya sendiri KECUALI rating, jumlah ulasan, status akun, dan
 * status verifikasi. Karena itu keempatnya ditampilkan sebagai informasi saja —
 * bukan sekadar disembunyikan, supaya creator tahu nilainya sekaligus tahu
 * bahwa yang mengubahnya adalah admin, bukan dirinya.
 */
export default function CreatorProfile() {
  const { user } = useAuth();
  const uid = user?.uid;
  const { data: creator, loading, error } = useDocument(uid ? doc(db, PATHS.creators, uid) : null);

  const [form, setForm] = useState(null);
  const [menyimpan, setMenyimpan] = useState(false);
  const [pesan, setPesan] = useState(null);

  // Diisi sekali saat dokumen selesai dimuat, supaya pembaruan dari server
  // tidak menimpa apa yang sedang diketik.
  useEffect(() => {
    if (creator && !form) {
      setForm({
        displayName: creator.displayName || '',
        bio: creator.bio || '',
        city: creator.city || '',
        categories: (creator.categories || []).join(', '),
        equipment: (creator.equipment || []).join(', '),
        socialLinks: (creator.socialLinks || []).join(', '),
        minPrice: creator.minPrice ?? '',
        photoUrl: creator.photoUrl || '',
        coverUrl: creator.coverUrl || '',
        serviceLat: creator.serviceLat ?? '',
        serviceLng: creator.serviceLng ?? '',
        serviceAddress: creator.serviceAddress || '',
      });
    }
  }, [creator, form]);

  const daftar = (teks) => teks.split(',').map((s) => s.trim()).filter(Boolean);
  const angka = (v) => (v === '' ? null : Number(v));

  const simpan = async (e) => {
    e.preventDefault();
    if (!form.displayName.trim()) {
      setPesan({ tipe: 'gagal', teks: 'Nama tampilan wajib diisi.' });
      return;
    }
    if ((form.serviceLat === '') !== (form.serviceLng === '')) {
      setPesan({ tipe: 'gagal', teks: 'Latitude dan longitude harus diisi keduanya, atau dikosongkan keduanya.' });
      return;
    }
    setMenyimpan(true);
    setPesan(null);
    try {
      await updateDoc(doc(db, PATHS.creators, uid), {
        displayName: form.displayName.trim(),
        bio: form.bio.trim() || null,
        city: form.city.trim() || null,
        categories: daftar(form.categories),
        equipment: daftar(form.equipment),
        socialLinks: daftar(form.socialLinks),
        minPrice: angka(form.minPrice),
        photoUrl: form.photoUrl.trim() || null,
        coverUrl: form.coverUrl.trim() || null,
        serviceLat: angka(form.serviceLat),
        serviceLng: angka(form.serviceLng),
        // Alamat lengkap disimpan sebagai keterangan bagi admin; yang dipakai
        // menghitung jarak di aplikasi tetap serviceLat/serviceLng.
        serviceAddress: form.serviceAddress?.trim() || null,
      });
      setPesan({ tipe: 'sukses', teks: 'Profil tersimpan. Perubahan langsung terlihat di aplikasi.' });
    } catch (err) {
      setPesan({
        tipe: 'gagal',
        teks: err.code === 'permission-denied'
          ? 'Perubahan ditolak server. Rating, status, dan verifikasi hanya bisa diubah admin.'
          : err.message || 'Gagal menyimpan profil.',
      });
    } finally {
      setMenyimpan(false);
    }
  };

  if (loading) return <div className="loading">Memuat...</div>;
  if (error) return <ErrorState error={error} onRetry={() => window.location.reload()} />;
  if (!creator) return <div className="empty-state">Profil creator tidak ditemukan</div>;

  return (
    <div>
      <h1 className="page-title">Profil Saya</h1>
      <p className="text-meta" style={{ marginBottom: 16 }}>
        Data di halaman ini yang tampil pada profil Anda di aplikasi JepretAja.
      </p>

      {pesan && (
        <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
          <div className="msg">{pesan.teks}</div>
        </div>
      )}

      <div className="grid grid-2">
        <form className="card" onSubmit={simpan}>
          <div className="section-title">Informasi Utama</div>

          <div className="mb-md">
            <label className="field-label">Nama Tampilan</label>
            <input className="input" value={form?.displayName ?? ''}
              onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
          </div>

          <div className="mb-md">
            <label className="field-label">Bio</label>
            <textarea className="input" rows={3} value={form?.bio ?? ''}
              placeholder="Ceritakan gaya dan pengalaman Anda dalam 1–2 kalimat."
              onChange={(e) => setForm({ ...form, bio: e.target.value })} />
          </div>

          <div className="mb-md">
            <label className="field-label">Harga Mulai Dari (Rp)</label>
            <input className="input" type="number" value={form?.minPrice ?? ''}
              onChange={(e) => setForm({ ...form, minPrice: e.target.value })} />
          </div>

          <div className="mb-md">
            <label className="field-label">Kategori Jasa</label>
            <input className="input" value={form?.categories ?? ''}
              placeholder="Wedding, Prewedding, Couple"
              onChange={(e) => setForm({ ...form, categories: e.target.value })} />
            <p className="text-meta" style={{ margin: '4px 0 0' }}>Pisahkan dengan koma.</p>
          </div>

          <div className="mb-md">
            <label className="field-label">Peralatan</label>
            <input className="input" value={form?.equipment ?? ''}
              placeholder="Sony A7 IV, Sigma 35mm f/1.4"
              onChange={(e) => setForm({ ...form, equipment: e.target.value })} />
          </div>

          <div className="mb-md">
            <label className="field-label">Tautan Sosial</label>
            <input className="input" value={form?.socialLinks ?? ''}
              placeholder="https://instagram.com/..., https://tiktok.com/..."
              onChange={(e) => setForm({ ...form, socialLinks: e.target.value })} />
          </div>

          <div className="section-title" style={{ marginTop: 20 }}>Foto</div>
          <div className="mb-md">
            <ImagePicker
              label="Foto Profil"
              folder={`profiles/${uid}`}
              value={form?.photoUrl ?? ''}
              onChange={(photoUrl) => setForm({ ...form, photoUrl })}
              sisiMaks={800}
              targetByte={180 * 1024}
              hint="Wajah terlihat jelas. Ditampilkan sebagai lingkaran kecil di aplikasi."
            />
          </div>
          <div className="mb-md">
            <ImagePicker
              label="Foto Sampul"
              folder={`profiles/${uid}`}
              value={form?.coverUrl ?? ''}
              onChange={(coverUrl) => setForm({ ...form, coverUrl })}
              hint="Foto lebar yang mewakili gaya kerja Anda."
            />
          </div>

          <div className="section-title" style={{ marginTop: 20 }}>Kota & Titik Layanan</div>
          <p className="text-meta" style={{ marginTop: 0, marginBottom: 10 }}>
            Dipakai fitur “Nearby” di aplikasi untuk menghitung jarak ke pelanggan.
            Pilih otomatis, cari nama tempat, atau isi manual.
          </p>
          <div className="mb-md">
            <LocationPicker
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

          <button className="btn btn-primary" type="submit" disabled={menyimpan}>
            {menyimpan ? 'Menyimpan...' : 'Simpan Profil'}
          </button>
        </form>

        <div>
          <div className="card mb-lg">
            <div className="section-title">Pratinjau</div>
            {form?.coverUrl && (
              <img src={form.coverUrl} alt="" onError={(e) => { e.target.style.display = 'none'; }}
                style={{ width: '100%', height: 120, objectFit: 'cover', borderRadius: 'var(--radius-md)', marginBottom: 12 }} />
            )}
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              {form?.photoUrl && (
                <img src={form.photoUrl} alt="" onError={(e) => { e.target.style.display = 'none'; }}
                  style={{ width: 56, height: 56, borderRadius: '50%', objectFit: 'cover' }} />
              )}
              <div>
                <div style={{ fontWeight: 700 }}>{form?.displayName || '(tanpa nama)'}</div>
                <div className="text-meta">{form?.city || 'Kota belum diisi'}</div>
              </div>
            </div>
            <p style={{ fontSize: 14, color: 'var(--text-secondary)', marginBottom: 0 }}>
              {form?.bio || 'Bio belum diisi.'}
            </p>
            <div style={{ marginTop: 10 }}>
              {daftar(form?.categories || '').map((c) => <span className="tag" key={c}>{c}</span>)}
            </div>
          </div>

          <div className="card">
            <div className="section-title">Hanya Bisa Diubah Admin</div>
            <div className="detail-row">
              <span className="k">Status Akun</span>
              <span className="v"><StatusBadge status={creator.status} /></span>
            </div>
            <div className="detail-row">
              <span className="k">Verifikasi</span>
              <span className="v"><StatusBadge status={creator.verified ? 'active' : 'pending'} /></span>
            </div>
            <div className="detail-row">
              <span className="k">Rating</span>
              <span className="v num">{(creator.rating || 0).toFixed(1)}</span>
            </div>
            <div className="detail-row">
              <span className="k">Jumlah Ulasan</span>
              <span className="v num">{creator.reviewCount || 0}</span>
            </div>
            <div className="detail-row">
              <span className="k">Pengikut</span>
              <span className="v num">{creator.followerCount || 0}</span>
            </div>
            <p className="text-meta" style={{ marginBottom: 0, marginTop: 10 }}>
              Rating dan jumlah ulasan dihitung otomatis dari ulasan pelanggan.
              Verifikasi diberikan admin setelah dokumen Anda ditinjau.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

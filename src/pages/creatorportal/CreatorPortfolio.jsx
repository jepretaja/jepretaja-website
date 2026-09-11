import { useState } from 'react';
import { addDoc, collection, deleteDoc, doc, updateDoc, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { useAuth } from '../../auth/AuthContext';
import { useConfirm } from '../../components/ConfirmDialog';
import EmptyState from '../../components/EmptyState';
import ErrorState from '../../components/ErrorState';
import ImagePicker from '../../components/ImagePicker';

const KOSONG = { title: '', category: '', media: [] };

/**
 * Portfolio milik creator ini.
 *
 * Foto dipilih langsung dari perangkat — galeri maupun kamera ponsel. Yang
 * disimpan di dokumen tetap berupa daftar URL, sama seperti yang ditulis APK;
 * yang berubah hanyalah dari mana URL itu berasal. Sebelumnya creator harus
 * menempelkan URL sendiri, yang berarti fitur ini praktis tidak bisa dipakai
 * oleh orang yang baru saja memotret dengan ponselnya.
 *
 * Tempat penyimpanan berkasnya diurus components/ImagePicker.jsx: Firebase
 * Storage kalau tersedia, dan kalau tidak, gambar terkompres ditanam langsung
 * di dalam dokumen.
 */
export default function CreatorPortfolio({ embedded = false }) {
  const { user } = useAuth();
  const uid = user?.uid;
  const { data, loading, error } = useCollection(
    collection(db, PATHS.portfolios),
    uid ? [where('creatorId', '==', uid)] : []
  );

  const [form, setForm] = useState(null);
  const [menyimpan, setMenyimpan] = useState(false);
  const [pesan, setPesan] = useState(null);
  const { confirm, dialog } = useConfirm();

  const bukaBaru = () => { setPesan(null); setForm({ ...KOSONG }); };
  const bukaUbah = (p) => {
    setPesan(null);
    setForm({
      id: p.id, title: p.title || '', category: p.category || '',
      media: [...(p.media || [])],
    });
  };

  const simpan = async (e) => {
    e.preventDefault();
    if (!form.title.trim()) { setPesan({ tipe: 'gagal', teks: 'Judul wajib diisi.' }); return; }
    const media = form.media.filter(Boolean);
    if (!media.length) { setPesan({ tipe: 'gagal', teks: 'Tambahkan minimal satu foto.' }); return; }

    setMenyimpan(true);
    setPesan(null);
    try {
      const isi = {
        creatorId: uid, title: form.title.trim(),
        category: form.category.trim() || null, media, status: 'active',
      };
      if (form.id) await updateDoc(doc(db, PATHS.portfolios, form.id), isi);
      else await addDoc(collection(db, PATHS.portfolios), isi);
      setForm(null);
      setPesan({ tipe: 'sukses', teks: form.id ? 'Portfolio diperbarui.' : 'Portfolio ditambahkan.' });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message || 'Gagal menyimpan portfolio.' });
    } finally {
      setMenyimpan(false);
    }
  };

  const hapus = async (p) => {
    const ok = await confirm({
      title: `Hapus portfolio "${p.title}"?`,
      message: 'Album ini akan hilang dari profil Anda di aplikasi.',
      danger: true,
      confirmLabel: 'Ya, Hapus',
    });
    if (!ok) return;
    try {
      await deleteDoc(doc(db, PATHS.portfolios, p.id));
      setPesan({ tipe: 'sukses', teks: 'Portfolio dihapus.' });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message || 'Gagal menghapus portfolio.' });
    }
  };

  return (
    <div>
      {dialog}
      {!embedded && (
        <>
          <h1 className="page-title">Portfolio</h1>
          <p className="text-meta" style={{ marginBottom: 16 }}>
            Album karya yang tampil di profil Anda pada aplikasi.
          </p>
        </>
      )}

      {pesan && (
        <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
          <div className="msg">{pesan.teks}</div>
        </div>
      )}

      {form && (
        <form className="card mb-lg" onSubmit={simpan}>
          <div className="section-title">{form.id ? 'Ubah Album' : 'Album Baru'}</div>
          <div className="mb-md" style={{ display: 'flex', gap: 12 }}>
            <div style={{ flex: 2 }}>
              <label className="field-label">Judul</label>
              <input className="input" value={form.title} placeholder="Dinda & Reza — Intimate Wedding"
                onChange={(e) => setForm({ ...form, title: e.target.value })} />
            </div>
            <div style={{ flex: 1 }}>
              <label className="field-label">Kategori</label>
              <input className="input" value={form.category} placeholder="Wedding"
                onChange={(e) => setForm({ ...form, category: e.target.value })} />
            </div>
          </div>
          <div className="mb-md">
            <ImagePicker
              label="Foto Album"
              multiple
              max={12}
              folder={`portfolios/${uid}`}
              value={form.media}
              onChange={(media) => setForm({ ...form, media })}
              hint="Pilih dari galeri atau ambil langsung dengan kamera. Foto pertama menjadi sampul album."
            />
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-primary" type="submit" disabled={menyimpan}>
              {menyimpan ? 'Menyimpan...' : 'Simpan Album'}
            </button>
            <button className="btn btn-outline" type="button" onClick={() => setForm(null)}>Batal</button>
          </div>
        </form>
      )}

      {!form && (
        <div style={{ marginBottom: 16 }}>
          <button className="btn btn-primary" onClick={bukaBaru}>+ Tambah Album</button>
        </div>
      )}

      {loading ? (
        <div className="loading">Memuat data...</div>
      ) : error ? (
        <ErrorState error={error} onRetry={() => window.location.reload()} />
      ) : data.length === 0 ? (
        <EmptyState title="Belum ada portfolio" />
      ) : (
        <div className="grid grid-3">
          {data.map((p) => (
            <div className="card" key={p.id}>
              {p.media?.[0] && (
                <img src={p.media[0]} alt="" onError={(e) => { e.target.style.display = 'none'; }}
                  style={{ width: '100%', height: 150, objectFit: 'cover', borderRadius: 'var(--radius-md)', marginBottom: 10 }} />
              )}
              <div style={{ fontWeight: 700, marginBottom: 2 }}>{p.title}</div>
              <div className="text-meta" style={{ marginBottom: 10 }}>
                {p.category || 'Tanpa kategori'} · {(p.media || []).length} media
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

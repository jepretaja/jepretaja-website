import { useState } from 'react';
import { addDoc, collection, deleteDoc, doc, updateDoc, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { useAuth } from '../../auth/AuthContext';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import { useConfirm } from '../../components/ConfirmDialog';
import { formatCurrency } from '../../utils/format';

const KOSONG = {
  name: '', price: '', duration: '', personnel: '', output: '', description: '', active: true,
};

/**
 * Paket jasa milik creator ini — bisa ditambah, diubah, dan dihapus langsung
 * dari web. Aturan Firestore mengizinkan creator menulis paket miliknya
 * sendiri (`resource.data.creatorId == uid()`), jadi tidak perlu lewat server.
 *
 * Sebelumnya halaman ini hanya bisa dilihat dengan catatan "menambah dan
 * mengubah paket dilakukan lewat aplikasi Android" — padahal paket adalah
 * hal paling sering diubah creator, dan izinnya sudah tersedia sejak awal.
 */
export default function CreatorPackages() {
  const { user } = useAuth();
  const uid = user?.uid;
  const { data, loading, error } = useCollection(
    collection(db, PATHS.packages),
    uid ? [where('creatorId', '==', uid)] : []
  );

  const [form, setForm] = useState(null); // null = tertutup
  const [menyimpan, setMenyimpan] = useState(false);
  const [pesan, setPesan] = useState(null);
  const { confirm, dialog } = useConfirm();

  const bukaBaru = () => { setPesan(null); setForm({ ...KOSONG }); };
  const bukaUbah = (p) => {
    setPesan(null);
    setForm({
      id: p.id,
      name: p.name || '', price: p.price ?? '', duration: p.duration || '',
      personnel: p.personnel || '', output: p.output || '',
      description: p.description || '', active: p.active !== false,
    });
  };

  const simpan = async (e) => {
    e.preventDefault();
    if (!form.name.trim()) { setPesan({ tipe: 'gagal', teks: 'Nama paket wajib diisi.' }); return; }
    if (!form.price || Number(form.price) <= 0) { setPesan({ tipe: 'gagal', teks: 'Harga wajib diisi dan lebih dari 0.' }); return; }
    setMenyimpan(true);
    setPesan(null);
    const isi = {
      creatorId: uid,
      name: form.name.trim(),
      price: Number(form.price),
      duration: form.duration.trim() || null,
      personnel: form.personnel.trim() || null,
      output: form.output.trim() || null,
      description: form.description.trim() || null,
      active: form.active,
    };
    try {
      if (form.id) await updateDoc(doc(db, PATHS.packages, form.id), isi);
      else await addDoc(collection(db, PATHS.packages), isi);
      setForm(null);
      setPesan({ tipe: 'sukses', teks: form.id ? 'Paket diperbarui.' : 'Paket baru ditambahkan.' });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message || 'Gagal menyimpan paket.' });
    } finally {
      setMenyimpan(false);
    }
  };

  const hapus = async (p) => {
    const ok = await confirm({
      title: `Hapus paket "${p.name}"?`,
      message: 'Paket tidak akan bisa dipesan lagi. Booking lama yang memakai paket ini tidak terpengaruh.',
      danger: true,
      confirmLabel: 'Ya, Hapus',
    });
    if (!ok) return;
    try {
      await deleteDoc(doc(db, PATHS.packages, p.id));
      setPesan({ tipe: 'sukses', teks: 'Paket dihapus.' });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message || 'Gagal menghapus paket.' });
    }
  };

  const toggleAktif = async (p) => {
    try {
      await updateDoc(doc(db, PATHS.packages, p.id), { active: !p.active });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message || 'Gagal mengubah status paket.' });
    }
  };

  return (
    <div>
      {dialog}
      <h1 className="page-title">Paket Jasa</h1>
      <p className="text-meta" style={{ marginBottom: 16 }}>
        Paket yang aktif akan tampil dan bisa dipesan pelanggan lewat aplikasi.
      </p>

      {pesan && (
        <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
          <div className="msg">{pesan.teks}</div>
        </div>
      )}

      {form && (
        <form className="card mb-lg" onSubmit={simpan}>
          <div className="section-title">{form.id ? 'Ubah Paket' : 'Paket Baru'}</div>
          <div className="mb-md" style={{ display: 'flex', gap: 12 }}>
            <div style={{ flex: 2 }}>
              <label className="field-label">Nama Paket</label>
              <input className="input" value={form.name} placeholder="Prewedding Half Day"
                onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </div>
            <div style={{ flex: 1 }}>
              <label className="field-label">Harga (Rp)</label>
              <input className="input" type="number" value={form.price}
                onChange={(e) => setForm({ ...form, price: e.target.value })} />
            </div>
          </div>
          <div className="mb-md" style={{ display: 'flex', gap: 12 }}>
            <div style={{ flex: 1 }}>
              <label className="field-label">Durasi</label>
              <input className="input" value={form.duration} placeholder="4 jam"
                onChange={(e) => setForm({ ...form, duration: e.target.value })} />
            </div>
            <div style={{ flex: 1 }}>
              <label className="field-label">Personel</label>
              <input className="input" value={form.personnel} placeholder="1 fotografer + 1 asisten"
                onChange={(e) => setForm({ ...form, personnel: e.target.value })} />
            </div>
          </div>
          <div className="mb-md">
            <label className="field-label">Hasil yang Didapat</label>
            <input className="input" value={form.output} placeholder="80 foto edit, 1 album mini"
              onChange={(e) => setForm({ ...form, output: e.target.value })} />
          </div>
          <div className="mb-md">
            <label className="field-label">Deskripsi</label>
            <textarea className="input" rows={3} value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>
          <label className="field-label" style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
            <input type="checkbox" checked={form.active}
              onChange={(e) => setForm({ ...form, active: e.target.checked })} />
            Paket aktif (bisa dipesan pelanggan)
          </label>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-primary" type="submit" disabled={menyimpan}>
              {menyimpan ? 'Menyimpan...' : 'Simpan Paket'}
            </button>
            <button className="btn btn-outline" type="button" onClick={() => setForm(null)}>Batal</button>
          </div>
        </form>
      )}

      <div className="table-wrap">
        <div className="table-toolbar">
          <span className="text-meta">{data.length} paket</span>
          {!form && <button className="btn btn-primary btn-sm" onClick={bukaBaru}>+ Tambah Paket</button>}
        </div>
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          columns={[
            { key: 'name', label: 'Nama Paket' },
            { key: 'price', label: 'Harga', render: (r) => formatCurrency(r.price) },
            { key: 'duration', label: 'Durasi', render: (r) => r.duration || '-' },
            { key: 'output', label: 'Hasil', render: (r) => (r.output || '-') },
            { key: 'active', label: 'Status', render: (r) => <StatusBadge status={r.active ? 'active' : 'suspended'} /> },
            {
              key: 'aksi',
              label: 'Aksi',
              render: (r) => (
                <div style={{ display: 'flex', gap: 8 }}>
                  <button className="btn btn-outline btn-sm" onClick={() => bukaUbah(r)}>Ubah</button>
                  <button className="btn btn-outline btn-sm" onClick={() => toggleAktif(r)}>
                    {r.active ? 'Nonaktifkan' : 'Aktifkan'}
                  </button>
                  <button className="btn btn-danger btn-sm" onClick={() => hapus(r)}>Hapus</button>
                </div>
              ),
            },
          ]}
          rows={data}
          emptyTitle="Belum ada paket jasa"
        />
      </div>
    </div>
  );
}

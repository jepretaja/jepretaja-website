import { useState } from 'react';
import { addDoc, collection, serverTimestamp, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { useAuth } from '../../auth/AuthContext';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import ImagePicker from '../../components/ImagePicker';
import { formatDateTime } from '../../utils/format';
import { byNewest } from '../../utils/sort';

const KOSONG = { fullName: '', idType: 'ktp', idNumber: '', documents: [], note: '' };

const JENIS_IDENTITAS = [
  { id: 'ktp', label: 'KTP' },
  { id: 'sim', label: 'SIM' },
  { id: 'paspor', label: 'Paspor' },
];

/**
 * Pengajuan verifikasi akun oleh creator.
 *
 * Sisi admin sudah lama punya halaman untuk menyetujui/menolak pengajuan
 * (pages/creators/CreatorVerification.jsx), tapi tidak pernah ada tempat bagi
 * creator untuk MENGAJUKANNYA dari web — antriannya hanya bisa diisi lewat
 * aplikasi Android. Halaman ini menutup sisi yang hilang itu.
 *
 * Foto dokumen dipilih langsung dari perangkat — dipotret dengan kamera
 * ponsel di tempat, atau diambil dari galeri. Berkasnya diunggah ke folder
 * `verifications/{uid}` yang, berbeda dengan portfolio, TIDAK terbuka untuk
 * publik di storage.rules: ini foto identitas, bukan etalase.
 *
 * Setelah dikirim, dokumen tidak bisa diubah atau dihapus creator —
 * firestore.rules hanya mengizinkan `create` bagi pemiliknya; perubahan status
 * sepenuhnya milik admin. Itu disengaja: berkas identitas yang bisa ditukar
 * setelah ditinjau membuat proses peninjauannya tidak berarti.
 */
export default function CreatorVerification() {
  const { user, creatorProfile } = useAuth();
  const uid = user?.uid;

  const { data, loading, error } = useCollection(
    collection(db, PATHS.creatorVerifications),
    uid ? [where('creatorId', '==', uid)] : []
  );

  const [form, setForm] = useState(KOSONG);
  const [mengirim, setMengirim] = useState(false);
  const [pesan, setPesan] = useState(null);

  const riwayat = byNewest(data, 'submittedAt');
  const sedangDitinjau = riwayat.some((r) => (r.status || 'pending') === 'pending');
  const sudahTerverifikasi = creatorProfile?.verified === true;

  const kirim = async (e) => {
    e.preventDefault();
    setPesan(null);

    if (!form.fullName.trim()) {
      setPesan({ tipe: 'gagal', teks: 'Nama sesuai identitas wajib diisi.' });
      return;
    }
    if (!form.idNumber.trim()) {
      setPesan({ tipe: 'gagal', teks: 'Nomor identitas wajib diisi.' });
      return;
    }
    const documents = form.documents.filter(Boolean);
    if (documents.length === 0) {
      setPesan({ tipe: 'gagal', teks: 'Sertakan minimal satu foto dokumen.' });
      return;
    }

    setMengirim(true);
    try {
      await addDoc(collection(db, PATHS.creatorVerifications), {
        creatorId: uid,
        creatorName: creatorProfile?.displayName || null,
        fullName: form.fullName.trim(),
        idType: form.idType,
        idNumber: form.idNumber.trim(),
        documents,
        note: form.note.trim() || null,
        // Antrian admin menyaring tepat pada nilai ini — pengajuan dengan
        // status lain tidak akan pernah muncul untuk ditinjau.
        status: 'pending',
        submittedAt: serverTimestamp(),
      });
      setForm(KOSONG);
      setPesan({ tipe: 'sukses', teks: 'Pengajuan terkirim. Tim kami akan meninjau dalam 1–3 hari kerja.' });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message || 'Gagal mengirim pengajuan.' });
    } finally {
      setMengirim(false);
    }
  };

  return (
    <div>
      <h1 className="page-title">Verifikasi Akun</h1>
      <p className="text-meta" style={{ marginBottom: 16 }}>
        Akun terverifikasi mendapat lencana di profil publik dan lebih dipercaya calon pelanggan.
      </p>

      {sudahTerverifikasi && (
        <div className="card banner-success">
          <div className="msg">Akun Anda sudah terverifikasi. Tidak perlu mengajukan lagi.</div>
        </div>
      )}

      {pesan && (
        <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
          <div className="msg">{pesan.teks}</div>
        </div>
      )}

      {!sudahTerverifikasi && (
        sedangDitinjau ? (
          <div className="card mb-lg">
            <div className="section-title">Sedang ditinjau</div>
            <p className="text-meta" style={{ margin: 0 }}>
              Pengajuan Anda sudah masuk antrian dan sedang diperiksa admin. Anda akan
              menerima notifikasi begitu hasilnya keluar — tidak perlu mengirim ulang.
            </p>
          </div>
        ) : (
          <form className="card mb-lg" onSubmit={kirim}>
            <div className="section-title">Ajukan Verifikasi</div>
            <div className="form-row mb-md">
              <div style={{ flex: 2 }}>
                <label className="field-label">Nama Sesuai Identitas</label>
                <input className="input" value={form.fullName} placeholder="Nama lengkap tanpa gelar"
                  onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
              </div>
              <div style={{ flex: 1 }}>
                <label className="field-label">Jenis Identitas</label>
                <select className="input" value={form.idType}
                  onChange={(e) => setForm({ ...form, idType: e.target.value })}>
                  {JENIS_IDENTITAS.map((j) => <option key={j.id} value={j.id}>{j.label}</option>)}
                </select>
              </div>
            </div>
            <div className="mb-md">
              <label className="field-label">Nomor Identitas</label>
              <input className="input" value={form.idNumber} placeholder="16 digit untuk KTP"
                onChange={(e) => setForm({ ...form, idNumber: e.target.value })} />
            </div>
            <div className="mb-md">
              <ImagePicker
                label="Foto Dokumen"
                multiple
                max={4}
                folder={`verifications/${uid}`}
                value={form.documents}
                onChange={(documents) => setForm({ ...form, documents })}
                hint="Foto identitas dan satu swafoto sambil memegangnya. Hanya Anda dan tim peninjau yang bisa membukanya."
              />
            </div>
            <div className="mb-md">
              <label className="field-label">Catatan untuk Admin (opsional)</label>
              <textarea className="input" rows={2} value={form.note}
                onChange={(e) => setForm({ ...form, note: e.target.value })} />
            </div>
            <button className="btn btn-primary" type="submit" disabled={mengirim}>
              {mengirim ? 'Mengirim...' : 'Kirim Pengajuan'}
            </button>
          </form>
        )
      )}

      <h2 className="section-title" style={{ marginTop: 28 }}>Riwayat Pengajuan</h2>
      <div className="table-wrap">
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          columns={[
            { key: 'fullName', label: 'Nama', render: (r) => r.fullName || '-' },
            { key: 'idType', label: 'Jenis', render: (r) => (r.idType || '-').toUpperCase() },
            { key: 'documents', label: 'Dokumen', render: (r) => `${(r.documents || []).length} berkas` },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status || 'pending'} /> },
            { key: 'submittedAt', label: 'Diajukan', render: (r) => formatDateTime(r.submittedAt) },
            { key: 'reviewedAt', label: 'Ditinjau', render: (r) => formatDateTime(r.reviewedAt) },
          ]}
          rows={riwayat}
          emptyTitle="Belum pernah mengajukan verifikasi"
        />
      </div>
    </div>
  );
}

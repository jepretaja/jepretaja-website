import { useState } from 'react';
import { EmailAuthProvider, reauthenticateWithCredential, sendPasswordResetEmail, updatePassword } from 'firebase/auth';
import { collection, where } from 'firebase/firestore';
import { auth, db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { useAuth } from '../../auth/AuthContext';
import { usePermission } from '../../hooks/usePermission';
import { ROLE_PERMISSIONS } from '../../auth/permissions';
import DataTable from '../../components/DataTable';
import { formatDateTime } from '../../utils/format';
import { byNewest } from '../../utils/sort';

const PANJANG_MINIMUM = 8;

/**
 * Akun admin yang sedang login: identitas, izin yang dimilikinya, dan
 * penggantian kata sandi.
 *
 * Sebelumnya seorang admin tidak punya satu pun layar tentang dirinya sendiri.
 * Dua akibatnya nyata: mengganti kata sandi hanya mungkin lewat tautan "lupa
 * sandi" di halaman login (harus keluar dulu), dan tidak ada tempat untuk
 * memastikan izin apa yang sebenarnya melekat pada perannya — padahal itu
 * pertanyaan pertama yang muncul setiap kali sebuah tombol tidak berfungsi.
 *
 * Daftar izin di sini dibaca dari ROLE_PERMISSIONS yang sama yang dipakai
 * menyembunyikan menu, jadi yang ditampilkan memang yang berlaku. Perlu
 * diingat halaman ini menjelaskan izin di UI; otorisasi sebenarnya tetap
 * ditegakkan ulang di /api/admin.
 */
export default function MyAccount() {
  const { user, adminProfile } = useAuth();
  const { role } = usePermission();

  const [form, setForm] = useState({ lama: '', baru: '', ulangi: '' });
  const [menyimpan, setMenyimpan] = useState(false);
  const [pesan, setPesan] = useState(null);

  // Riwayat tindakan admin ini sendiri — "apa yang terakhir saya lakukan"
  // adalah pertanyaan yang jauh lebih sering muncul daripada seluruh aktivitas
  // semua admin, yang sudah ada di halaman Audit Logs.
  const { data: logs, loading, error } = useCollection(
    collection(db, PATHS.auditLogs),
    user?.uid ? [where('adminId', '==', user.uid)] : []
  );
  const riwayat = byNewest(logs).slice(0, 25);

  const izin = ROLE_PERMISSIONS[role] || [];

  const gantiSandi = async (e) => {
    e.preventDefault();
    setPesan(null);

    if (form.baru.length < PANJANG_MINIMUM) {
      setPesan({ tipe: 'gagal', teks: `Kata sandi baru minimal ${PANJANG_MINIMUM} karakter.` });
      return;
    }
    if (form.baru !== form.ulangi) {
      setPesan({ tipe: 'gagal', teks: 'Konfirmasi kata sandi tidak sama.' });
      return;
    }
    if (form.baru === form.lama) {
      setPesan({ tipe: 'gagal', teks: 'Kata sandi baru harus berbeda dari yang lama.' });
      return;
    }

    setMenyimpan(true);
    try {
      // Firebase menolak updatePassword kalau sesi login sudah lama
      // ("requires-recent-login"). Autentikasi ulang di sini membuat
      // penggantian kata sandi selalu berhasil tanpa perlu keluar-masuk, dan
      // sekaligus memastikan yang mengganti memang pemilik akun — bukan orang
      // yang kebetulan menemukan komputer yang belum terkunci.
      await reauthenticateWithCredential(
        auth.currentUser,
        EmailAuthProvider.credential(user.email, form.lama)
      );
      await updatePassword(auth.currentUser, form.baru);
      setForm({ lama: '', baru: '', ulangi: '' });
      setPesan({ tipe: 'sukses', teks: 'Kata sandi berhasil diganti.' });
    } catch (err) {
      const teks = {
        'auth/wrong-password': 'Kata sandi lama salah.',
        'auth/invalid-credential': 'Kata sandi lama salah.',
        'auth/too-many-requests': 'Terlalu banyak percobaan. Coba lagi beberapa menit.',
        'auth/weak-password': 'Kata sandi baru terlalu lemah.',
      }[err.code] || err.message || 'Gagal mengganti kata sandi.';
      setPesan({ tipe: 'gagal', teks });
    } finally {
      setMenyimpan(false);
    }
  };

  const kirimTautanReset = async () => {
    setPesan(null);
    try {
      await sendPasswordResetEmail(auth, user.email);
      setPesan({ tipe: 'sukses', teks: `Tautan atur ulang dikirim ke ${user.email}.` });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message || 'Gagal mengirim tautan.' });
    }
  };

  return (
    <div>
      <h1 className="page-title">Akun Saya</h1>

      {pesan && (
        <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
          <div className="msg">{pesan.teks}</div>
        </div>
      )}

      <div className="grid grid-2 mb-lg">
        <div className="card">
          <div className="section-title">Identitas</div>
          <div className="detail-row"><span>Nama</span><span>{adminProfile?.name || adminProfile?.displayName || '-'}</span></div>
          <div className="detail-row"><span>Email</span><span>{user?.email || '-'}</span></div>
          <div className="detail-row"><span>Peran</span><span>{(role || '-').replace(/_/g, ' ')}</span></div>
          <div className="detail-row"><span>UID</span><span className="num">{user?.uid || '-'}</span></div>
          <div className="detail-row"><span>Ditambahkan</span><span>{formatDateTime(adminProfile?.createdAt)}</span></div>

          <div className="section-title" style={{ marginTop: 20 }}>Izin Peran Ini</div>
          {izin.length === 0 ? (
            <p className="text-meta" style={{ margin: 0 }}>
              Peran Anda tidak dikenali di daftar izin. Hubungi super admin.
            </p>
          ) : (
            <div>
              {izin.map((p) => <span className="tag" key={p}>{p.replace(/_/g, ' ')}</span>)}
            </div>
          )}
        </div>

        <form className="card" onSubmit={gantiSandi}>
          <div className="section-title">Ganti Kata Sandi</div>
          <div className="mb-md">
            <label className="field-label">Kata Sandi Sekarang</label>
            <input className="input" type="password" value={form.lama} autoComplete="current-password"
              onChange={(e) => setForm({ ...form, lama: e.target.value })} />
          </div>
          <div className="mb-md">
            <label className="field-label">Kata Sandi Baru</label>
            <input className="input" type="password" value={form.baru} autoComplete="new-password"
              onChange={(e) => setForm({ ...form, baru: e.target.value })} />
            <div className="text-meta" style={{ marginTop: 6 }}>Minimal {PANJANG_MINIMUM} karakter.</div>
          </div>
          <div className="mb-md">
            <label className="field-label">Ulangi Kata Sandi Baru</label>
            <input className="input" type="password" value={form.ulangi} autoComplete="new-password"
              onChange={(e) => setForm({ ...form, ulangi: e.target.value })} />
          </div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button className="btn btn-primary" type="submit" disabled={menyimpan}>
              {menyimpan ? 'Menyimpan...' : 'Ganti Kata Sandi'}
            </button>
            <button className="btn btn-outline" type="button" onClick={kirimTautanReset}>
              Kirim Tautan Atur Ulang
            </button>
          </div>
        </form>
      </div>

      <h2 className="section-title">Aktivitas Terakhir Saya</h2>
      <div className="table-wrap">
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          columns={[
            { key: 'action', label: 'Aksi', render: (r) => (r.action || '-').replace(/_/g, ' ') },
            { key: 'targetType', label: 'Jenis Target', render: (r) => r.targetType || '-' },
            { key: 'targetId', label: 'Target', render: (r) => (r.targetId || '-').slice(0, 12) },
            { key: 'reason', label: 'Alasan', render: (r) => r.reason || '-' },
            { key: 'createdAt', label: 'Waktu', render: (r) => formatDateTime(r.createdAt) },
          ]}
          rows={riwayat}
          emptyTitle="Belum ada aktivitas tercatat atas nama Anda"
        />
      </div>
    </div>
  );
}

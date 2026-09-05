import { useState } from 'react';
import { collection, doc, serverTimestamp, updateDoc, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { useAuth } from '../../auth/AuthContext';
import EmptyState from '../../components/EmptyState';
import ErrorState from '../../components/ErrorState';
import StatusBadge from '../../components/StatusBadge';
import { formatDateTime } from '../../utils/format';
import { byNewest } from '../../utils/sort';

/**
 * Sebuah notifikasi dianggap BELUM dibaca hanya kalau kedua penandanya kosong.
 *
 * Server menulis `readAt: null` dan tidak pernah menulis `isRead` sama sekali
 * (lihat writeNotification di api/_lib/notify.js) — `readAt` itulah penanda
 * yang sebenarnya, dan yang dipakai badge di APK. Memeriksa `isRead` saja
 * membuat notifikasi yang sudah dibaca di aplikasi tetap terhitung belum
 * dibaca di web, dan angka pada lonceng tidak pernah turun.
 */
export function belumDibaca(n) {
  return !n.readAt && !n.isRead;
}

const SARINGAN = [
  { id: 'semua', label: 'Semua' },
  { id: 'belum', label: 'Belum dibaca' },
  { id: 'booking', label: 'Booking' },
  { id: 'payment', label: 'Pembayaran' },
];

/**
 * Notifikasi milik creator yang sedang login.
 *
 * Notifikasi selama ini HANYA bisa dilihat di aplikasi Android, padahal
 * seluruh pemberitahuan penting — booking baru masuk, penarikan disetujui,
 * sengketa dibuka — ditulis server ke koleksi yang sama dan justru paling
 * sering dikerjakan creator dari komputer.
 *
 * Satu-satunya penulisan yang diizinkan firestore.rules di sini adalah
 * menandai sudah dibaca (`isRead` + `readAt`); membuat dan menghapus
 * notifikasi tetap milik server sepenuhnya.
 */
export default function CreatorNotifications() {
  const { user } = useAuth();
  const uid = user?.uid;
  const [saringan, setSaringan] = useState('semua');
  const [pesan, setPesan] = useState(null);

  const { data, loading, error } = useCollection(
    collection(db, PATHS.notifications),
    uid ? [where('userId', '==', uid)] : []
  );

  const semua = byNewest(data);
  const daftarBelumDibaca = semua.filter(belumDibaca);

  const terlihat = semua.filter((n) => {
    if (saringan === 'semua') return true;
    if (saringan === 'belum') return belumDibaca(n);
    return (n.type || '') === saringan;
  });

  const tandaiDibaca = async (n) => {
    if (!belumDibaca(n)) return;
    try {
      await updateDoc(doc(db, PATHS.notifications, n.id), { isRead: true, readAt: serverTimestamp() });
    } catch (err) {
      setPesan(err.message || 'Gagal menandai notifikasi.');
    }
  };

  const tandaiSemua = async () => {
    setPesan(null);
    // Ditulis satu per satu, bukan lewat batch, supaya satu dokumen yang
    // ditolak tidak ikut menggagalkan sisanya.
    const hasil = await Promise.allSettled(
      daftarBelumDibaca.map((n) =>
        updateDoc(doc(db, PATHS.notifications, n.id), { isRead: true, readAt: serverTimestamp() })
      )
    );
    const gagal = hasil.filter((h) => h.status === 'rejected').length;
    if (gagal) setPesan(`${gagal} notifikasi gagal ditandai. Coba lagi sebentar.`);
  };

  return (
    <div>
      <h1 className="page-title">Notifikasi</h1>
      <p className="text-meta" style={{ marginBottom: 16 }}>
        {daftarBelumDibaca.length > 0
          ? `${daftarBelumDibaca.length} notifikasi belum dibaca.`
          : 'Semua notifikasi sudah dibaca.'}
      </p>

      {pesan && <div className="card banner-danger"><div className="msg">{pesan}</div></div>}

      <div className="flex-between mb-md">
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
        {daftarBelumDibaca.length > 0 && (
          <button className="btn btn-outline btn-sm" onClick={tandaiSemua}>Tandai semua dibaca</button>
        )}
      </div>

      {loading ? (
        <div className="loading">Memuat data...</div>
      ) : error ? (
        <ErrorState error={error} onRetry={() => window.location.reload()} />
      ) : terlihat.length === 0 ? (
        <EmptyState glyph="♪" title="Tidak ada notifikasi" hint="Pemberitahuan booking dan pembayaran akan muncul di sini." />
      ) : (
        <div className="card" style={{ padding: 0 }}>
          <ul className="notice-list" style={{ padding: '0 20px' }}>
            {terlihat.map((n) => (
              <li key={n.id} className="notice-item">
                <div className="notice-head">
                  {belumDibaca(n) && <span className="landing-dot landing-dot-warning" />}
                  {n.type && <StatusBadge status={n.type} />}
                  <span className="text-meta" style={{ marginLeft: 'auto' }}>{formatDateTime(n.createdAt)}</span>
                </div>
                <div className="notice-title">{n.title || 'Notifikasi'}</div>
                <p className="notice-body">{n.body || '-'}</p>
                {belumDibaca(n) && (
                  <button className="btn btn-outline btn-sm" style={{ marginTop: 10 }} onClick={() => tandaiDibaca(n)}>
                    Tandai dibaca
                  </button>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

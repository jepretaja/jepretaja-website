import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { collection, doc, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useDocument } from '../../hooks/useDocument';
import { useCollection } from '../../hooks/useCollection';
import { useAuth } from '../../auth/AuthContext';
import { useConfirm } from '../../components/ConfirmDialog';
import StatusBadge from '../../components/StatusBadge';
import ErrorState from '../../components/ErrorState';
import EmptyState from '../../components/EmptyState';
import { formatCurrency, formatDate, formatDateTime } from '../../utils/format';
import { cancelBooking, confirmBooking, markServiceCompleted, startService } from '../../utils/appActions';

/**
 * Daftar aksi yang boleh dilakukan CREATOR pada tiap status.
 *
 * Sengaja dibuat sebagai peta status -> aksi, bukan sekadar menampilkan semua
 * tombol dan membiarkan server menolak. Alasannya: pindah status booking
 * menyentuh uang (menandai selesai memulai hitungan pelepasan dana escrow),
 * jadi tombol yang pasti ditolak tidak boleh terlihat seperti pilihan yang sah.
 *
 * Isinya harus tetap sejalan dengan BOOKING_TRANSITIONS di
 * api/_lib/actions/appBooking.js — server tetap memvalidasi ulang, halaman ini
 * hanya menghindari tombol yang menyesatkan.
 */
const AKSI_PER_STATUS = {
  paid: ['terima', 'batal'],
  confirmed: ['mulai', 'batal'],
  upcoming: ['mulai', 'batal'],
  in_progress: ['selesai'],
  pending_payment: ['batal'],
};

/** Detail satu booking milik creator, lengkap dengan aksi yang bisa ia lakukan
 *  sendiri. Sebelumnya daftar booking di portal hanya bisa dilihat — memulai
 *  sesi dan menandai pekerjaan selesai hanya mungkin lewat aplikasi Android,
 *  padahal keduanya adalah aksi paling rutin seorang creator. */
export default function CreatorBookingDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const uid = user?.uid;
  const { confirm, dialog } = useConfirm();
  const [sibuk, setSibuk] = useState(null);
  const [pesan, setPesan] = useState(null);

  const { data: booking, loading, error } = useDocument(doc(db, PATHS.bookings, id));

  // Ulasan untuk booking ini (kalau pelanggan sudah menulisnya).
  const { data: ulasan } = useCollection(
    collection(db, PATHS.reviews),
    id ? [where('bookingId', '==', id)] : []
  );

  const jalankan = async (aksi) => {
    const rincian = {
      terima: {
        title: 'Terima booking ini?',
        message: 'Booking akan dikonfirmasi dan pelanggan akan menerima pemberitahuan.',
        fn: confirmBooking,
        danger: false,
      },
      mulai: {
        title: 'Mulai sesi sekarang?',
        message: 'Status booking berubah menjadi "in progress" dan pelanggan akan diberi tahu.',
        fn: startService,
        danger: false,
      },
      selesai: {
        title: 'Tandai pekerjaan selesai?',
        message: 'Pelanggan akan diminta mengonfirmasi hasil. Setelah dikonfirmasi, dana mulai diproses untuk dilepas ke saldo Anda.',
        fn: markServiceCompleted,
        danger: false,
      },
      batal: {
        title: 'Batalkan booking ini?',
        message: 'Pembatalan oleh creator dapat memengaruhi reputasi Anda dan dana pelanggan akan diproses sesuai kebijakan pengembalian.',
        fn: cancelBooking,
        danger: true,
      },
    }[aksi];

    const ok = await confirm({ title: rincian.title, message: rincian.message, danger: rincian.danger });
    if (!ok) return;

    setSibuk(aksi);
    setPesan(null);
    try {
      await rincian.fn({ bookingId: id });
      setPesan({ tipe: 'sukses', teks: 'Status booking berhasil diperbarui.' });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message });
    } finally {
      setSibuk(null);
    }
  };

  if (loading) return <div className="loading">Memuat...</div>;
  if (error) return <ErrorState error={error} onRetry={() => window.location.reload()} />;
  if (!booking) return <EmptyState title="Booking tidak ditemukan" />;

  // Lapisan kedua selain aturan Firestore: dokumen milik orang lain tidak
  // pernah sampai ke sini karena aturannya menolak, tapi kalau suatu saat
  // aturan itu dilonggarkan, halaman ini tetap tidak menampilkan data.
  if (booking.creatorId !== uid) {
    return <EmptyState glyph="🔒" title="Bukan booking Anda" hint="Booking ini milik creator lain." />;
  }

  const aksi = AKSI_PER_STATUS[booking.status] || [];
  const label = { terima: 'Terima Booking', mulai: 'Mulai Sesi', selesai: 'Tandai Selesai', batal: 'Batalkan' };

  return (
    <div>
      {dialog}
      <div className="breadcrumb"><Link to="/creator/bookings">Booking Saya</Link> / #{id.slice(0, 8).toUpperCase()}</div>
      <h1 className="page-title">{booking.packageName || 'Booking'}</h1>

      {pesan && (
        <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
          <div className="msg">{pesan.teks}</div>
        </div>
      )}

      <div className="grid grid-2">
        <div className="card">
          <div className="section-title">Rincian</div>
          <div className="detail-row"><span>Pelanggan</span><span>{booking.customerName || booking.customerId || '-'}</span></div>
          <div className="detail-row"><span>Tanggal Acara</span><span>{formatDate(booking.date)}</span></div>
          <div className="detail-row"><span>Jam</span><span>{booking.time || '-'}</span></div>
          <div className="detail-row"><span>Lokasi</span><span>{booking.location || '-'}</span></div>
          <div className="detail-row"><span>Catatan</span><span>{booking.note || '-'}</span></div>
          <div className="detail-row"><span>Nilai</span><span className="num">{formatCurrency(booking.total)}</span></div>
          <div className="detail-row"><span>Status</span><span><StatusBadge status={booking.status} /></span></div>
          <div className="detail-row"><span>Dibuat</span><span>{formatDateTime(booking.createdAt)}</span></div>
        </div>

        <div>
          <div className="card mb-lg">
            <div className="section-title">Aksi</div>
            {aksi.length === 0 ? (
              <p className="text-meta" style={{ margin: 0 }}>
                Tidak ada aksi yang bisa Anda lakukan pada status “{(booking.status || '-').replace(/_/g, ' ')}”.
                {booking.status === 'completed' && ' Sekarang giliran pelanggan mengonfirmasi hasil.'}
              </p>
            ) : (
              <>
                <p className="text-meta" style={{ marginTop: 0 }}>
                  Perpindahan status divalidasi ulang di server — permintaan yang tidak sesuai alur akan ditolak.
                </p>
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  {aksi.map((a) => (
                    <button
                      key={a}
                      className={`btn btn-sm ${a === 'batal' ? 'btn-danger' : 'btn-primary'}`}
                      disabled={Boolean(sibuk)}
                      onClick={() => jalankan(a)}
                    >
                      {sibuk === a ? 'Memproses...' : label[a]}
                    </button>
                  ))}
                </div>
              </>
            )}
          </div>

          <div className="card">
            <div className="section-title">Ulasan Pelanggan</div>
            {ulasan.length === 0 ? (
              <p className="text-meta" style={{ margin: 0 }}>Belum ada ulasan untuk booking ini.</p>
            ) : (
              ulasan.map((u) => (
                <div key={u.id} style={{ marginBottom: 12 }}>
                  <div className="flex-row mb-sm">
                    <strong>{'★'.repeat(Math.round(Number(u.rating) || 0)) || '-'}</strong>
                    <span className="text-meta">{formatDateTime(u.createdAt)}</span>
                  </div>
                  {/* Moderasi review di sisi admin membaca field `text`;
                      `comment` ikut dicoba untuk data lama. */}
                  <p className="notice-body" style={{ margin: 0 }}>{u.text || u.comment || '-'}</p>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

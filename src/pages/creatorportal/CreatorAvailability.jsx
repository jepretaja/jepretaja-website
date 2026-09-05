import { useMemo, useState } from 'react';
import { collection, where } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { useAuth } from '../../auth/AuthContext';
import ErrorState from '../../components/ErrorState';
import { useConfirm } from '../../components/ConfirmDialog';
import { blockAvailabilityDate, unblockAvailabilityDate } from '../../utils/appActions';

const NAMA_BULAN = [
  'Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni',
  'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember',
];
const NAMA_HARI = ['Sen', 'Sel', 'Rab', 'Kam', 'Jum', 'Sab', 'Min'];

// Booking pada status inilah yang benar-benar "mengunci" sebuah tanggal.
// Daftarnya sama persis dengan yang dipakai blockAvailabilityDate di
// api/_lib/actions/appAvailability.js — kalau berbeda, kalender di sini akan
// menampilkan tanggal sebagai bebas padahal server menolak menutupnya.
const STATUS_MENGUNCI = ['pending_payment', 'paid', 'confirmed', 'upcoming', 'in_progress'];

/** 'YYYY-MM-DD' dari tanggal lokal — sengaja BUKAN toISOString(), karena
 *  toISOString() memakai UTC dan menggeser tanggal satu hari untuk zona
 *  waktu Indonesia (WIB = UTC+7) di jam-jam awal hari. */
function kunciTanggal(d) {
  const bulan = String(d.getMonth() + 1).padStart(2, '0');
  const hari = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${bulan}-${hari}`;
}

/** Indeks kolom kalender: Senin = 0 ... Minggu = 6. */
function kolomHari(d) {
  return (d.getDay() + 6) % 7;
}

/**
 * Kalender ketersediaan creator.
 *
 * Penulisannya WAJIB lewat /api/app: firestore.rules menutup total penulisan
 * ke `availability_blocks` (`allow write: if false`) karena ada satu aturan
 * yang tidak bisa ditegakkan di security rules — tanggal yang sudah punya
 * booking aktif tidak boleh ditutup. Kalau creator bisa menutupnya, pesanan
 * yang sudah dibayar akan lenyap dari kalendernya sendiri.
 *
 * Karena itu tanggal ber-booking di sini ditampilkan terkunci, bukan sekadar
 * dibiarkan gagal saat diklik.
 */
export default function CreatorAvailability() {
  const { user } = useAuth();
  const uid = user?.uid;
  const { confirm, dialog } = useConfirm();

  const [kursor, setKursor] = useState(() => {
    const n = new Date();
    return new Date(n.getFullYear(), n.getMonth(), 1);
  });
  const [sibuk, setSibuk] = useState(null); // dateKey yang sedang diproses
  const [pesan, setPesan] = useState(null);

  const { data: blok, loading, error } = useCollection(
    collection(db, PATHS.availabilityBlocks),
    uid ? [where('creatorId', '==', uid)] : []
  );
  const { data: booking } = useCollection(
    collection(db, PATHS.bookings),
    uid ? [where('creatorId', '==', uid)] : []
  );

  // Hanya dokumen dengan blocked: true yang dihitung tertutup — unblock
  // menyisakan dokumen dengan blocked: false, bukan menghapusnya.
  const tanggalTertutup = useMemo(() => {
    const set = new Map();
    blok.forEach((b) => { if (b.blocked) set.set(b.date, b.reason || ''); });
    return set;
  }, [blok]);

  const tanggalBooking = useMemo(() => {
    const set = new Map();
    booking.forEach((b) => {
      if (!STATUS_MENGUNCI.includes(b.status)) return;
      // `dateKey` ditulis server saat booking dibuat; `date` dipakai sebagai
      // cadangan untuk data lama yang belum punya field itu.
      const kunci = b.dateKey || (b.date ? kunciTanggal(b.date?.toDate ? b.date.toDate() : new Date(b.date)) : null);
      if (kunci) set.set(kunci, b.customerName || b.packageName || 'Booking aktif');
    });
    return set;
  }, [booking]);

  const sel = useMemo(() => {
    const awal = new Date(kursor.getFullYear(), kursor.getMonth(), 1);
    const jumlahHari = new Date(kursor.getFullYear(), kursor.getMonth() + 1, 0).getDate();
    const kosongDepan = kolomHari(awal);
    return [
      ...Array.from({ length: kosongDepan }, () => null),
      ...Array.from({ length: jumlahHari }, (_, i) => new Date(kursor.getFullYear(), kursor.getMonth(), i + 1)),
    ];
  }, [kursor]);

  const hariIni = kunciTanggal(new Date());

  const klik = async (tanggal) => {
    const kunci = kunciTanggal(tanggal);
    if (tanggalBooking.has(kunci)) return;
    if (kunci < hariIni) return;

    const tertutup = tanggalTertutup.has(kunci);
    const ok = await confirm({
      title: tertutup ? `Buka kembali ${kunci}?` : `Tutup tanggal ${kunci}?`,
      message: tertutup
        ? 'Pelanggan bisa memesan lagi pada tanggal ini.'
        : 'Pelanggan tidak akan bisa memesan pada tanggal ini sampai Anda membukanya kembali.',
      confirmLabel: tertutup ? 'Ya, Buka' : 'Ya, Tutup',
    });
    if (!ok) return;

    setSibuk(kunci);
    setPesan(null);
    try {
      if (tertutup) await unblockAvailabilityDate({ date: kunci });
      else await blockAvailabilityDate({ date: kunci, reason: '' });
      setPesan({ tipe: 'sukses', teks: tertutup ? `Tanggal ${kunci} dibuka kembali.` : `Tanggal ${kunci} ditutup.` });
    } catch (err) {
      setPesan({ tipe: 'gagal', teks: err.message });
    } finally {
      setSibuk(null);
    }
  };

  const geserBulan = (delta) =>
    setKursor((k) => new Date(k.getFullYear(), k.getMonth() + delta, 1));

  return (
    <div>
      {dialog}
      <h1 className="page-title">Kalender Ketersediaan</h1>
      <p className="text-meta" style={{ marginBottom: 16 }}>
        Klik satu tanggal untuk menutup atau membukanya. Tanggal yang sudah ada
        booking aktif terkunci dan tidak bisa ditutup.
      </p>

      {pesan && (
        <div className={`card ${pesan.tipe === 'sukses' ? 'banner-success' : 'banner-danger'}`}>
          <div className="msg">{pesan.teks}</div>
        </div>
      )}

      {error ? (
        <ErrorState error={error} onRetry={() => window.location.reload()} />
      ) : (
        <div className="card">
          <div className="flex-between mb-md">
            <button className="btn btn-outline btn-sm" onClick={() => geserBulan(-1)}>‹ Sebelumnya</button>
            <strong>{NAMA_BULAN[kursor.getMonth()]} {kursor.getFullYear()}</strong>
            <button className="btn btn-outline btn-sm" onClick={() => geserBulan(1)}>Berikutnya ›</button>
          </div>

          <div className="kalender-grid">
            {NAMA_HARI.map((h) => (
              <div key={h} className="kalender-hari">{h}</div>
            ))}
            {sel.map((tanggal, i) => {
              if (!tanggal) return <div key={`kosong-${i}`} />;
              const kunci = kunciTanggal(tanggal);
              const dibooking = tanggalBooking.has(kunci);
              const tertutup = tanggalTertutup.has(kunci);
              const lampau = kunci < hariIni;
              const kelas = [
                'kalender-sel',
                dibooking ? 'is-booking' : '',
                tertutup ? 'is-tutup' : '',
                lampau ? 'is-lampau' : '',
                kunci === hariIni ? 'is-hari-ini' : '',
              ].filter(Boolean).join(' ');
              return (
                <button
                  key={kunci}
                  type="button"
                  className={kelas}
                  disabled={dibooking || lampau || sibuk === kunci || loading}
                  title={dibooking ? tanggalBooking.get(kunci) : tertutup ? (tanggalTertutup.get(kunci) || 'Ditutup') : 'Tersedia'}
                  onClick={() => klik(tanggal)}
                >
                  <span className="kalender-angka">{tanggal.getDate()}</span>
                  <span className="kalender-tanda">
                    {sibuk === kunci ? '…' : dibooking ? 'booking' : tertutup ? 'tutup' : ''}
                  </span>
                </button>
              );
            })}
          </div>

          <div className="kalender-legenda">
            <span><i className="kotak kotak-bebas" /> Tersedia</span>
            <span><i className="kotak kotak-tutup" /> Ditutup sendiri</span>
            <span><i className="kotak kotak-booking" /> Ada booking aktif</span>
          </div>
        </div>
      )}
    </div>
  );
}

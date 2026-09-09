import { Link, useNavigate } from 'react-router-dom';
import { collection, where, doc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { useDocument } from '../../hooks/useDocument';
import { useAuth } from '../../auth/AuthContext';
import StatCard from '../../components/StatCard';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import EmptyState from '../../components/EmptyState';
import Icon from '../../components/Icon';
import { formatCurrency, formatDateTime } from '../../utils/format';
import { byNewest } from '../../utils/sort';
import { STATUS_AKTIF, STATUS_SELESAI } from '../../utils/bookingStatus';

const AKSI_CEPAT = [
  { to: '/creator/posts', icon: 'upload', label: 'Unggah Karya' },
  { to: '/creator/packages', icon: 'box', label: 'Tambah Paket' },
  { to: '/creator/availability', icon: 'calendar', label: 'Atur Kalender' },
  { to: '/creator/chats', icon: 'chat', label: 'Buka Pesan' },
];

function salam() {
  const jam = new Date().getHours();
  if (jam < 11) return 'Selamat pagi';
  if (jam < 15) return 'Selamat siang';
  if (jam < 19) return 'Selamat sore';
  return 'Selamat malam';
}

/**
 * Ringkasan milik creator yang sedang login.
 *
 * Setiap query WAJIB memakai where('creatorId','==',uid). Ini bukan sekadar
 * penyaring tampilan — aturan Firestore juga menolak pembacaan di luar
 * miliknya, jadi query tanpa filter ini akan gagal, bukan bocor.
 *
 * SOAL BENTUK HALAMANNYA: versi sebelumnya menampilkan empat kartu angka dan
 * satu tabel kosong. Untuk akun yang baru dibuat — keadaan yang dialami SETIAP
 * creator pada hari pertamanya — hasilnya adalah layar berisi empat angka nol
 * dan satu kotak kosong, yang tidak memberitahu apa pun tentang apa yang harus
 * dikerjakan supaya angka-angka itu berubah.
 *
 * Karena itu ada daftar "Langkah Berikutnya" yang membaca keadaan sesungguhnya
 * (profil, paket, portfolio, verifikasi, rekening) dan hilang dengan sendirinya
 * begitu semuanya beres — panduan yang menetap selamanya akan berubah menjadi
 * hiasan yang diabaikan.
 */
export default function CreatorHome() {
  const { user, creatorProfile } = useAuth();
  const navigate = useNavigate();
  const uid = user?.uid;

  // Urutan terbaru dihitung di klien (lihat utils/sort.js) — where + orderBy
  // di field berbeda menuntut composite index, dan tanpa index itu query
  // gagal total sehingga halaman ini hanya menampilkan pesan error.
  //
  // Batas 10 baris juga dipindah ke sini: sebelumnya limit(10) ikut membatasi
  // data yang dipakai menghitung kartu statistik, sehingga "Booking Aktif"
  // hanya menghitung 10 booking terakhir, bukan seluruhnya.
  const { data: semuaBooking, loading, error } = useCollection(
    collection(db, PATHS.bookings),
    uid ? [where('creatorId', '==', uid)] : []
  );
  const { data: wallet } = useDocument(uid ? doc(db, PATHS.wallets, uid) : null);
  const { data: profilUser } = useDocument(uid ? doc(db, PATHS.users, uid) : null);
  const { data: paket } = useCollection(
    collection(db, PATHS.packages), uid ? [where('creatorId', '==', uid)] : []
  );
  const { data: portfolio } = useCollection(
    collection(db, PATHS.portfolios), uid ? [where('creatorId', '==', uid)] : []
  );

  const bookingTerbaru = byNewest(semuaBooking).slice(0, 8);

  // Daftar status memakai kosakata yang sama dengan APK (utils/bookingStatus.js).
  // Sebelumnya daftarnya ditulis ulang di sini dan sempat menyebut 'pending' —
  // status yang tidak pernah ditulis aplikasi — sehingga kartu "Booking Aktif"
  // diam-diam melewatkan booking yang belum dibayar maupun yang sudah dibayar
  // tapi belum dikerjakan.
  const aktif = semuaBooking.filter((b) => STATUS_AKTIF.includes(b.status)).length;
  const selesai = semuaBooking.filter((b) => STATUS_SELESAI.includes(b.status)).length;

  const nama = creatorProfile?.displayName || 'Creator';
  const rating = Number(creatorProfile?.rating) || 0;

  const langkah = [
    {
      id: 'profil',
      label: 'Lengkapi profil',
      hint: 'Foto, bio, dan kota — tiga hal pertama yang dilihat calon pelanggan.',
      to: '/creator/profile',
      selesai: Boolean(creatorProfile?.photoUrl && creatorProfile?.bio && creatorProfile?.city),
      icon: 'user',
    },
    {
      id: 'paket',
      label: 'Buat paket jasa',
      hint: 'Tanpa paket aktif, jasa Anda belum bisa dipesan siapa pun.',
      to: '/creator/packages',
      selesai: paket.some((p) => p.active !== false),
      icon: 'box',
    },
    {
      id: 'portfolio',
      label: 'Unggah portfolio',
      hint: 'Karya adalah alasan orang memilih Anda, bukan daftar harga.',
      to: '/creator/portfolio',
      selesai: portfolio.length > 0,
      icon: 'image',
    },
    {
      id: 'rekening',
      label: 'Isi rekening penarikan',
      hint: 'Diperlukan sebelum saldo bisa dicairkan.',
      to: '/creator/wallet',
      selesai: Boolean(profilUser?.payoutAccount?.bankAccountNumber),
      icon: 'wallet',
    },
  ];
  const jumlahSelesai = langkah.filter((l) => l.selesai).length;
  const semuaBeres = jumlahSelesai === langkah.length;

  return (
    <div>
      {/* Kepala halaman: identitas dulu, angka belakangan. Sapaan bernama
          membuat portal ini terasa milik orangnya, bukan formulir kosong. */}
      <div className="hero">
        <div className="hero-avatar">
          {creatorProfile?.photoUrl ? (
            <img src={creatorProfile.photoUrl} alt="" onError={(e) => { e.target.style.display = 'none'; }} />
          ) : (
            <span>{nama[0].toUpperCase()}</span>
          )}
        </div>
        <div className="hero-teks">
          <div className="hero-salam">{salam()},</div>
          <h1 className="hero-nama">{nama}</h1>
          <div className="hero-cip">
            <span className="cip cip-sukses"><Icon name="verified" size={14} /> Creator aktif</span>
            {creatorProfile?.city && <span className="cip"><Icon name="target" size={14} /> {creatorProfile.city}</span>}
            <span className="cip"><Icon name="star" size={14} /> {rating.toFixed(1)} · {selesai} sesi selesai</span>
          </div>
        </div>
      </div>

      <div className="aksi-cepat mb-lg">
        {AKSI_CEPAT.map((a) => (
          <Link className="aksi-kartu" key={a.to} to={a.to}>
            <span className="aksi-ikon"><Icon name={a.icon} size={18} /></span>
            {a.label}
            <Icon name="chevron" size={16} className="aksi-panah" />
          </Link>
        ))}
      </div>

      <div className="grid grid-4 mb-lg">
        <StatCard
          label="Saldo Tersedia"
          value={formatCurrency(wallet?.availableBalance || 0)}
          icon="wallet" tone="sukses"
          delta="Bisa ditarik sekarang"
        />
        <StatCard
          label="Saldo Tertahan"
          value={formatCurrency(wallet?.pendingBalance || 0)}
          icon="lock" tone="peringatan"
          delta="Menunggu sesi selesai"
        />
        <StatCard
          label="Booking Aktif"
          value={aktif}
          icon="booking" tone="info"
          delta={aktif > 0 ? 'Sedang berjalan' : 'Belum ada yang berjalan'}
        />
        <StatCard
          label="Rating"
          value={rating.toFixed(1)}
          icon="star" tone="utama"
          delta={`${creatorProfile?.reviewCount || 0} ulasan`}
        />
      </div>

      {!semuaBeres && (
        <div className="card mb-lg">
          <div className="flex-between mb-md">
            <div>
              <div className="section-title" style={{ marginBottom: 2 }}>Langkah Berikutnya</div>
              <div className="text-meta">
                {jumlahSelesai} dari {langkah.length} selesai — lengkapi supaya profil Anda siap menerima pesanan.
              </div>
            </div>
            <div className="cincin-progres num">{Math.round((jumlahSelesai / langkah.length) * 100)}%</div>
          </div>

          <div className="bilah-progres mb-md">
            <span style={{ width: `${(jumlahSelesai / langkah.length) * 100}%` }} />
          </div>

          <div className="langkah-daftar">
            {langkah.map((l) => (
              <Link className={`langkah${l.selesai ? ' beres' : ''}`} key={l.id} to={l.to}>
                <span className="langkah-tanda">
                  <Icon name={l.selesai ? 'check' : l.icon} size={16} />
                </span>
                <span className="langkah-teks">
                  <strong>{l.label}</strong>
                  <span className="text-meta">{l.hint}</span>
                </span>
                {!l.selesai && <span className="langkah-aksi">Kerjakan</span>}
              </Link>
            ))}
          </div>
        </div>
      )}

      <div className="flex-between mb-md" style={{ marginTop: 28 }}>
        <h2 className="section-title" style={{ marginBottom: 0 }}>Booking Terbaru</h2>
        {semuaBooking.length > 0 && (
          <Link className="btn btn-outline btn-sm" to="/creator/bookings">Lihat semua</Link>
        )}
      </div>
      <div className="table-wrap">
        {!loading && !error && bookingTerbaru.length === 0 ? (
          <EmptyState
            icon="booking"
            title="Belum ada booking masuk"
            hint="Pesanan pertama biasanya datang setelah paket dan portfolio terisi — pelanggan perlu tahu apa yang Anda tawarkan."
            actionLabel="Lengkapi etalase"
            actionTo="/creator/packages"
          />
        ) : (
          <DataTable
            loading={loading}
            error={error}
            onRetry={() => window.location.reload()}
            columns={[
              { key: 'customerName', label: 'Pelanggan', render: (r) => r.customerName || r.customerId || '-' },
              { key: 'packageName', label: 'Paket', render: (r) => r.packageName || '-' },
              { key: 'date', label: 'Tanggal Acara', render: (r) => formatDateTime(r.date) },
              { key: 'total', label: 'Nilai', render: (r) => formatCurrency(r.total) },
              { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            ]}
            rows={bookingTerbaru}
            onRowClick={(r) => navigate(`/creator/bookings/${r.id}`)}
            emptyTitle="Belum ada booking masuk"
          />
        )}
      </div>
    </div>
  );
}

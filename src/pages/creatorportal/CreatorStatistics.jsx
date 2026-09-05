import { collection, where } from 'firebase/firestore';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { useAuth } from '../../auth/AuthContext';
import StatCard from '../../components/StatCard';
import EmptyState from '../../components/EmptyState';
import ErrorState from '../../components/ErrorState';
import { formatCurrency, compactNumber } from '../../utils/format';
import { toMillis } from '../../utils/sort';
import { STATUS_AKTIF, STATUS_SELESAI, STATUS_BERBAYAR, hitungGmv } from '../../utils/bookingStatus';

const BULAN_SINGKAT = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des'];
const JUMLAH_BULAN = 12;

/**
 * Statistik performa milik creator yang sedang login.
 *
 * Ini versi berlingkup-sendiri dari halaman Analytics admin: bentuk grafik dan
 * kosakata statusnya sengaja dipinjam dari sana (utils/bookingStatus.js) supaya
 * satu angka tidak pernah berbeda antara yang dilihat creator dan yang dilihat
 * admin. Perbedaannya hanya satu — setiap query di sini disaring
 * `creatorId == uid`, dan aturan Firestore menolak apa pun di luar itu.
 *
 * Grafiknya berwarna tunggal dengan alasan yang sama seperti di Analytics:
 * yang dibandingkan adalah besaran antar kategori berlabel, dan label sumbu
 * itulah yang membawa identitas — bukan warna.
 */
export default function CreatorStatistics() {
  const { user, creatorProfile } = useAuth();
  const uid = user?.uid;

  const { data: bookings, loading, error } = useCollection(
    collection(db, PATHS.bookings),
    uid ? [where('creatorId', '==', uid)] : []
  );
  const { data: reviews } = useCollection(
    collection(db, PATHS.reviews),
    uid ? [where('creatorId', '==', uid)] : []
  );

  const gmv = hitungGmv(bookings);
  const selesai = bookings.filter((b) => STATUS_SELESAI.includes(b.status)).length;
  const aktif = bookings.filter((b) => STATUS_AKTIF.includes(b.status)).length;
  const dibatalkan = bookings.filter((b) => b.status === 'cancelled').length;

  // Booking 'draft' belum pernah benar-benar dipesan, jadi tidak adil ikut
  // jadi penyebut tingkat penyelesaian.
  const diproses = bookings.filter((b) => b.status !== 'draft').length;
  const tingkatSelesai = diproses ? ((selesai / diproses) * 100).toFixed(1) : '0.0';

  const berbayar = bookings.filter((b) => STATUS_BERBAYAR.includes(b.status));
  const rataNilai = berbayar.length ? gmv / berbayar.length : 0;

  // --- Pendapatan per bulan (12 bulan terakhir).
  //
  // Ember dibuat lebih dulu untuk SELURUH 12 bulan, termasuk bulan tanpa
  // transaksi. Kalau hanya bulan yang ada datanya yang digambar, jeda sepi
  // menghilang dari sumbu dan grafiknya terbaca seolah pendapatan berjalan
  // mulus tanpa putus.
  const emberBulan = new Map();
  for (let i = JUMLAH_BULAN - 1; i >= 0; i--) {
    const d = new Date();
    d.setDate(1);
    d.setMonth(d.getMonth() - i);
    emberBulan.set(`${d.getFullYear()}-${d.getMonth()}`, {
      label: `${BULAN_SINGKAT[d.getMonth()]} ${String(d.getFullYear()).slice(2)}`,
      nilai: 0,
      jumlah: 0,
    });
  }
  berbayar.forEach((b) => {
    const ms = toMillis(b.createdAt);
    if (!ms) return;
    const d = new Date(ms);
    const ember = emberBulan.get(`${d.getFullYear()}-${d.getMonth()}`);
    if (!ember) return;
    ember.nilai += Number(b.total) || 0;
    ember.jumlah += 1;
  });
  const trenBulanan = [...emberBulan.values()];

  // --- Paket paling laku, diturunkan dari data (bukan daftar tulis tangan).
  const emberPaket = {};
  berbayar.forEach((b) => {
    const nama = b.packageName || '(tanpa nama paket)';
    emberPaket[nama] = (emberPaket[nama] || 0) + 1;
  });
  const paketTeratas = Object.entries(emberPaket)
    .map(([nama, jumlah]) => ({ nama, jumlah }))
    .sort((a, b) => b.jumlah - a.jumlah)
    .slice(0, 6);

  const rataRating = reviews.length
    ? reviews.reduce((t, r) => t + (Number(r.rating) || 0), 0) / reviews.length
    : Number(creatorProfile?.rating) || 0;

  const gaya = {
    grid: { stroke: 'var(--border)', strokeDasharray: '3 3' },
    sumbu: { fontSize: 11, stroke: 'var(--text-secondary)' },
    tooltip: {
      background: 'var(--surface)', border: '1px solid var(--border)',
      borderRadius: 8, fontSize: 12.5, color: 'var(--text)',
    },
  };

  if (loading) return <div className="loading">Memuat data...</div>;
  if (error) return <ErrorState error={error} onRetry={() => window.location.reload()} />;

  return (
    <div>
      <h1 className="page-title">Statistik & Performa</h1>
      <p className="text-meta" style={{ marginBottom: 20 }}>
        Dihitung dari seluruh booking Anda, bukan hanya yang terlihat di halaman daftar.
      </p>

      <div className="grid grid-4 mb-lg">
        <StatCard label="Nilai Transaksi" value={formatCurrency(gmv)} delta="Hanya booking terbayar" icon="money" tone="sukses" />
        <StatCard label="Rata-rata per Booking" value={formatCurrency(rataNilai)} icon="chart" tone="info" />
        <StatCard
          label="Tingkat Penyelesaian"
          value={`${tingkatSelesai}%`}
          delta={`${selesai} dari ${diproses} booking`}
          deltaDirection={Number(tingkatSelesai) >= 50 ? 'up' : 'down'}
          icon="check" tone={Number(tingkatSelesai) >= 50 ? 'sukses' : 'peringatan'}
        />
        <StatCard
          label="Rating Rata-rata"
          value={rataRating.toFixed(1)}
          delta={`${reviews.length} ulasan`}
          icon="star" tone="utama"
        />
      </div>

      <div className="grid grid-4 mb-lg">
        <StatCard label="Booking Berjalan" value={compactNumber(aktif)} />
        <StatCard label="Booking Selesai" value={compactNumber(selesai)} />
        <StatCard label="Dibatalkan" value={compactNumber(dibatalkan)} deltaDirection="down" />
        <StatCard label="Total Booking" value={compactNumber(bookings.length)} />
      </div>

      <div className="card mb-lg">
        <div className="section-title">Pendapatan 12 Bulan Terakhir</div>
        {trenBulanan.every((d) => d.nilai === 0) ? (
          <EmptyState title="Belum ada booking terbayar dalam 12 bulan terakhir" />
        ) : (
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={trenBulanan} margin={{ top: 8, right: 16, left: 8, bottom: 0 }}>
              <CartesianGrid {...gaya.grid} vertical={false} />
              <XAxis dataKey="label" {...gaya.sumbu} tickLine={false} axisLine={false} />
              <YAxis {...gaya.sumbu} tickLine={false} axisLine={false} width={72}
                tickFormatter={(v) => (v >= 1e6 ? `${v / 1e6} jt` : v >= 1e3 ? `${v / 1e3} rb` : v)} />
              <Tooltip contentStyle={gaya.tooltip} formatter={(v) => [formatCurrency(v), 'Pendapatan']}
                cursor={{ fill: 'var(--neutral-tint)' }} />
              <Bar dataKey="nilai" fill="var(--primary)" radius={[4, 4, 0, 0]} maxBarSize={26} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>

      <div className="card">
        <div className="section-title">Paket Paling Sering Dipesan</div>
        {paketTeratas.length === 0 ? (
          <EmptyState title="Belum ada paket yang pernah dipesan" />
        ) : (
          <ResponsiveContainer width="100%" height={Math.max(180, paketTeratas.length * 44)}>
            <BarChart data={paketTeratas} layout="vertical" margin={{ top: 8, right: 16, left: 8, bottom: 0 }}>
              <CartesianGrid {...gaya.grid} horizontal={false} />
              <XAxis type="number" {...gaya.sumbu} tickLine={false} axisLine={false} allowDecimals={false} />
              <YAxis type="category" dataKey="nama" {...gaya.sumbu} tickLine={false} axisLine={false} width={160} />
              <Tooltip contentStyle={gaya.tooltip} formatter={(v) => [`${v} booking`, 'Jumlah']}
                cursor={{ fill: 'var(--neutral-tint)' }} />
              <Bar dataKey="jumlah" fill="var(--primary)" radius={[0, 4, 4, 0]} maxBarSize={22} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  );
}

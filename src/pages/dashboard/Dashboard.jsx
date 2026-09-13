import { collection, limit, orderBy, query, where } from 'firebase/firestore';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, BarChart, Bar } from 'recharts';
import { useNavigate } from 'react-router-dom';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import StatCard from '../../components/StatCard';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';
import { formatCurrency, formatDateTime, compactNumber } from '../../utils/format';
import { STATUS_AKTIF, STATUS_SELESAI, hitungGmv } from '../../utils/bookingStatus';

/**
 * Admin Dashboard Detail (section 19): metrik utama + grafik + booking
 * terbaru. Angka-angka utama dihitung on-the-fly dari data yang di-stream
 * (untuk MVP) — pada skala produksi sebaiknya dipindah ke aggregation
 * terjadwal (Cloud Functions + BigQuery/Firestore rollup).
 */
export default function Dashboard() {
  const navigate = useNavigate();
  const { data: users, error: usersError } = useCollection(collection(db, PATHS.users));
  const { data: creators, error: creatorsError } = useCollection(collection(db, PATHS.creators));
  const { data: bookings, loading: bookingsLoading, error: bookingsError } = useCollection(collection(db, PATHS.bookings), [orderBy('createdAt', 'desc'), limit(200)]);
  const { data: disputes, error: disputesError } = useCollection(collection(db, PATHS.disputes), [where('status', 'in', ['open', 'reviewing'])]);
  const { data: withdrawals, error: withdrawalsError } = useCollection(collection(db, PATHS.withdrawals), [where('status', '==', 'requested')]);
  const { data: reports, error: reportsError } = useCollection(collection(db, PATHS.reports), [where('status', '==', 'open')]);

  // Dashboard menggabungkan 6 sumber data berbeda — bila salah satu gagal,
  // tampilkan satu banner peringatan (bukan memblokir seluruh halaman,
  // karena sumber lain mungkin tetap berhasil dimuat).
  const anyError = usersError || creatorsError || bookingsError || disputesError || withdrawalsError || reportsError;

  const verifiedCreators = creators.filter((c) => c.verified).length;

  // Kosakata status dipakai bersama dari utils/bookingStatus.js supaya Dashboard,
  // Analytics, dan BookingList tidak lagi memakai daftar berbeda-beda.
  const activeBookings = bookings.filter((b) => STATUS_AKTIF.includes(b.status)).length;
  const completedBookings = bookings.filter((b) => STATUS_SELESAI.includes(b.status)).length;

  // GMV hanya menghitung booking yang uangnya benar-benar masuk. Sebelumnya
  // seluruh booking dijumlahkan — termasuk yang dibatalkan dan yang belum
  // dibayar — sehingga angkanya jauh lebih besar dari pendapatan sebenarnya.
  const gmv = hitungGmv(bookings);

  // Grafik booking 14 hari terakhir.
  //
  // Sebelumnya sumbu-X dibangun dari urutan dokumen yang datang (createdAt
  // menurun), sehingga tanggalnya berjalan MUNDUR dari kiri ke kanan; dan hari
  // tanpa booking sama sekali tidak muncul, membuat garisnya rata di angka 1
  // dan terlihat seperti grafik rusak. Sekarang deretan harinya dibuat lebih
  // dulu secara berurutan, baru diisi jumlahnya.
  const HARI_GRAFIK = 14;
  const ember = {};
  for (let i = HARI_GRAFIK - 1; i >= 0; i--) {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    d.setDate(d.getDate() - i);
    ember[d.toDateString()] = { date: d.toLocaleDateString('id-ID', { day: '2-digit', month: 'short' }), count: 0 };
  }
  bookings.forEach((b) => {
    const d = b.createdAt?.toDate ? b.createdAt.toDate() : null;
    if (!d) return;
    d.setHours(0, 0, 0, 0);
    const k = d.toDateString();
    if (ember[k]) ember[k].count += 1;
  });
  const chartData = Object.values(ember);

  const categoryBuckets = {};
  bookings.forEach((b) => {
    const key = b.packageName || 'Lainnya';
    categoryBuckets[key] = (categoryBuckets[key] || 0) + 1;
  });
  const topPackages = Object.entries(categoryBuckets)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 6)
    .map(([name, count]) => ({ name, count }));

  return (
    <div>
      <h1 className="page-title">Dashboard</h1>
      {anyError && (
        <div className="card banner-danger">
          <span className="msg">⚠ Sebagian data dashboard gagal dimuat (kemungkinan masalah koneksi atau izin akses).</span>
          <button className="btn btn-outline btn-sm" onClick={() => window.location.reload()}>Coba Lagi</button>
        </div>
      )}
      <div className="grid grid-4 mb-lg">
        <StatCard label="Total Users" value={compactNumber(users.length)} icon="users" tone="info" onClick={() => navigate('/users')} />
        <StatCard label="Total Creators" value={compactNumber(creators.length)} delta={`${verifiedCreators} verified`} icon="sparkle" tone="utama" onClick={() => navigate('/creators')} />
        <StatCard label="Active Bookings" value={compactNumber(activeBookings)} icon="booking" tone="peringatan" onClick={() => navigate('/bookings')} />
        <StatCard label="Completed Bookings" value={compactNumber(completedBookings)} icon="check" tone="sukses" onClick={() => navigate('/bookings')} />
      </div>
      <div className="grid grid-4 mb-lg">
        <StatCard label="Gross Transaction Value" value={formatCurrency(gmv)} icon="money" tone="sukses" onClick={() => navigate('/payments')} />
        <StatCard
          label="Open Disputes" value={disputes.length}
          deltaDirection={disputes.length ? 'down' : 'up'}
          delta={disputes.length ? 'Perlu ditinjau' : 'Aman'}
          icon="alert" tone={disputes.length ? 'bahaya' : 'netral'} onClick={() => navigate('/disputes')}
        />
        <StatCard label="Pending Withdrawals" value={withdrawals.length} icon="download" tone={withdrawals.length ? 'peringatan' : 'netral'} onClick={() => navigate('/withdrawals')} />
        <StatCard label="Reported Content" value={reports.length} icon="flag" tone={reports.length ? 'peringatan' : 'netral'} onClick={() => navigate('/reports')} />
      </div>

      <div className="grid grid-2 mb-lg">
        <div className="card">
          <div className="section-title">Booking Harian</div>
          <ResponsiveContainer width="100%" height={220}>
            <LineChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
              <XAxis dataKey="date" fontSize={11} stroke="#9a9a9a" />
              <YAxis fontSize={11} stroke="#9a9a9a" allowDecimals={false} />
              <Tooltip />
              <Line type="monotone" dataKey="count" stroke="var(--primary)" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
        <div className="card">
          <div className="section-title">Paket Terpopuler</div>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={topPackages} layout="vertical" margin={{ left: 20 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
              <XAxis type="number" fontSize={11} allowDecimals={false} />
              <YAxis type="category" dataKey="name" fontSize={11} width={120} />
              <Tooltip />
              {/* Sebelumnya memakai var(--sidebar) yang nyaris hitam pekat —
                  terbaca sebagai grafik mati, bukan data. */}
              <Bar dataKey="count" fill="var(--primary)" radius={[0, 4, 4, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="table-wrap">
        <div className="table-toolbar">
          <div className="section-title mb-0">Booking Terbaru</div>
        </div>
        <DataTable
          loading={bookingsLoading}
          error={bookingsError}
          onRetry={() => window.location.reload()}
          onRowClick={(row) => navigate(`/bookings/${row.id}`)}
          emptyTitle="Belum ada booking"
          columns={[
            { key: 'packageName', label: 'Paket' },
            { key: 'total', label: 'Total', render: (r) => formatCurrency(r.total) },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status} /> },
            { key: 'createdAt', label: 'Dibuat', render: (r) => formatDateTime(r.createdAt) },
          ]}
          rows={bookings.slice(0, 8)}
        />
      </div>
    </div>
  );
}

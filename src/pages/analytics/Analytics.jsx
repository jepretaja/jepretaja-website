import { collection } from 'firebase/firestore';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import StatCard from '../../components/StatCard';
import EmptyState from '../../components/EmptyState';
import { formatCurrency, compactNumber } from '../../utils/format';
import { STATUS_AKTIF, STATUS_SELESAI, hitungGmv } from '../../utils/bookingStatus';

/**
 * Analytics (section 19.1 & 29).
 *
 * Seluruh grafik di halaman ini sengaja BERWARNA TUNGGAL: yang dibandingkan
 * adalah besaran antar kategori berlabel, dan label sumbu itulah yang membawa
 * identitas — bukan warna. Versi sebelumnya memakai donat lima warna yang,
 * selain menyulitkan pembaca buta warna (pasangan hijau/oranye hanya terpisah
 * ΔE 6.2 pada protanopia), juga hanya memuat lima status yang ditulis manual
 * sehingga status yang benar-benar ada di data tidak pernah muncul.
 */
export default function Analytics() {
  const { data: bookings, error: bookingsError } = useCollection(collection(db, PATHS.bookings));
  const { data: creators, error: creatorsError } = useCollection(collection(db, PATHS.creators));
  const { data: disputes } = useCollection(collection(db, PATHS.disputes));
  const { data: payments } = useCollection(collection(db, PATHS.payments));
  const anyError = bookingsError || creatorsError;

  const gmv = hitungGmv(bookings);
  const selesai = bookings.filter((b) => STATUS_SELESAI.includes(b.status)).length;
  const aktif = bookings.filter((b) => STATUS_AKTIF.includes(b.status)).length;

  // Booking berstatus 'draft' belum pernah benar-benar dipesan, jadi tidak adil
  // ikut jadi penyebut tingkat penyelesaian.
  const diproses = bookings.filter((b) => b.status !== 'draft').length;
  const tingkatSelesai = diproses ? ((selesai / diproses) * 100).toFixed(1) : '0.0';

  // Hanya sengketa yang MASIH terbuka yang menandakan masalah berjalan. Versi
  // sebelumnya menjumlahkan seluruh sengketa dan seluruh refund sepanjang masa
  // lalu membaginya dengan jumlah booking — menghasilkan angka 70% yang
  // terbaca seperti krisis, padahal sebagian besar sudah selesai ditangani.
  const sengketaTerbuka = disputes.filter((d) => ['open', 'reviewing'].includes(d.status)).length;
  const tingkatSengketa = diproses ? ((sengketaTerbuka / diproses) * 100).toFixed(1) : '0.0';

  const rataNilai = selesai + aktif > 0 ? gmv / (selesai + aktif) : 0;

  // --- Sebaran status: diturunkan dari data, bukan daftar yang ditulis tangan.
  const emberStatus = {};
  bookings.forEach((b) => {
    const s = b.status || '(tanpa status)';
    emberStatus[s] = (emberStatus[s] || 0) + 1;
  });
  const sebaranStatus = Object.entries(emberStatus)
    .map(([nama, jumlah]) => ({ nama: nama.replace(/_/g, ' '), jumlah }))
    .sort((a, b) => b.jumlah - a.jumlah);

  // --- Tren pendapatan 30 hari, dari pembayaran yang benar-benar lunas.
  //
  // Dibuat sebagai BATANG, bukan garis. Pendapatan harian adalah ember diskret:
  // garis menyiratkan kesinambungan antar titik, sehingga hari tanpa transaksi
  // terbaca sebagai "turun ke nol" dan grafiknya tampak patah — padahal yang
  // terjadi hanyalah tidak ada pembayaran hari itu.
  const HARI = 30;
  const emberHari = {};
  for (let i = HARI - 1; i >= 0; i--) {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    d.setDate(d.getDate() - i);
    emberHari[d.toDateString()] = {
      tanggal: d.toLocaleDateString('id-ID', { day: '2-digit', month: 'short' }),
      nilai: 0,
    };
  }
  payments.filter((p) => p.status === 'paid').forEach((p) => {
    const d = p.createdAt?.toDate ? p.createdAt.toDate() : null;
    if (!d) return;
    d.setHours(0, 0, 0, 0);
    const k = d.toDateString();
    if (emberHari[k]) emberHari[k].nilai += Number(p.amount) || 0;
  });
  const trenPendapatan = Object.values(emberHari);

  // --- Creator dengan nilai transaksi tertinggi.
  const emberCreator = {};
  bookings.forEach((b) => {
    if (!STATUS_SELESAI.includes(b.status) && !STATUS_AKTIF.includes(b.status)) return;
    emberCreator[b.creatorId] = (emberCreator[b.creatorId] || 0) + (Number(b.total) || 0);
  });
  const creatorTeratas = Object.entries(emberCreator)
    .map(([id, nilai]) => ({
      nama: creators.find((c) => c.id === id)?.displayName || id,
      nilai,
    }))
    .sort((a, b) => b.nilai - a.nilai)
    .slice(0, 6);

  const gaya = {
    grid: { stroke: 'var(--border)', strokeDasharray: '3 3' },
    sumbu: { fontSize: 11, stroke: 'var(--text-secondary)' },
    // Tooltip mengikuti permukaan kartu agar tetap terbaca di mode gelap.
    tooltip: {
      background: 'var(--surface)', border: '1px solid var(--border)',
      borderRadius: 8, fontSize: 12.5, color: 'var(--text)',
    },
  };

  return (
    <div>
      <h1 className="page-title">Analytics</h1>

      {anyError && (
        <div className="card banner-danger">
          <span className="msg">⚠ Sebagian data gagal dimuat.</span>
          <button className="btn btn-outline btn-sm" onClick={() => window.location.reload()}>Coba Lagi</button>
        </div>
      )}

      <div className="grid grid-4 mb-lg">
        <StatCard label="Nilai Transaksi (GMV)" value={formatCurrency(gmv)} delta="Hanya booking terbayar" />
        <StatCard label="Rata-rata per Booking" value={formatCurrency(rataNilai)} />
        <StatCard
          label="Tingkat Penyelesaian"
          value={`${tingkatSelesai}%`}
          delta={`${selesai} dari ${diproses} booking`}
          deltaDirection={Number(tingkatSelesai) >= 50 ? 'up' : 'down'}
        />
        <StatCard
          label="Sengketa Terbuka"
          value={`${tingkatSengketa}%`}
          delta={`${sengketaTerbuka} kasus perlu ditinjau`}
          deltaDirection={sengketaTerbuka ? 'down' : 'up'}
        />
      </div>

      <div className="grid grid-4 mb-lg">
        <StatCard label="Total Creator" value={compactNumber(creators.length)} delta={`${creators.filter((c) => c.verified).length} terverifikasi`} />
        <StatCard label="Booking Berjalan" value={compactNumber(aktif)} />
        <StatCard label="Booking Selesai" value={compactNumber(selesai)} />
        <StatCard label="Total Booking" value={compactNumber(bookings.length)} />
      </div>

      <div className="card mb-lg">
        <div className="section-title">Pendapatan 30 Hari Terakhir</div>
        {trenPendapatan.every((d) => d.nilai === 0) ? (
          <EmptyState title="Belum ada pembayaran lunas dalam 30 hari terakhir" />
        ) : (
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={trenPendapatan} margin={{ top: 8, right: 16, left: 8, bottom: 0 }}>
              <CartesianGrid {...gaya.grid} vertical={false} />
              {/* Label tanggal ditipiskan jadi tiap 3 hari; 30 label berjejer
                  di lebar kartu ini pasti saling bertabrakan. */}
              <XAxis dataKey="tanggal" {...gaya.sumbu} tickLine={false} axisLine={false} interval={2} />
              <YAxis {...gaya.sumbu} tickLine={false} axisLine={false} width={72}
                tickFormatter={(v) => (v >= 1e6 ? `${v / 1e6} jt` : v >= 1e3 ? `${v / 1e3} rb` : v)} />
              <Tooltip contentStyle={gaya.tooltip} formatter={(v) => [formatCurrency(v), 'Pendapatan']}
                cursor={{ fill: 'var(--neutral-tint)' }} />
              <Bar dataKey="nilai" fill="var(--primary)" radius={[4, 4, 0, 0]} maxBarSize={22} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>

      <div className="grid grid-2">
        <div className="card">
          <div className="section-title">Sebaran Status Booking</div>
          {sebaranStatus.length === 0 ? (
            <EmptyState title="Belum ada booking" />
          ) : (
            <ResponsiveContainer width="100%" height={Math.max(220, sebaranStatus.length * 34)}>
              <BarChart data={sebaranStatus} layout="vertical" margin={{ top: 4, right: 24, left: 8, bottom: 4 }}>
                <CartesianGrid {...gaya.grid} horizontal={false} />
                <XAxis type="number" {...gaya.sumbu} allowDecimals={false} tickLine={false} axisLine={false} />
                <YAxis type="category" dataKey="nama" {...gaya.sumbu} width={124} tickLine={false} axisLine={false} />
                <Tooltip contentStyle={gaya.tooltip} formatter={(v) => [`${v} booking`, 'Jumlah']} cursor={{ fill: 'var(--neutral-tint)' }} />
                <Bar dataKey="jumlah" fill="var(--primary)" radius={[0, 4, 4, 0]} barSize={16} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="card">
          <div className="section-title">Creator dengan Transaksi Tertinggi</div>
          {creatorTeratas.length === 0 ? (
            <EmptyState title="Belum ada transaksi" />
          ) : (
            <ResponsiveContainer width="100%" height={Math.max(220, creatorTeratas.length * 34)}>
              <BarChart data={creatorTeratas} layout="vertical" margin={{ top: 4, right: 24, left: 8, bottom: 4 }}>
                <CartesianGrid {...gaya.grid} horizontal={false} />
                <XAxis type="number" {...gaya.sumbu} tickLine={false} axisLine={false}
                  tickFormatter={(v) => (v >= 1e6 ? `${v / 1e6} jt` : v >= 1e3 ? `${v / 1e3} rb` : v)} />
                <YAxis type="category" dataKey="nama" {...gaya.sumbu} width={124} tickLine={false} axisLine={false} />
                <Tooltip contentStyle={gaya.tooltip} formatter={(v) => [formatCurrency(v), 'Nilai transaksi']} cursor={{ fill: 'var(--neutral-tint)' }} />
                <Bar dataKey="nilai" fill="var(--primary)" radius={[0, 4, 4, 0]} barSize={16} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>
    </div>
  );
}

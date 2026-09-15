import { Link } from 'react-router-dom';

const APK_DOWNLOAD_URL = '/downloads/jepretaja.apk';

const langkahAdmin = [
  ['Masuk ke dashboard', 'Gunakan akun admin yang sudah terdaftar untuk membuka seluruh area operasional.'],
  ['Periksa ringkasan', 'Dashboard menampilkan booking, transaksi, creator, dan antrean yang membutuhkan tindakan.'],
  ['Tinjau creator dan konten', 'Buka Creator Verification untuk profil baru, lalu Explore Content untuk meninjau karya.'],
  ['Kelola transaksi', 'Periksa pembayaran, escrow, refund, withdrawal, dan sengketa sesuai antrean yang tersedia.'],
  ['Catat dan pantau', 'Setiap keputusan penting tersimpan di Audit Logs sehingga aktivitas tim mudah ditelusuri.'],
];

const langkahCreator = [
  ['Buat akun creator', 'Daftar sebagai creator atau ubah akun customer menjadi creator dari menu Pengaturan.'],
  ['Lengkapi profil', 'Isi bio, kota layanan, kategori, portofolio, paket jasa, dan rekening pencairan.'],
  ['Unggah karya', 'Pilih foto atau video dari perangkat. Karya baru masuk ke antrean review sebelum tampil publik.'],
  ['Tunggu persetujuan', 'Setelah admin menyetujui karya, status berubah menjadi tayang dan karya muncul di Explore serta Home.'],
  ['Kelola pekerjaan', 'Pantau booking, kalender, chat, notifikasi, saldo, dan permintaan withdrawal dari Creator Studio.'],
];

function GuideSection({ title, items }) {
  return (
    <section className="guide-section">
      <div className="guide-section-heading">
        <span className="guide-kicker">Alur kerja</span>
        <h2>{title}</h2>
      </div>
      <ol className="guide-steps">
        {items.map(([heading, description], index) => (
          <li key={heading}>
            <span className="guide-step-number">{String(index + 1).padStart(2, '0')}</span>
            <div>
              <h3>{heading}</h3>
              <p>{description}</p>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}

export default function UserGuide() {
  return (
    <div className="guide-page">
      <header className="landing-header">
        <div className="landing-container landing-header-inner">
          <Link to="/" className="landing-brand">
            <span className="landing-mark">JA</span>
            <span>
              <span className="landing-brand-name">JepretAja</span>
              <span className="landing-brand-sub">Panduan Pengguna</span>
            </span>
          </Link>
          <nav className="landing-nav">
            <Link to="/">Beranda</Link>
            <a className="btn btn-download" href={APK_DOWNLOAD_URL} download>Unduh APK</a>
            <Link className="btn btn-primary" to="/login">Masuk</Link>
          </nav>
        </div>
      </header>

      <main>
        <section className="guide-hero">
          <div className="landing-container guide-hero-inner">
            <div>
              <span className="guide-kicker">JepretAja / Panduan</span>
              <h1>Mulai dengan arah yang jelas.</h1>
              <p>Rujukan singkat untuk admin, creator, dan tim operasional JepretAja.</p>
            </div>
            <a className="btn btn-download btn-lg" href={APK_DOWNLOAD_URL} download>Unduh aplikasi Android</a>
          </div>
        </section>

        <div className="landing-container guide-content">
          <GuideSection title="Untuk Admin" items={langkahAdmin} />
          <GuideSection title="Untuk Creator" items={langkahCreator} />

          <section className="guide-note">
            <div>
              <span className="guide-kicker">Catatan penting</span>
              <h2>Konten baru melewati review.</h2>
              <p>Upload creator tidak langsung masuk Explore. Admin perlu membuka detail konten dan memilih Approve (Publish). Setelah itu karya tampil di Home dan Explore.</p>
            </div>
            <Link className="btn btn-primary" to="/login">Buka dashboard admin</Link>
          </section>
        </div>
      </main>

      <footer className="landing-footer">
        <div className="landing-container landing-footer-inner">
          <div className="landing-footer-brand">JepretAja</div>
          <div className="text-meta"><Link to="/">Beranda</Link> · <Link to="/privasi">Kebijakan Privasi</Link> · <Link to="/syarat">Syarat Layanan</Link></div>
        </div>
      </footer>
    </div>
  );
}

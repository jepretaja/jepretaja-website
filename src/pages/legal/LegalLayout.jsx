import { Link } from 'react-router-dom';

/**
 * Kerangka halaman dokumen publik (kebijakan privasi, syarat layanan,
 * penghapusan akun).
 *
 * Sengaja TIDAK memerlukan login dan berada di luar AdminLayout: Google Play
 * mewajibkan URL kebijakan privasi dan URL penghapusan akun bisa dibuka siapa
 * pun tanpa memasang aplikasi atau punya akun. Halaman yang tersembunyi di
 * balik login akan ditolak saat peninjauan.
 */
export default function LegalLayout({ judul, ringkas, diperbarui, children }) {
  return (
    <div className="landing">
      <header className="landing-header">
        <div className="landing-container landing-header-inner">
          <Link to="/" className="landing-brand">
            <span className="landing-mark">JA</span>
            <span>
              <span className="landing-brand-name">JepretAja</span>
              <span className="landing-brand-sub">Dokumen Resmi</span>
            </span>
          </Link>
          <nav className="landing-nav">
            <Link to="/privasi">Kebijakan Privasi</Link>
            <Link to="/syarat">Syarat Layanan</Link>
            <Link to="/hapus-akun">Hapus Akun</Link>
          </nav>
        </div>
      </header>

      <main className="landing-container legal-main">
        <h1 className="legal-title">{judul}</h1>
        {ringkas && <p className="legal-lead">{ringkas}</p>}
        {diperbarui && <p className="text-meta legal-updated">Terakhir diperbarui: {diperbarui}</p>}
        <article className="legal-body">{children}</article>
      </main>

      <footer className="landing-footer">
        <div className="landing-container landing-footer-inner">
          <div>
            <div className="landing-footer-brand">JepretAja</div>
            <div className="text-meta">© {new Date().getFullYear()} JepretAja. Seluruh hak cipta dilindungi.</div>
          </div>
          <div className="landing-footer-meta">
            <div className="text-meta"><Link to="/privasi">Kebijakan Privasi</Link> · <Link to="/syarat">Syarat Layanan</Link></div>
            <div className="text-meta">Kontak: <a href="mailto:support.soxvo@gmail.com">support.soxvo@gmail.com</a></div>
          </div>
        </div>
      </footer>
    </div>
  );
}

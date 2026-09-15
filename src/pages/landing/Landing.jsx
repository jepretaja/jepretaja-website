import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { doc, getDoc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { formatDate } from '../../utils/format';

/**
 * Halaman depan publik — pusat informasi sebelum admin masuk ke area kontrol.
 *
 * Sengaja TIDAK memerlukan login: seluruh isinya dibaca dari koleksi
 * `settings` yang memang boleh dibaca publik menurut firestore.rules. Karena
 * itu halaman ini tetap tampil utuh walau Firestore sedang bermasalah atau
 * pengunjung belum punya sesi — informasi status justru paling dibutuhkan
 * tepat ketika ada gangguan.
 */

// Isi bawaan dipakai bila dokumen settings/announcements belum dibuat. Dengan
// begini halaman depan tetap berisi di database yang masih kosong sekalipun,
// dan admin bisa menimpanya kapan saja lewat dokumen tersebut.
const PENGUMUMAN_BAWAAN = [
  {
    tipe: 'pemeliharaan',
    judul: 'Pemeliharaan server terjadwal',
    isi: 'Pemeliharaan rutin basis data dilakukan setiap Minggu pukul 01.00–03.00 WIB. Panel tetap dapat diakses, namun proses penarikan dana ditunda sementara.',
  },
  {
    tipe: 'rilis',
    judul: 'Verifikasi transfer manual kini tersedia',
    isi: 'Pembayaran melalui transfer bank dapat dicocokkan langsung dari menu Verifikasi Transfer. Cocokkan nominal pada kolom Transfer yang sudah termasuk kode unik.',
  },
  {
    tipe: 'internal',
    judul: 'Pengingat keamanan akun',
    isi: 'Jangan membagikan kredensial admin kepada siapa pun. Setiap tindakan yang Anda lakukan tercatat pada Audit Logs beserta identitas akun Anda.',
  },
];

const KONTAK_BAWAAN = {
  timSupport: 'support.soxvo@gmail.com',
  daruratIT: '+62 812-0000-0000',
  jamOperasional: 'Senin–Jumat, 09.00–18.00 WIB',
};

const LABEL_TIPE = {
  pemeliharaan: { teks: 'Pemeliharaan', kelas: 'warning' },
  rilis: { teks: 'Rilis Baru', kelas: 'success' },
  internal: { teks: 'Internal', kelas: 'info' },
  penting: { teks: 'Penting', kelas: 'danger' },
};

/** Ambang latensi pemeriksaan database, dalam milidetik. */
const BATAS_STABIL = 1500;
const BATAS_LAMBAT = 4000;
const BATAS_TIMEOUT = 8000;
const APK_DOWNLOAD_URL = '/downloads/jepretaja.apk';

export default function Landing() {
  const [pengumuman, setPengumuman] = useState(PENGUMUMAN_BAWAAN);
  const [kontak, setKontak] = useState(KONTAK_BAWAAN);
  const [status, setStatus] = useState({ keadaan: 'memeriksa', latensi: null, waktu: null });

  /**
   * Pemeriksaan kesehatan yang SUNGGUHAN, bukan indikator hias.
   *
   * Widget status yang selalu hijau justru berbahaya: ia berbohong tepat pada
   * saat gangguan terjadi, padahal di situlah admin paling membutuhkannya.
   * Karena itu status database diukur dari percobaan baca sungguhan ke
   * Firestore beserta latensinya, dan kegagalan dilaporkan apa adanya.
   */
  const periksaStatus = useCallback(async () => {
    setStatus((s) => ({ ...s, keadaan: 'memeriksa' }));
    const mulai = performance.now();
    try {
      await Promise.race([
        getDoc(doc(db, PATHS.settings, 'general')),
        new Promise((_, tolak) => setTimeout(() => tolak(new Error('timeout')), BATAS_TIMEOUT)),
      ]);
      const latensi = Math.round(performance.now() - mulai);
      setStatus({
        keadaan: latensi <= BATAS_STABIL ? 'stabil' : latensi <= BATAS_LAMBAT ? 'lambat' : 'lambat',
        latensi,
        waktu: new Date(),
      });
    } catch {
      setStatus({ keadaan: 'gangguan', latensi: null, waktu: new Date() });
    }
  }, []);

  useEffect(() => {
    periksaStatus();
  }, [periksaStatus]);

  // Pengumuman boleh ditimpa lewat settings/announcements. Kegagalan membaca
  // sengaja diabaikan: halaman depan harus tetap tampil walau Firestore mati.
  useEffect(() => {
    getDoc(doc(db, PATHS.settings, 'announcements'))
      .then((snap) => {
        if (!snap.exists()) return;
        const data = snap.data();
        if (Array.isArray(data.items) && data.items.length) setPengumuman(data.items);
        if (data.kontak) setKontak((k) => ({ ...k, ...data.kontak }));
      })
      .catch(() => {});
  }, []);

  const statusDb = {
    memeriksa: { label: 'Memeriksa…', nada: 'neutral' },
    stabil: { label: 'Stabil', nada: 'success' },
    lambat: { label: 'Lambat', nada: 'warning' },
    gangguan: { label: 'Gangguan', nada: 'danger' },
  }[status.keadaan];

  const semuaNormal = status.keadaan === 'stabil';

  return (
    <div className="landing">
      <header className="landing-header">
        <div className="landing-container landing-header-inner">
          <Link to="/" className="landing-brand">
            <span className="landing-mark">JA</span>
            <span>
              <span className="landing-brand-name">JepretAja</span>
              <span className="landing-brand-sub">Portal Manajemen Internal</span>
            </span>
          </Link>
          <nav className="landing-nav">
            <Link to="/panduan">Panduan Penggunaan</Link>
            <a href="#bantuan">Bantuan IT</a>
            <a className="btn btn-download" href={APK_DOWNLOAD_URL} download>Unduh APK</a>
            <Link className="btn btn-primary" to="/login">Masuk ke Dashboard</Link>
          </nav>
        </div>
      </header>

      <section className="landing-hero">
        <div className="landing-container landing-hero-layout">
          <div className="landing-hero-copy">
            <span className={`landing-pill landing-pill-${semuaNormal ? 'success' : 'warning'}`}>
              <span className={`landing-dot landing-dot-${semuaNormal ? 'success' : 'warning'}`} />
              {semuaNormal ? 'Semua sistem beroperasi normal' : 'Periksa status sistem di bawah'}
            </span>
            <h1 className="landing-title">Operasikan bisnis kreatif dengan <em>lebih percaya diri.</em></h1>
            <p className="landing-subtitle">
              Satu ruang kendali untuk mengelola creator, pemesanan, transaksi, dan pengalaman
              pelanggan JepretAja. Dibuat untuk keputusan yang cepat dan terukur.
            </p>
            <div className="landing-cta">
              <Link className="btn btn-primary btn-lg" to="/login">Masuk ke Dashboard <span aria-hidden="true">-&gt;</span></Link>
              <a className="btn btn-download btn-lg" href={APK_DOWNLOAD_URL} download>Unduh aplikasi Android</a>
            </div>
            <div className="landing-hero-note"><span className="landing-note-check">&#10003;</span> Aman, terukur, dan siap bertumbuh bersama tim Anda</div>
          </div>
          <div className="landing-product-preview" aria-label="Pratinjau dashboard JepretAja">
            <div className="preview-glow" />
            <div className="preview-window">
              <div className="preview-toolbar"><span className="preview-brand"><span className="preview-brand-mark">JA</span> JepretAja</span><span className="preview-avatar">AD</span></div>
              <div className="preview-body">
                <div className="preview-greeting"><span>Selamat pagi, Admin</span><strong>Ringkasan performa</strong></div>
                <div className="preview-stats">
                  <div><span>Gross booking value</span><strong>Rp 248,6 jt</strong><small className="preview-up">+18,4%</small></div>
                  <div><span>Booking aktif</span><strong>1.284</strong><small className="preview-up">+12,8%</small></div>
                </div>
                <div className="preview-chart"><div className="preview-chart-head"><span>Performa transaksi</span><b>7 hari terakhir</b></div><div className="preview-bars"><i style={{ height: '38%' }} /><i style={{ height: '52%' }} /><i style={{ height: '46%' }} /><i style={{ height: '68%' }} /><i style={{ height: '60%' }} /><i style={{ height: '82%' }} /><i style={{ height: '94%' }} /></div></div>
                <div className="preview-activity"><span className="preview-pulse" /> Sistem siap digunakan <b>Online</b></div>
              </div>
            </div>
          </div>
        </div>
        <div className="landing-trust landing-container"><span>Dipercaya untuk mengelola operasional</span><strong>CREATOR NETWORK</strong><strong>STUDIO PARTNERS</strong><strong>JEPRETAJA PRO</strong><strong>BUILT FOR SCALE</strong></div>
      </section>

      <main className="landing-container landing-main">
        <div className="landing-grid">
          {/* ---------------- Papan Pengumuman ---------------- */}
          <section className="landing-panel">
            <div className="landing-panel-head">
              <h2 className="landing-panel-title">Papan Pengumuman</h2>
              <span className="text-meta">{pengumuman.length} informasi</span>
            </div>
            <ul className="notice-list">
              {pengumuman.map((n, i) => {
                const label = LABEL_TIPE[n.tipe] || LABEL_TIPE.internal;
                return (
                  <li className="notice-item" key={i}>
                    <div className="notice-head">
                      <span className={`badge badge-${label.kelas}`}>{label.teks}</span>
                      {n.tanggal && <span className="text-meta">{formatDate(n.tanggal)}</span>}
                    </div>
                    <h3 className="notice-title">{n.judul}</h3>
                    <p className="notice-body">{n.isi}</p>
                  </li>
                );
              })}
            </ul>
          </section>

          {/* ---------------- Status Sistem ---------------- */}
          <aside className="landing-side">
            <section className="landing-panel">
              <div className="landing-panel-head">
                <h2 className="landing-panel-title">Status Sistem</h2>
                <button className="btn btn-outline btn-sm" onClick={periksaStatus}>
                  Periksa Ulang
                </button>
              </div>

              <div className="status-row">
                <span className="status-name">
                  <span className="landing-dot landing-dot-success" /> Portal Web
                </span>
                <span className="badge badge-success">Online</span>
              </div>

              <div className="status-row">
                <span className="status-name">
                  <span className={`landing-dot landing-dot-${statusDb.nada}`} /> Database
                </span>
                <span className={`badge badge-${statusDb.nada}`}>{statusDb.label}</span>
              </div>

              {status.latensi !== null && (
                <div className="status-row status-row-sub">
                  <span className="text-meta">Waktu respons database</span>
                  <span className="text-meta num">{status.latensi} ms</span>
                </div>
              )}

              {status.waktu && (
                <p className="status-time">
                  Terakhir diperiksa{' '}
                  {status.waktu.toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit', second: '2-digit' })} WIB
                </p>
              )}

              {status.keadaan === 'gangguan' && (
                <p className="status-warning">
                  Database tidak merespons. Bila ini berlanjut, hubungi tim IT sebelum melakukan
                  tindakan finansial apa pun.
                </p>
              )}
            </section>

            {/* ---------------- Bantuan IT ---------------- */}
            <section className="landing-panel" id="bantuan">
              <div className="landing-panel-head">
                <h2 className="landing-panel-title">Bantuan IT</h2>
              </div>
              <div className="detail-row">
                <span className="k">Tim Support</span>
                <span className="v"><a href={`mailto:${kontak.timSupport}`}>{kontak.timSupport}</a></span>
              </div>
              <div className="detail-row">
                <span className="k">Darurat IT</span>
                <span className="v">{kontak.daruratIT}</span>
              </div>
              <div className="detail-row">
                <span className="k">Jam Operasional</span>
                <span className="v">{kontak.jamOperasional}</span>
              </div>
            </section>
          </aside>
        </div>

      </main>

      <footer className="landing-footer">
        <div className="landing-container landing-footer-inner">
          <div>
            <div className="landing-footer-brand">JepretAja — Portal Manajemen Internal</div>
            <div className="text-meta">
              © {new Date().getFullYear()} JepretAja. Seluruh hak cipta dilindungi.
            </div>
          </div>
          <div className="landing-footer-meta">
            <div className="text-meta">
              <Link to="/privasi">Kebijakan Privasi</Link> · <Link to="/syarat">Syarat Layanan</Link> · <Link to="/hapus-akun">Hapus Akun</Link>
            </div>
            <div className="text-meta">Versi aplikasi <strong>{__APP_VERSION__}</strong></div>
            <div className="text-meta">
              Dukungan teknis: <a href={`mailto:${kontak.timSupport}`}>{kontak.timSupport}</a>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}

import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';

/**
 * Halaman 404.
 *
 * Sebelumnya tidak ada route penampung sama sekali, sehingga URL yang salah
 * ketik — atau tautan lama yang halamannya sudah dipindah — menghasilkan
 * layar PUTIH TOTAL tanpa pesan apa pun. Kegagalan seperti itu paling mudah
 * disalahartikan sebagai "webnya rusak" atau "servernya mati", padahal yang
 * terjadi hanya alamatnya tidak dikenal.
 *
 * Tautan keluarnya menyesuaikan siapa yang sedang login: mengirim seorang
 * creator ke /dashboard hanya akan memantulkannya balik ke portal creator,
 * dan tamu yang belum masuk akan berakhir di halaman login.
 */
export default function NotFound() {
  const { pathname } = useLocation();
  const { user, adminProfile, creatorProfile, loading } = useAuth();

  const tujuan = adminProfile
    ? { to: '/dashboard', label: 'Kembali ke Dashboard' }
    : creatorProfile
      ? { to: '/creator', label: 'Kembali ke Portal Creator' }
      : user
        ? { to: '/login', label: 'Ke Halaman Login' }
        : { to: '/', label: 'Ke Halaman Depan' };

  return (
    <div className="login-shell">
      <div className="login-card" style={{ textAlign: 'center' }}>
        <div className="login-mark">JA</div>
        <h1 className="login-title">Halaman tidak ditemukan</h1>
        <p className="login-subtitle">
          Alamat <code>{pathname}</code> tidak ada di panel ini. Mungkin tautannya salah ketik
          atau halamannya sudah dipindah.
        </p>
        {!loading && (
          <Link className="btn btn-primary btn-block" to={tujuan.to}>{tujuan.label}</Link>
        )}
        <Link className="login-back" to="/">Halaman depan JepretAja</Link>
      </div>
    </div>
  );
}

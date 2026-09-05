import { useState } from 'react';
import { Navigate, Link } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';

/** Login panel web — melayani admin maupun creator. */
export default function Login() {
  const { user, role, login, logout, loading } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  // Pengalihan menunggu sampai peran selesai dibaca, lalu diarahkan ke
  // area yang sesuai. Tidak memakai navigate() manual setelah login
  // karena saat itu peran belum tentu termuat.
  if (!loading && user && role === 'admin') return <Navigate to="/dashboard" replace />;
  if (!loading && user && role === 'creator') return <Navigate to="/creator" replace />;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await login(email, password);
    } catch (err) {
      setError('Email atau password salah.');
    } finally {
      setSubmitting(false);
    }
  };

  // Autentikasi berhasil tapi akun bukan admin maupun creator (mis. akun
  // pelanggan biasa). Ditolak secara eksplisit dan sesinya diakhiri,
  // supaya tidak tertinggal dalam keadaan setengah masuk.
  if (!loading && user && !role) {
    return (
      <div className="login-shell">
        <div className="login-card">
          <div className="login-mark">JA</div>
          <h2 className="login-title">Akun tidak punya akses</h2>
          <p className="login-subtitle">
            Panel ini hanya untuk admin dan creator terdaftar. Jika Anda creator,
            pastikan pendaftaran creator Anda sudah selesai lewat aplikasi.
          </p>
          <button className="btn btn-outline btn-block" onClick={logout}>Keluar</button>
        </div>
      </div>
    );
  }

  return (
    <div className="login-shell">
      <form className="login-card" onSubmit={handleSubmit}>
        <div className="login-mark">JA</div>
        <h2 className="login-title">JepretAja</h2>
        <p className="login-subtitle">Masuk sebagai admin atau creator.</p>
        <div className="field">
          <input className="input" type="email" placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </div>
        <div className="field">
          <input className="input" type="password" placeholder="Password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </div>
        {error && <div className="field-error">{error}</div>}
        <button className="btn btn-primary btn-block" disabled={submitting} type="submit">
          {submitting ? 'Memproses...' : 'Masuk'}
        </button>
        <Link className="login-back" to="/">← Kembali ke halaman depan</Link>
      </form>
    </div>
  );
}

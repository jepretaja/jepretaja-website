import { useEffect, useRef, useState } from 'react';
import { Outlet, Navigate, Link } from 'react-router-dom';
import Sidebar from './Sidebar';
import { useAuth } from '../auth/AuthContext';
import { useAdminQueue } from '../hooks/useAdminQueue';
import HeaderBadge, { formatJumlah } from '../components/HeaderBadge';

export default function AdminLayout() {
  const { user, adminProfile, creatorProfile, loading, logout } = useAuth();
  // Dipanggil sebelum gerbang di bawah: hook tidak boleh dilewati pada render
  // yang menempuh `return` lebih awal.
  const antrian = useAdminQueue();
  const [bukaAntrian, setBukaAntrian] = useState(false);
  const panelRef = useRef(null);

  // Klik di luar panel menutupnya. Tanpa ini panel menggantung terbuka saat
  // admin berpindah perhatian ke isi halaman, menutupi baris tabel di bawahnya.
  useEffect(() => {
    if (!bukaAntrian) return;
    const tutup = (e) => {
      if (panelRef.current && !panelRef.current.contains(e.target)) setBukaAntrian(false);
    };
    const esc = (e) => { if (e.key === 'Escape') setBukaAntrian(false); };
    document.addEventListener('mousedown', tutup);
    document.addEventListener('keydown', esc);
    return () => {
      document.removeEventListener('mousedown', tutup);
      document.removeEventListener('keydown', esc);
    };
  }, [bukaAntrian]);

  if (loading) return <div className="loading">Memuat...</div>;
  if (!user) return <Navigate to="/login" replace />;
  // Creator dialihkan ke portalnya sendiri. Sebelumnya gerbang ini hanya
  // memeriksa "sudah login", bukan "apakah admin" — sehingga akun non-admin
  // mana pun yang berhasil autentikasi bisa membuka kerangka dashboard
  // operasional (berisi data seluruh pengguna & pembayaran).
  if (!adminProfile) {
    return <Navigate to={creatorProfile ? '/creator' : '/login'} replace />;
  }

  return (
    <div className="admin-shell">
      <Sidebar />
      <div className="main">
        <div className="topbar">
          <div />
          <div className="actions">
            {/* Padanan lonceng notifikasi untuk admin. Admin tidak punya kotak
                masuk pribadi, jadi yang dihitung adalah pekerjaan yang menunggu
                ditangani — dan daftarnya dibuka langsung di sini supaya
                terlihat ANTRIAN MANA yang menumpuk, bukan cuma totalnya. */}
            <div className="header-antrian" ref={panelRef}>
              <HeaderBadge
                icon="bell"
                label="Antrian pekerjaan"
                jumlah={antrian.total}
                active={bukaAntrian}
                onClick={() => setBukaAntrian((b) => !b)}
              />
              {bukaAntrian && (
                <div className="header-panel">
                  <div className="header-panel-judul">Menunggu ditangani</div>
                  {antrian.baris.length === 0 ? (
                    <div className="header-panel-kosong">
                      Tidak ada antrian untuk peran Anda.
                    </div>
                  ) : (
                    antrian.baris.map((b) => (
                      <Link
                        key={b.id}
                        to={b.to}
                        className={`header-panel-baris${b.jumlah > 0 ? ' ada' : ''}`}
                        onClick={() => setBukaAntrian(false)}
                      >
                        <span>{b.label}</span>
                        <span className={b.jumlah > 0 ? 'header-panel-angka' : 'text-meta'}>
                          {b.jumlah > 0 ? formatJumlah(b.jumlah) : '0'}
                        </span>
                      </Link>
                    ))
                  )}
                  {antrian.total === 0 && antrian.baris.length > 0 && (
                    <div className="header-panel-kosong">
                      Semua antrian bersih — tidak ada yang menunggu.
                    </div>
                  )}
                </div>
              )}
            </div>

            <span className="text-meta">
              {adminProfile?.role ? adminProfile.role.replace('_', ' ') : 'admin'}
            </span>
            <Link className="admin-avatar" to="/akun" title="Akun Saya">
              {(user.email || 'A')[0].toUpperCase()}
            </Link>
            <button className="btn btn-outline btn-sm" onClick={logout}>Keluar</button>
          </div>
        </div>
        <div className="content">
          <Outlet />
        </div>
      </div>
    </div>
  );
}

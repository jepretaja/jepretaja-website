import { Outlet, Navigate, NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useUnreadCreator } from '../hooks/useUnreadCreator';
import HeaderBadge from '../components/HeaderBadge';
import Icon from '../components/Icon';

/**
 * Menu portal creator, dikelompokkan seperti sidebar admin.
 *
 * Sebelumnya seluruh menu berada dalam satu daftar datar berisi tujuh butir.
 * Setelah pekerjaan harian (booking, kalender, pesan) bertambah, satu daftar
 * panjang membuat hal yang dikerjakan setiap hari berbaur dengan hal yang
 * disentuh sebulan sekali — karena itu dipisah per tujuan.
 */
const GROUPS = [
  {
    label: 'Pekerjaan',
    items: [
      { to: '/creator', label: 'Ringkasan', icon: 'home', end: true },
      { to: '/creator/bookings', label: 'Booking Saya', icon: 'booking' },
      { to: '/creator/availability', label: 'Kalender', icon: 'calendar' },
      { to: '/creator/chats', label: 'Pesan', icon: 'chat' },
      { to: '/creator/notifications', label: 'Notifikasi', icon: 'bell' },
    ],
  },
  {
    label: 'Etalase',
    items: [
      { to: '/creator/packages', label: 'Paket Jasa', icon: 'box' },
      { to: '/creator/posts', label: 'Unggahan Saya', icon: 'upload' },
      { to: '/creator/portfolio', label: 'Portfolio', icon: 'image' },
      { to: '/creator/reviews', label: 'Ulasan', icon: 'star' },
    ],
  },
  {
    label: 'Akun',
    items: [
      { to: '/creator/profile', label: 'Profil Saya', icon: 'user' },
      { to: '/creator/verification', label: 'Verifikasi Akun', icon: 'verified' },
      { to: '/creator/wallet', label: 'Saldo & Penarikan', icon: 'wallet' },
      { to: '/creator/statistics', label: 'Statistik', icon: 'chart' },
    ],
  },
];

/**
 * Kerangka portal creator.
 *
 * Gerbangnya sengaja ketat: hanya lolos kalau dokumen creators/{uid} ada.
 * Admin yang tersesat ke URL ini dialihkan ke dashboard admin, dan akun
 * biasa (customer) ditolak — bukan sekadar disembunyikan menunya.
 */
export default function CreatorLayout() {
  const { user, creatorProfile, adminProfile, loading, logout } = useAuth();
  const { pathname } = useLocation();
  // Dipanggil sebelum gerbang di bawah karena hook tidak boleh dilewati saat
  // render lain menempuh jalur `return` lebih awal. Tanpa uid, hook ini tidak
  // membuka langganan apa pun.
  const belum = useUnreadCreator(user?.uid);

  if (loading) return <div className="loading">Memuat...</div>;
  if (!user) return <Navigate to="/login" replace />;
  if (adminProfile) return <Navigate to="/dashboard" replace />;
  if (!creatorProfile) return <Navigate to="/login" replace />;

  return (
    <div className="admin-shell">
      {/* Memakai kelas yang sama dengan sidebar admin (nav-group / nav-item).
          Sebelumnya di sini dipakai `sidebar-nav` dan `sidebar-link` yang tidak
          pernah ada di index.css, sehingga menunya tampil tanpa gaya apa pun —
          berdempet jadi satu baris panjang. */}
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="mark">JA</div>
          <div>
            <div className="name">JepretAja</div>
            <div className="sub">Portal Creator</div>
          </div>
        </div>
        {GROUPS.map((group) => (
          <div className="nav-group" key={group.label}>
            <div className="nav-group-label">{group.label}</div>
            {group.items.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
              >
                <Icon name={item.icon} size={17} className="icon" />
                {item.label}
              </NavLink>
            ))}
          </div>
        ))}
        <div className="sidebar-footer">
          Masuk sebagai<br />
          <strong>{creatorProfile.displayName || user.email}</strong>
        </div>
      </aside>

      <div className="main">
        <div className="topbar">
          <div />
          <div className="actions">
            {/* Dua pintu masuk paling sering di portal ini. Ditaruh di header,
                bukan hanya di menu samping, supaya pesan dan notifikasi baru
                terlihat dari halaman mana pun creator sedang bekerja. */}
            <HeaderBadge
              to="/creator/chats"
              icon="chat"
              label="Pesan"
              jumlah={belum.chat}
              active={pathname.startsWith('/creator/chats')}
            />
            <HeaderBadge
              to="/creator/notifications"
              icon="bell"
              label="Notifikasi"
              jumlah={belum.notifikasi}
              active={pathname.startsWith('/creator/notifications')}
            />
            <span className="text-meta">creator</span>
            <div className="admin-avatar">
              {(creatorProfile.displayName || user.email || 'C')[0].toUpperCase()}
            </div>
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

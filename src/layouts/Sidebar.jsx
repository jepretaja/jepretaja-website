import { NavLink } from 'react-router-dom';
import { usePermission } from '../hooks/usePermission';
import Icon from '../components/Icon';

/**
 * Menu Utama Admin (section 18.1), difilter per permission role (section 73):
 * Finance Admin tidak melihat Admin Users; Moderator tidak melihat
 * Wallet/Withdrawal/Refund; Support Admin hanya Users/Bookings/Disputes/Reports.
 * Ini HANYA UX (menyembunyikan menu) — otorisasi sesungguhnya tetap di
 * Cloud Functions, jadi menyembunyikan menu bukan satu-satunya proteksi.
 */
const GROUPS = [
  {
    label: 'Overview',
    items: [{ to: '/dashboard', label: 'Dashboard', icon: 'dashboard', end: true, permission: null }],
  },
  {
    label: 'Manajemen Pengguna',
    items: [
      { to: '/users', label: 'Users', icon: 'users', permission: 'view_users' },
      { to: '/creators', label: 'Creators', icon: 'sparkle', permission: 'view_users' },
      { to: '/creator-verification', label: 'Creator Verification', icon: 'verified', permission: 'verify_creator' },
    ],
  },
  {
    label: 'Konten',
    items: [
      { to: '/explore', label: 'Explore Content', icon: 'image', permission: 'moderate_content' },
      { to: '/comments', label: 'Komentar', icon: 'comment', permission: 'moderate_content' },
      { to: '/reviews', label: 'Reviews', icon: 'star', permission: 'moderate_content' },
      { to: '/portfolios', label: 'Portfolio Creator', icon: 'folder', permission: 'moderate_content' },
      { to: '/packages', label: 'Katalog Paket', icon: 'box', permission: 'moderate_content' },
      { to: '/reports', label: 'Reports', icon: 'flag', permission: 'manage_booking' },
      { to: '/chats', label: 'Monitoring Chat', icon: 'chat', permission: 'manage_dispute' },
    ],
  },
  {
    label: 'Transaksi',
    items: [
      { to: '/bookings', label: 'Bookings', icon: 'booking', permission: 'manage_booking' },
      { to: '/tracking', label: 'Perjalanan Live', icon: 'map', permission: 'manage_booking' },
      { to: '/payments', label: 'Payments', icon: 'money', permission: 'manage_payment' },
      { to: '/payments/manual', label: 'Verifikasi Transfer', icon: 'check', permission: 'manage_payment' },
      { to: '/escrow', label: 'Escrow / Held Funds', icon: 'lock', permission: 'manage_escrow' },
      { to: '/wallets', label: 'Wallets', icon: 'wallet', permission: 'manage_escrow' },
      { to: '/withdrawals', label: 'Withdrawals', icon: 'download', permission: 'manage_withdrawal' },
      { to: '/refunds', label: 'Refunds', icon: 'refresh', permission: 'manage_refund' },
      { to: '/disputes', label: 'Disputes', icon: 'alert', permission: 'manage_dispute' },
      // Hanya muncul untuk super_admin — satu-satunya peran yang punya izin ini.
      { to: '/kas-platform', label: 'Kas Platform', icon: 'money', permission: 'manage_platform_payout' },
    ],
  },
  {
    label: 'Growth',
    items: [
      { to: '/categories', label: 'Categories', icon: 'tag', permission: 'manage_promotion' },
      { to: '/promotions', label: 'Promotions', icon: 'target', permission: 'manage_promotion' },
      { to: '/notifications', label: 'Notifications', icon: 'bell', permission: 'manage_promotion' },
      { to: '/analytics', label: 'Analytics', icon: 'chart', permission: 'view_analytics' },
    ],
  },
  {
    label: 'Sistem',
    items: [
      // Tanpa permission: setiap admin berhak atas halaman akunnya sendiri,
      // termasuk role yang paling terbatas.
      { to: '/akun', label: 'Akun Saya', icon: 'user', permission: null },
      { to: '/settings', label: 'Settings', icon: 'settings', permission: 'manage_settings' },
      { to: '/admin-users', label: 'Admin Users', icon: 'key', permission: 'manage_admin' },
      { to: '/audit-logs', label: 'Audit Logs', icon: 'list', permission: 'manage_admin' },
    ],
  },
];

export default function Sidebar() {
  const { role, can } = usePermission();

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <div className="mark">JA</div>
        <div>
          <div className="name">JepretAja</div>
          <div className="sub">Admin Console</div>
        </div>
      </div>
      {GROUPS.map((group) => {
        const visibleItems = group.items.filter((item) => !item.permission || can(item.permission));
        if (visibleItems.length === 0) return null;
        return (
          <div className="nav-group" key={group.label}>
            <div className="nav-group-label">{group.label}</div>
            {visibleItems.map((item) => (
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
        );
      })}
      {role && (
        <div className="sidebar-footer">
          Masuk sebagai <strong>{role.replace('_', ' ')}</strong>
        </div>
      )}
    </aside>
  );
}

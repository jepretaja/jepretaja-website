import { usePermission } from '../hooks/usePermission';
import EmptyState from './EmptyState';

/** Membungkus route sensitif — mencegah akses langsung lewat URL oleh role
 * yang tidak berwenang, sebagai lapisan kedua selain sidebar filtering. */
export default function RequirePermission({ permission, children }) {
  const { can } = usePermission();
  if (!can(permission)) {
    return <EmptyState glyph="🔒" title="Akses ditolak" hint="Role Anda tidak memiliki izin untuk halaman ini." />;
  }
  return children;
}

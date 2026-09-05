import { useAuth } from '../auth/AuthContext';
import { hasPermission } from '../auth/permissions';

/** Hook: can('manage_withdrawal') -> boolean, mengikuti role admin yang login. */
export function usePermission() {
  const { adminProfile } = useAuth();
  const role = adminProfile?.role;
  return { role, can: (permission) => hasPermission(role, permission) };
}

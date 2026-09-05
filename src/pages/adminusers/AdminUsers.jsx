import { useState } from 'react';
import { collection } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import { usePermission } from '../../hooks/usePermission';
import { inviteAdmin } from '../../utils/adminActions';
import DataTable from '../../components/DataTable';
import StatusBadge from '../../components/StatusBadge';

/**
 * Admin Users — invite SEKARANG memanggil Cloud Function `inviteAdmin`
 * (membuat akun Auth + custom claim + dokumen admin_users sekaligus),
 * bukan addDoc langsung yang sebelumnya tidak pernah benar-benar membuat
 * akun login. Hanya super_admin yang bisa mengakses halaman ini.
 */
export default function AdminUsers() {
  const { data, loading, error: loadError } = useCollection(collection(db, PATHS.adminUsers));
  const { can } = usePermission();
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('admin');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [resetLink, setResetLink] = useState(null);

  const invite = async (e) => {
    e.preventDefault();
    if (!email.trim()) return;
    setSubmitting(true);
    setError(null);
    setResetLink(null);
    try {
      const result = await inviteAdmin({ email: email.trim(), role });
      setResetLink(result.resetLink);
      setEmail('');
    } catch (err) {
      setError(err.message || 'Gagal mengundang admin.');
    } finally {
      setSubmitting(false);
    }
  };

  if (!can('manage_admin')) {
    return (
      <div>
        <h1 className="page-title">Admin Users</h1>
        <div className="empty-state">Hanya super_admin yang dapat mengakses halaman ini.</div>
      </div>
    );
  }

  return (
    <div>
      <h1 className="page-title">Admin Users</h1>
      <form onSubmit={invite} style={{ display: 'flex', gap: 10, marginBottom: 12, flexWrap: 'wrap' }}>
        <input className="input" style={{ maxWidth: 260 }} placeholder="Email admin baru" value={email} onChange={(e) => setEmail(e.target.value)} />
        <select className="input" style={{ width: 180 }} value={role} onChange={(e) => setRole(e.target.value)}>
          <option value="admin">Admin</option>
          <option value="finance_admin">Finance Admin</option>
          <option value="moderator">Moderator</option>
          <option value="support_admin">Support Admin</option>
          <option value="super_admin">Super Admin</option>
        </select>
        <button className="btn btn-primary" type="submit" disabled={submitting}>{submitting ? 'Mengundang...' : 'Undang'}</button>
      </form>
      {error && <p style={{ fontSize: 12.5, color: 'var(--danger)', marginBottom: 10 }}>{error}</p>}
      {resetLink && (
        <p style={{ fontSize: 12.5, color: 'var(--success)', marginBottom: 10 }}>
          Akun dibuat. Kirim link set-password ini ke admin baru: <a href={resetLink} target="_blank" rel="noreferrer">{resetLink}</a>
        </p>
      )}
      <div className="table-wrap">
        <DataTable
          loading={loading}
          error={loadError}
          onRetry={() => window.location.reload()}
          columns={[
            { key: 'email', label: 'Email' },
            { key: 'role', label: 'Role', render: (r) => <StatusBadge status={r.role} /> },
            { key: 'status', label: 'Status', render: (r) => <StatusBadge status={r.status || 'active'} /> },
          ]}
          rows={data}
        />
      </div>
    </div>
  );
}

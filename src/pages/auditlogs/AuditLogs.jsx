import { collection, limit, orderBy } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useCollection } from '../../hooks/useCollection';
import DataTable from '../../components/DataTable';
import { formatDateTime } from '../../utils/format';

/** Audit Logs (section 21 & 29): adminId, action, targetId, reason, timestamp. */
export default function AuditLogs() {
  const { data, loading, error } = useCollection(collection(db, PATHS.auditLogs), [orderBy('createdAt', 'desc'), limit(200)]);

  return (
    <div>
      <h1 className="page-title">Audit Logs</h1>
      <div className="table-wrap">
        <DataTable
          loading={loading}
          error={error}
          onRetry={() => window.location.reload()}
          emptyTitle="Belum ada aktivitas tercatat"
          columns={[
            { key: 'adminId', label: 'Admin' },
            { key: 'action', label: 'Aksi' },
            { key: 'targetType', label: 'Target Type' },
            { key: 'targetId', label: 'Target ID', render: (r) => (r.targetId || '').slice(0, 10) },
            { key: 'reason', label: 'Alasan' },
            { key: 'createdAt', label: 'Waktu', render: (r) => formatDateTime(r.createdAt) },
          ]}
          rows={data}
        />
      </div>
    </div>
  );
}

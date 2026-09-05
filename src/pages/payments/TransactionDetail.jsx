import { useParams, Link } from 'react-router-dom';
import { doc } from 'firebase/firestore';
import { db } from '../../firebase/config';
import { PATHS } from '../../firebase/paths';
import { useDocument } from '../../hooks/useDocument';
import StatusBadge from '../../components/StatusBadge';
import ErrorState from '../../components/ErrorState';
import { formatCurrency, formatDateTime } from '../../utils/format';

/** Transaction Detail (section 29). */
export default function TransactionDetail() {
  const { id } = useParams();
  const { data: payment, loading, error } = useDocument(doc(db, PATHS.payments, id));

  if (loading) return <div className="loading">Memuat...</div>;
  if (error) return <ErrorState error={error} onRetry={() => window.location.reload()} />;
  if (!payment) return <div className="empty-state">Transaksi tidak ditemukan</div>;

  return (
    <div>
      <div className="breadcrumb"><Link to="/payments">Payments</Link> / {id.slice(0, 10).toUpperCase()}</div>
      <div className="card" style={{ maxWidth: 520 }}>
        <div className="section-title">Detail Transaksi</div>
        <div className="detail-row"><span className="k">Booking ID</span><span className="v">{payment.bookingId}</span></div>
        <div className="detail-row"><span className="k">Provider</span><span className="v">{payment.provider}</span></div>
        <div className="detail-row"><span className="k">External ID</span><span className="v">{payment.externalId || '-'}</span></div>
        <div className="detail-row"><span className="k">Jumlah</span><span className="v num">{formatCurrency(payment.amount)}</span></div>
        <div className="detail-row"><span className="k">Status</span><span className="v"><StatusBadge status={payment.status} /></span></div>
        <div className="detail-row"><span className="k">Waktu</span><span className="v">{formatDateTime(payment.createdAt)}</span></div>
      </div>
    </div>
  );
}

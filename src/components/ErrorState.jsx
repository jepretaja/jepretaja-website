import { friendlyErrorMessage } from '../utils/errors';

/** Tampilan error konsisten (loading/error/empty — section 49) — dipakai
 * di DataTable dan halaman detail (useDocument) di seluruh Admin Web. */
export default function ErrorState({ error, onRetry }) {
  return (
    <div className="empty-state">
      <div className="glyph">⚠</div>
      <div style={{ fontWeight: 600, color: 'var(--danger)' }}>{friendlyErrorMessage(error)}</div>
      {onRetry && (
        <button className="btn btn-outline btn-sm" style={{ marginTop: 12 }} onClick={onRetry}>
          Coba Lagi
        </button>
      )}
    </div>
  );
}

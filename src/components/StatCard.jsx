import Icon from './Icon';

/**
 * Kartu angka ringkas.
 *
 * `icon` dan `tone` opsional — kartu lama yang memanggil tanpa keduanya tetap
 * tampil seperti sebelumnya. Warna pada lencana ikon dipakai sebagai penanda
 * jenis (uang, peringatan, pencapaian), BUKAN sebagai satu-satunya pembawa
 * arti: labelnya selalu ada di sebelahnya, jadi kartu tetap terbaca penuh
 * tanpa membedakan warna.
 */
export default function StatCard({ label, value, delta, deltaDirection = 'up', icon, tone = 'netral', onClick }) {
  return (
    <div className={`card stat-card${onClick ? ' is-clickable' : ''}`} onClick={onClick} role={onClick ? 'button' : undefined} tabIndex={onClick ? 0 : undefined} onKeyDown={onClick ? (event) => { if (event.key === 'Enter' || event.key === ' ') onClick(); } : undefined}>
      <div className="stat-head">
        <div className="label">{label}</div>
        {icon && (
          <span className={`stat-ikon nada-${tone}`}>
            <Icon name={icon} size={16} />
          </span>
        )}
      </div>
      <div className="value num">{value}</div>
      {delta && <div className={`delta ${deltaDirection}`}>{delta}</div>}
    </div>
  );
}

import { Link } from 'react-router-dom';
import Icon from './Icon';

/** Angka di atas 99 dipendekkan — badge yang melebar mendorong isi header
 *  lain dan angka pastinya pun tidak berguna pada jumlah sebesar itu. */
export function formatJumlah(n) {
  return n > 99 ? '99+' : String(n);
}

/**
 * Tombol ikon di header dengan penanda jumlah.
 *
 * Gelembung merah HANYA muncul saat jumlahnya di atas nol. Badge permanen
 * berisi "0" melatih mata untuk mengabaikannya, sehingga saat benar-benar ada
 * yang masuk pun tidak lagi terlihat — persis kebalikan dari gunanya. Aturan
 * yang sama dipakai BadgedIconButton di APK.
 *
 * Jumlahnya juga ditulis di `aria-label`, bukan hanya digambar: pembaca layar
 * membacakan label tombol, bukan gelembungnya.
 */
export default function HeaderBadge({ to, onClick, icon, label, jumlah = 0, active = false }) {
  const isi = (
    <>
      <Icon name={icon} size={18} />
      {jumlah > 0 && <span className="header-badge-angka">{formatJumlah(jumlah)}</span>}
    </>
  );

  const kelas = `header-badge${active ? ' active' : ''}`;
  const aria = jumlah > 0 ? `${label}, ${jumlah} baru` : label;

  if (to) {
    return (
      <Link className={kelas} to={to} title={aria} aria-label={aria}>
        {isi}
      </Link>
    );
  }
  return (
    <button type="button" className={kelas} onClick={onClick} title={aria} aria-label={aria}>
      {isi}
    </button>
  );
}

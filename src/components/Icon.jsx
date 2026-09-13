/**
 * Satu set ikon garis, digambar sebagai SVG inline.
 *
 * Sebelumnya seluruh panel memakai karakter Unicode sebagai ikon (◧ ▤ ✦ ⛁ ♪ ⚿).
 * Tiga masalahnya nyata, bukan soal selera:
 *
 *  1. Bentuknya berbeda-beda di tiap sistem — sebagian huruf, sebagian
 *     simbol matematika, sebagian emoji berwarna — sehingga satu baris menu
 *     terlihat seperti kumpulan potongan dari lima tempat berbeda.
 *  2. Ukuran dan berat garisnya tidak bisa diatur, jadi ada yang tampak tebal
 *     dan besar, ada yang nyaris hilang di sebelahnya.
 *  3. Sebagian tidak punya arti yang jelas: "⛁" untuk escrow dan "♪" untuk
 *     notifikasi tidak dikenali siapa pun tanpa membaca labelnya.
 *
 * SVG inline dipilih daripada pustaka ikon karena tidak menambah unduhan,
 * mewarisi `currentColor` (jadi otomatis ikut mode gelap), dan ukurannya
 * ditentukan satu tempat.
 *
 * Semua ikon digambar pada kanvas 24x24 dengan gaya garis yang sama.
 */
const JALUR = {
  dashboard: ['M4 4h7v7H4z', 'M13 4h7v7h-7z', 'M4 13h7v7H4z', 'M13 13h7v7h-7z'],
  home: ['M3 10.5 12 3l9 7.5', 'M5.5 9.5V20h13V9.5'],
  booking: ['M6 3h12v18l-3-2-3 2-3-2-3 2Z', 'M9.5 8.5h5', 'M9.5 12.5h5'],
  map: ['M3.5 5.5 9 3l6 3 5.5-2.5v15L15 21l-6-3-5.5 2.5Z', 'M9 3v15', 'M15 6v15'],
  calendar: ['M4 6h16v14H4z', 'M8 3v4', 'M16 3v4', 'M4 10h16'],
  chat: ['M21 12a8 8 0 0 1-11.9 7L4 20.5 5.5 15A8 8 0 1 1 21 12Z'],
  bell: ['M6 9.5a6 6 0 1 1 12 0c0 3.6 1.4 5 1.4 5H4.6s1.4-1.4 1.4-5Z', 'M10 18a2 2 0 0 0 4 0'],
  box: ['M12 3 3 7.5v9L12 21l9-4.5v-9Z', 'M3 7.5 12 12l9-4.5', 'M12 12v9'],
  upload: ['M12 15.5V4', 'm7.5 8.5 4.5-4.5 4.5 4.5', 'M4 16.5V20h16v-3.5'],
  image: ['M4 5h16v14H4z', 'm5 16.5 4-4 3 3 3-3 4 4', 'M15.4 9.4h.01'],
  star: ['m12 4 2.5 5.1 5.5.8-4 3.9.9 5.6-4.9-2.6-4.9 2.6.9-5.6-4-3.9 5.5-.8Z'],
  user: ['M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z', 'M4.5 20.5a7.5 7.5 0 0 1 15 0'],
  users: ['M10 12a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z', 'M3.5 20a6.5 6.5 0 0 1 13 0', 'M16 5.5a3.5 3.5 0 0 1 0 7', 'M17.5 14.5a6.5 6.5 0 0 1 3 5.5'],
  sparkle: ['m12 3 1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9Z', 'M18.5 15.5 19 17l1.5.5-1.5.5-.5 1.5-.5-1.5L16.5 17l1.5-.5Z'],
  verified: ['M12 3.2 14 4.7l2.5-.2.8 2.4 2 1.5-.9 2.4.9 2.4-2 1.5-.8 2.4-2.5-.2L12 20.8l-2-1.5-2.5.2-.8-2.4-2-1.5.9-2.4-.9-2.4 2-1.5.8-2.4 2.5.2Z', 'm9.2 12 2 2 3.6-3.8'],
  wallet: ['M4 7.5h13.5a2.5 2.5 0 0 1 2.5 2.5v7a2 2 0 0 1-2 2H4Z', 'M4 7.5v-1a2 2 0 0 1 2-2h9.5', 'M16.5 13.2h.01'],
  chart: ['M4.5 20V11', 'M10 20V4.5', 'M15.5 20v-6', 'M21 20V8', 'M3 20h18'],
  folder: ['M4 6.5h5l2 2h9v11H4z'],
  comment: ['M4 5h16v11H9l-5 4Z', 'M8 9.5h8', 'M8 12.5h5'],
  flag: ['M6 21V4', 'M6 4.5h11l-2.5 3.5L17 11.5H6Z'],
  money: ['M3 6.5h18v11H3z', 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z', 'M6 9.5h.01', 'M18 14.5h.01'],
  check: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z', 'm8.2 12 2.6 2.6 5-5.2'],
  lock: ['M5.5 11h13v9.5h-13z', 'M9 11V8a3 3 0 0 1 6 0v3'],
  download: ['M12 4v10.5', 'm7.5 11 4.5 4.5 4.5-4.5', 'M4 20h16'],
  refresh: ['M19.5 12a7.5 7.5 0 1 1-2.2-5.3', 'M20 4v5h-5'],
  alert: ['M12 3.5 2.5 20.5h19Z', 'M12 10v4.5', 'M12 17.5h.01'],
  tag: ['M3.5 12.5 12 4h8v8l-8.5 8.5Z', 'M16.2 8h.01'],
  target: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z', 'M12 16.5a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9Z', 'M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z'],
  settings: ['M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4Z', 'M12 2.5v2.2', 'M12 19.3v2.2', 'M4.8 4.8l1.6 1.6', 'M17.6 17.6l1.6 1.6', 'M2.5 12h2.2', 'M19.3 12h2.2', 'M4.8 19.2l1.6-1.6', 'M17.6 6.4l1.6-1.6'],
  key: ['M14.5 4a5.5 5.5 0 1 0 0 11 5.5 5.5 0 0 0 0-11Z', 'm10 13-6 6v2h3v-2h2v-2h2Z', 'M16.2 8.2h.01'],
  list: ['M4 7h16', 'M4 12h16', 'M4 17h10'],
  clock: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z', 'M12 7.5V12l3 2'],
  plus: ['M12 5v14', 'M5 12h14'],
  chevron: ['m9.5 6 6 6-6 6'],
  inbox: ['M4 13.5 6 5h12l2 8.5V19H4Z', 'M4 13.5h4l1 2.5h6l1-2.5h4'],
};

export default function Icon({ name, size = 18, className = '', title }) {
  const jalur = JALUR[name];
  // Nama yang salah ketik tidak boleh membuat menu ambruk — lebih baik satu
  // ikon kosong dengan ukuran yang tepat daripada layar yang gagal render.
  if (!jalur) return <span className={className} style={{ width: size, height: size, display: 'inline-block' }} />;

  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      // Ikon di sini selalu ditemani teks label, jadi bagi pembaca layar ia
      // hiasan — kecuali kalau pemanggilnya sengaja memberi judul.
      aria-hidden={title ? undefined : 'true'}
      role={title ? 'img' : undefined}
      focusable="false"
    >
      {title && <title>{title}</title>}
      {jalur.map((d) => <path key={d} d={d} />)}
    </svg>
  );
}

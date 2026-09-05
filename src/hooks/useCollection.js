import { useEffect, useRef, useState } from 'react';
import { onSnapshot, query, queryEqual } from 'firebase/firestore';

// Kalau stream Firestore macet (diblokir ekstensi/antivirus/proxy di sisi
// klien, misalnya) callback sukses maupun error onSnapshot bisa sama-sama
// tidak pernah terpanggil, dan `loading` macet di `true` selamanya (spinner
// tanpa henti, tanpa tombol "Coba Lagi"). Batas waktu ini memastikan
// pengguna selalu berakhir di salah satu state yang jelas.
const LISTEN_TIMEOUT_MS = 12000;

/**
 * Hook generik untuk stream koleksi Firestore + query constraints
 * (where/orderBy/limit) yang di-pass sebagai array.
 *
 * Contoh: useCollection(collection(db, 'bookings'), [orderBy('createdAt','desc'), limit(50)])
 */
export function useCollection(baseRef, constraints = []) {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Pemanggil membangun ref dan constraint langsung di dalam render, mis.
  // `useCollection(collection(db, PATHS.creators), [orderBy('rating','desc')])`,
  // sehingga objeknya SELALU baru setiap render sekalipun query-nya sama
  // persis. Memakai objek itu apa adanya sebagai dependency useEffect
  // membuat hook berlangganan ulang tiap render: setLoading(true) memicu
  // render berikutnya, render membuat objek baru, objek baru memicu
  // langganan baru — berputar tanpa henti. Akibatnya halaman macet di
  // "Memuat data..." selamanya sementara koneksi Listen ke Firestore terus
  // dibuka lalu dibatalkan. Identitas query karena itu distabilkan di sini:
  // objek hanya diganti kalau isi query-nya memang berubah.
  const nextQuery = baseRef
    ? (constraints.length ? query(baseRef, ...constraints) : baseRef)
    : null;
  const queryRef = useRef(null);
  if (!nextQuery) {
    queryRef.current = null;
  } else if (!queryRef.current || !queryEqual(queryRef.current, nextQuery)) {
    queryRef.current = nextQuery;
  }
  const stableQuery = queryRef.current;

  useEffect(() => {
    if (!stableQuery) return;
    setLoading(true);
    setError(null);

    const timeoutId = setTimeout(() => {
      setError({ code: 'deadline-exceeded' });
      setLoading(false);
    }, LISTEN_TIMEOUT_MS);

    const unsub = onSnapshot(
      stableQuery,
      (snap) => {
        clearTimeout(timeoutId);
        setData(snap.docs.map((d) => ({ id: d.id, ...d.data() })));
        setLoading(false);
      },
      (err) => {
        clearTimeout(timeoutId);
        setError(err);
        setLoading(false);
      }
    );
    return () => {
      clearTimeout(timeoutId);
      unsub();
    };
  }, [stableQuery]);

  return { data, loading, error };
}

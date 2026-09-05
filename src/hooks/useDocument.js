import { useEffect, useRef, useState } from 'react';
import { onSnapshot, refEqual } from 'firebase/firestore';

// Lihat catatan yang sama di useCollection.js: kalau stream Firestore macet
// di sisi klien, callback sukses maupun error onSnapshot bisa sama-sama
// tidak pernah terpanggil. Batas waktu ini mencegah `loading` macet
// selamanya walau tidak ada permission-denied maupun error lain yang eksplisit.
const LISTEN_TIMEOUT_MS = 12000;

/**
 * Sebelumnya hook ini TIDAK melacak error sama sekali — bila Firestore
 * menolak akses (permission-denied) atau koneksi timeout, `loading` akan
 * macet di `true` selamanya (spinner tidak pernah berhenti) karena
 * callback error onSnapshot tidak pernah dipasang. Diperbaiki di sini.
 */
export function useDocument(docRef) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Sama seperti di useCollection: pemanggil membuat `doc(db, ...)` langsung
  // di dalam render, jadi objeknya selalu baru walau menunjuk dokumen yang
  // sama. Tanpa distabilkan, setiap render akan berlangganan ulang dan
  // halaman detail ikut macet di "Memuat data..." tanpa henti.
  const refHolder = useRef(null);
  if (!docRef) {
    refHolder.current = null;
  } else if (!refHolder.current || !refEqual(refHolder.current, docRef)) {
    refHolder.current = docRef;
  }
  const stableRef = refHolder.current;

  useEffect(() => {
    if (!stableRef) return;
    setLoading(true);
    setError(null);

    const timeoutId = setTimeout(() => {
      setError({ code: 'deadline-exceeded' });
      setLoading(false);
    }, LISTEN_TIMEOUT_MS);

    const unsub = onSnapshot(
      stableRef,
      (snap) => {
        clearTimeout(timeoutId);
        setData(snap.exists() ? { id: snap.id, ...snap.data() } : null);
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
  }, [stableRef]);

  return { data, loading, error };
}

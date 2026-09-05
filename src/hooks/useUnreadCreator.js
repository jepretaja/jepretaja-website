import { collection, where } from 'firebase/firestore';
import { db } from '../firebase/config';
import { PATHS } from '../firebase/paths';
import { useCollection } from './useCollection';

/**
 * Jumlah pesan dan notifikasi yang belum dibaca milik creator yang login.
 *
 * Dipakai badge di header portal creator (layouts/CreatorLayout.jsx).
 *
 * Penanda "belum dibaca" adalah `readAt == null`, DITULIS EKSPLISIT oleh
 * server (lihat writeNotification di api/_lib/notify.js). Ini bukan detail
 * sepele: di Firestore, dokumen yang sama sekali tidak punya field `readAt`
 * TIDAK cocok dengan query `where('readAt', '==', null)` — kalau suatu saat
 * ada jalur penulisan yang lupa menyertakan field itu, badge akan diam di nol
 * selamanya walau pesannya masuk, dan tidak ada error apa pun yang muncul.
 *
 * Kedua query memakai composite index yang sudah terdaftar di
 * firestore.indexes.json: messages(participants CONTAINS, readAt) dan
 * notifications(userId, readAt).
 */
export function useUnreadCreator(uid) {
  // Pesan yang belum dibaca, TERMASUK kiriman sendiri — Firestore tidak
  // mengizinkan `!=` digabung dengan array-contains, jadi penyaring pengirim
  // dikerjakan di klien. Alasan yang sama dipakai ChatRepository di APK.
  const { data: pesan } = useCollection(
    uid ? collection(db, PATHS.messages) : null,
    uid ? [where('participants', 'array-contains', uid), where('readAt', '==', null)] : []
  );

  const { data: notifikasi } = useCollection(
    uid ? collection(db, PATHS.notifications) : null,
    uid ? [where('userId', '==', uid), where('readAt', '==', null)] : []
  );

  return {
    chat: pesan.filter((m) => m.senderId !== uid).length,
    // `isRead` ikut diperiksa untuk dokumen lama yang sempat ditandai lewat
    // panel web sebelum `readAt` dipakai sebagai penanda tunggal.
    notifikasi: notifikasi.filter((n) => !n.isRead).length,
  };
}

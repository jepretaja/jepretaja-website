import { getMessaging } from 'firebase-admin/messaging';
// './' bukan '../': berkas ini ada di api/_lib/, satu folder dengan
// firebaseAdmin.js. Jalur lama menunjuk api/firebaseAdmin.js yang tidak
// pernah ada, sehingga SETIAP modul yang memakai notify.js gagal dimuat —
// dan karena notify.js dipakai hampir semua aksi booking, pembayaran, dan
// sengketa, /api/admin maupun /api/app ikut gagal sejak baris import-nya.
import { FieldValue } from './firebaseAdmin.js';

/**
 * Menulis satu notifikasi in-app ke koleksi `notifications`.
 *
 * Dua hal yang wajib diperhatikan:
 *
 * 1. **`readAt: null` ditulis eksplisit.** Badge di aplikasi menghitung
 *    notifikasi belum dibaca dengan query `where('readAt', '==', null)`, dan di
 *    Firestore dokumen yang TIDAK punya field itu sama sekali tidak akan cocok
 *    dengan query tersebut. Tanpa baris ini badge lonceng selamanya 0 meski
 *    notifikasinya masuk.
 * 2. **Bentuk dokumennya mengikuti NotificationModel.kt** di APK
 *    (userId, type, title, body, referenceId, deepLink, createdAt, readAt).
 *    Kalau salah satu nama diubah di sini, layar Notifikasi ikut kosong.
 *
 * Koleksi ini `allow create: if false` untuk klien, jadi hanya Admin SDK di
 * folder api/ yang bisa menulisnya — itu memang disengaja.
 *
 * @param tx transaksi Firestore bila pemanggilnya berada di dalam
 *           runTransaction; null untuk penulisan lepas.
 */
export function writeNotification(db, tx, { userId, type, title, body, referenceId = null, deepLink = null, docId = null }) {
  if (!userId) return null;

  // docId diisi untuk kejadian yang bisa terulang (suka, follow) supaya
  // notifikasinya diperbarui, bukan menumpuk tiap kali tombolnya ditekan lagi.
  const ref = docId
    ? db.collection('notifications').doc(docId)
    : db.collection('notifications').doc();
  const data = {
    notificationId: ref.id,
    userId,
    type,
    title,
    body,
    referenceId,
    deepLink,
    createdAt: FieldValue.serverTimestamp(),
    readAt: null,
  };

  if (tx) {
    tx.set(ref, data);
    // Push dikirim di luar transaksi. Transaksi Firestore bisa DIULANG saat ada
    // tabrakan tulisan, dan apa pun yang punya efek ke luar (mengirim push,
    // memanggil API) akan ikut terulang — pengguna menerima notifikasi yang
    // sama dua kali. Karena itu pengirimannya dijadwalkan setelah blok ini,
    // bukan di dalamnya.
    kirimPush(db, userId, title, body, referenceId, type);
    return ref.id;
  }
  // Sengaja tidak di-await oleh pemanggil: notifikasi adalah efek samping,
  // kegagalannya tidak boleh membatalkan aksi utama yang sudah berhasil.
  ref.set(data)
    .then(() => kirimPush(db, userId, title, body, referenceId, type))
    .catch((err) => console.error('[notify] gagal menulis notifikasi:', err));
  return ref.id;
}

/**
 * Push FCM ke perangkat penerima.
 *
 * Token disimpan di `users/{uid}.fcmToken` oleh JepretAjaMessagingService di
 * aplikasi. Sebelumnya token itu ditulis rajin setiap kali aplikasi dibuka tapi
 * tidak pernah dipakai satu kali pun — notifikasi hanya terlihat kalau
 * pengguna kebetulan sedang membuka aplikasi.
 *
 * Kegagalan tidak pernah dilempar ke pemanggil: notifikasi in-app-nya sudah
 * tersimpan, dan push yang gagal bukan alasan menggagalkan aksi utama.
 *
 * Token yang sudah tidak sah dihapus, supaya perangkat yang sudah lama dicopot
 * tidak terus-menerus dicoba pada setiap notifikasi berikutnya.
 */
async function kirimPush(db, userId, title, body, referenceId, type) {
  try {
    const snap = await db.collection('users').doc(userId).get();
    const token = snap.data()?.fcmToken;
    if (!token) return;

    await getMessaging().send({
      token,
      notification: { title, body },
      data: {
        type: String(type || ''),
        referenceId: String(referenceId || ''),
      },
      android: { priority: 'high', notification: { channelId: 'jepretaja_default' } },
    });
  } catch (err) {
    const kode = err?.errorInfo?.code || err?.code;
    if (kode === 'messaging/registration-token-not-registered' || kode === 'messaging/invalid-argument') {
      await db.collection('users').doc(userId)
        .update({ fcmToken: FieldValue.delete() })
        .catch(() => {});
      return;
    }
    console.error('[notify] push gagal:', kode || err);
  }
}

/** Judul singkat per status booking, dipakai notifikasi perpindahan status. */
export const JUDUL_STATUS = {
  paid: 'Pembayaran diterima',
  confirmed: 'Booking dikonfirmasi',
  upcoming: 'Sesi akan berlangsung',
  in_progress: 'Sesi dimulai',
  completed: 'Sesi ditandai selesai',
  customer_confirmed: 'Hasil dikonfirmasi pelanggan',
  funds_released: 'Dana dilepas',
  reviewed: 'Ulasan masuk',
  cancelled: 'Booking dibatalkan',
  refund_requested: 'Pengajuan refund',
  disputed: 'Sengketa dibuka',
};

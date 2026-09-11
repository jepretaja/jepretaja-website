import { adminAuth, adminDb } from './firebaseAdmin.js';
import { unauthorized, forbidden } from './http.js';

/**
 * Verifikasi ID token Firebase milik pengguna aplikasi (customer/creator).
 *
 * Berbeda dari requireAdmin() di rbac.js yang mensyaratkan keanggotaan di
 * koleksi admin_users. Di sini yang dipakai endpoint aplikasi: cukup akun
 * terautentikasi dan berstatus aktif.
 *
 * Identitas pemanggil SELALU diambil dari token, tidak pernah dari body
 * request. Kalau customerId dibaca dari body, siapa pun bisa membuat atau
 * membatalkan booking atas nama orang lain hanya dengan mengganti satu field.
 */
export async function requireUser(req) {
  const header = req.headers.authorization || req.headers.Authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
  if (!token) throw unauthorized('Token tidak dikirim.');

  let decoded;
  try {
    decoded = await adminAuth().verifyIdToken(token);
  } catch {
    throw unauthorized('Token tidak valid atau sudah kedaluwarsa.');
  }

  const db = adminDb();
  const snap = await db.collection('users').doc(decoded.uid).get();
  const creatorSnap = snap.exists ? null : await db.collection('creators').doc(decoded.uid).get();
  if (!snap.exists && !creatorSnap?.exists) throw forbidden('Akun tidak ditemukan.');
  const user = snap.exists ? snap.data() : { ...creatorSnap.data(), role: 'creator' };
  if (user.status && user.status !== 'active') {
    throw forbidden('Akun Anda sedang tidak aktif.');
  }

  return { uid: decoded.uid, email: decoded.email || user.email || '', profile: user };
}

/** Memastikan pemanggil adalah creator terdaftar (untuk aksi sisi creator). */
export async function requireCreator(req) {
  const actor = await requireUser(req);
  const snap = await adminDb().collection('creators').doc(actor.uid).get();
  if (!snap.exists) throw forbidden('Akun Anda bukan creator terdaftar.');
  return { ...actor, creator: snap.data() };
}

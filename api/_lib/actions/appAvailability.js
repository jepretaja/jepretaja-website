import { adminDb, FieldValue } from '../firebaseAdmin.js';
import { requireCreator } from '../authUser.js';
import { readBody, requireString, badRequest, conflict } from '../http.js';

/**
 * Creator menutup/membuka tanggal tertentu.
 *
 * ID dokumen sengaja deterministik (`{creatorId}_{YYYY-MM-DD}` di koleksi availability_blocks) supaya
 * createBooking bisa memeriksa ketersediaan lewat satu pembacaan langsung
 * di dalam transaksi — query biasa tidak diperbolehkan menjadi dasar
 * penguncian dalam transaksi Firestore.
 */
function kunciTanggal(dateIso) {
  const d = new Date(dateIso);
  if (Number.isNaN(d.getTime())) throw badRequest('Format tanggal tidak valid.');
  return dateIso.slice(0, 10);
}

export async function blockAvailabilityDate(req) {
  const actor = await requireCreator(req);
  const body = readBody(req);
  const tanggal = kunciTanggal(requireString(body.date, 'date'));
  const reason = typeof body.reason === 'string' ? body.reason.trim() : '';

  const db = adminDb();

  // Tanggal yang sudah ada booking aktif tidak boleh ditutup — kalau boleh,
  // creator bisa "menghilangkan" pesanan yang sudah dibayar dari kalendernya.
  const adaBooking = await db.collection('bookings')
    .where('creatorId', '==', actor.uid)
    .where('dateKey', '==', tanggal)
    .where('status', 'in', ['pending_payment', 'paid', 'confirmed', 'upcoming', 'in_progress'])
    .limit(1)
    .get();
  if (!adaBooking.empty) {
    throw conflict('Tanggal ini sudah ada booking aktif, tidak bisa ditutup.');
  }

  await db.collection('availability_blocks').doc(`${actor.uid}_${tanggal}`).set({
    creatorId: actor.uid, date: tanggal, blocked: true, reason,
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });

  return { date: tanggal, blocked: true };
}

export async function unblockAvailabilityDate(req) {
  const actor = await requireCreator(req);
  const body = readBody(req);
  const tanggal = kunciTanggal(requireString(body.date, 'date'));

  await adminDb().collection('availability_blocks').doc(`${actor.uid}_${tanggal}`).set({
    creatorId: actor.uid, date: tanggal, blocked: false, reason: '',
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });

  return { date: tanggal, blocked: false };
}

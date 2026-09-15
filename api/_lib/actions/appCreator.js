import { requireUser } from '../authUser.js';
import { adminDb } from '../firebaseAdmin.js';
import { conflict } from '../http.js';

/** Mengubah akun customer menjadi creator tanpa membuat akun Auth kedua. */
export async function becomeCreator(req) {
  const actor = await requireUser(req);
  if (actor.profile.role === 'creator') return { alreadyCreator: true };
  if (actor.profile.role !== 'customer') throw conflict('Hanya akun customer yang bisa mendaftar sebagai creator.');

  const db = adminDb();
  const userRef = db.collection('users').doc(actor.uid);
  const creatorRef = db.collection('creators').doc(actor.uid);
  await db.runTransaction(async (tx) => {
    const userSnap = await tx.get(userRef);
    const creatorSnap = await tx.get(creatorRef);
    if (!userSnap.exists) throw conflict('Profil pengguna belum siap. Coba lagi sebentar.');
    if (creatorSnap.exists) {
      tx.update(userRef, { role: 'creator' });
      return;
    }
    const user = userSnap.data() || {};
    tx.update(userRef, { role: 'creator' });
    tx.set(creatorRef, {
      userId: actor.uid,
      displayName: user.name || actor.email || 'Creator JepretAja',
      city: user.city || null,
      categories: [],
      rating: 0,
      reviewCount: 0,
      followerCount: 0,
      verified: false,
      verificationStatus: 'unverified',
      status: 'active',
      createdAt: new Date(),
    });
  });
  return { alreadyCreator: false, creatorId: actor.uid };
}
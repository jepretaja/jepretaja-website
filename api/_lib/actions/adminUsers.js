import { adminAuth, adminDb, FieldValue } from '../firebaseAdmin.js';
import { requireAdmin, ROLE_PERMISSIONS } from '../rbac.js';
import { writeAudit } from '../audit.js';
import { readBody, requireString, requireOneOf, badRequest, conflict, notFound } from '../http.js';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Undang admin baru. Tidak pernah membuat password: akun dibuat dengan
 * password acak yang tidak dibagikan ke siapa pun, lalu dibalas dengan link
 * reset password supaya admin baru yang menentukan passwordnya sendiri.
 *
 * Custom claim { admin: true, role } di-set di sini karena hanya server yang
 * boleh menyentuhnya — kalau tidak, siapa pun bisa mengaku super_admin.
 */
export async function inviteAdmin(req) {
  const actor = await requireAdmin(req, 'manage_admin');
  const body = readBody(req);
  const email = requireString(body.email, 'email').toLowerCase();
  const role = requireOneOf(body.role, 'role', Object.keys(ROLE_PERMISSIONS));
  const name = typeof body.name === 'string' ? body.name.trim() : null;

  if (!EMAIL_RE.test(email)) throw badRequest('Format email tidak valid.');
  if (role === 'super_admin' && actor.role !== 'super_admin') {
    throw conflict('Hanya super_admin yang boleh mengundang super_admin lain.');
  }

  const auth = adminAuth();
  const db = adminDb();

  let userRecord;
  try {
    userRecord = await auth.getUserByEmail(email);
  } catch (err) {
    if (err && err.code === 'auth/user-not-found') {
      userRecord = await auth.createUser({
        email,
        emailVerified: false,
        displayName: name || email.split('@')[0],
        // Password acak yang tidak pernah dikirim ke mana pun — satu-satunya
        // cara masuk adalah lewat link reset di bawah.
        password: `Jp!${Math.random().toString(36).slice(2)}${Math.random().toString(36).slice(2)}`,
      });
    } else {
      throw err;
    }
  }

  const adminRef = db.collection('admin_users').doc(userRecord.uid);
  const existing = await adminRef.get();
  if (existing.exists && (existing.data().status || 'active') === 'active') {
    throw conflict(`${email} sudah terdaftar sebagai admin dengan role "${existing.data().role}".`);
  }

  await adminRef.set({
    email,
    name: name || userRecord.displayName || email.split('@')[0],
    role,
    status: 'active',
    invitedBy: actor.uid,
    createdAt: existing.exists ? existing.data().createdAt || FieldValue.serverTimestamp() : FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });

  await auth.setCustomUserClaims(userRecord.uid, { admin: true, role });

  const resetLink = await auth.generatePasswordResetLink(email);

  await writeAudit(db, actor, {
    action: 'invite_admin',
    targetType: 'admin_user',
    targetId: userRecord.uid,
    reason: `${email} sebagai ${role}`,
    metadata: { email, role },
  });

  return { uid: userRecord.uid, email, role, resetLink };
}

/**
 * Audit log untuk aksi yang masih ditulis langsung ke Firestore dari React
 * (suspend user, moderasi Explore, kategori, promo). adminId diambil dari
 * token, bukan dari body.
 */
export async function writeAdminAuditLog(req) {
  const actor = await requireAdmin(req, null);
  const body = readBody(req);
  // Dibaca dari `auditAction`, BUKAN `action`: field `action` sudah dipakai
  // router di api/admin.js untuk memilih handler, jadi memakai nama yang sama
  // untuk dua hal berbeda membuat keduanya saling menimpa.
  const action = requireString(body.auditAction, 'auditAction');
  const db = adminDb();

  const id = await writeAudit(db, actor, {
    action,
    targetType: typeof body.targetType === 'string' ? body.targetType : null,
    targetId: typeof body.targetId === 'string' ? body.targetId : null,
    reason: typeof body.reason === 'string' ? body.reason : null,
    metadata: body.metadata && typeof body.metadata === 'object' ? body.metadata : null,
  });

  return { logId: id };
}

/** Status booking yang masih berjalan — akun dengan salah satunya tidak boleh dihapus. */
const BOOKING_AKTIF = [
  'pending_payment', 'paid', 'confirmed', 'upcoming', 'in_progress',
  'completed', 'customer_confirmed', 'refund_requested', 'disputed',
];

/**
 * Hapus akun pengguna atau creator.
 *
 * Penghapusan TIDAK bisa dilakukan dari browser: firestore.rules menetapkan
 * `allow delete: if false` pada koleksi users, dan lagi pula menghapus profil
 * saja tidak ada gunanya kalau akun loginnya masih hidup. Karena itu aksi ini
 * dikerjakan di server, di mana Admin SDK bisa menyentuh Firebase Auth.
 *
 * Yang dilakukan:
 *  1. menolak bila pengguna masih punya booking berjalan atau saldo tersisa —
 *     menghapusnya akan memutus jejak uang yang belum selesai;
 *  2. menonaktifkan akun Firebase Auth sehingga tidak bisa login lagi;
 *  3. menghapus profil creator bila ada;
 *  4. menandai dokumen users sebagai 'deleted', BUKAN menghapusnya.
 *
 * Langkah terakhir disengaja: booking, pembayaran, dan ulasan lama menyimpan
 * customerId. Menghapus dokumennya membuat riwayat transaksi menunjuk ke akun
 * yang tidak ada, dan itu merusak pembukuan yang justru wajib disimpan.
 */
export async function adminDeleteUser(req) {
  const actor = await requireAdmin(req, 'manage_users');
  const body = readBody(req);
  const userId = requireString(body.userId, 'userId');
  const reason = typeof body.reason === 'string' ? body.reason.trim() : '';

  if (userId === actor.uid) throw conflict('Anda tidak bisa menghapus akun Anda sendiri.');

  const auth = adminAuth();
  const db = adminDb();

  const adminSnap = await db.collection('admin_users').doc(userId).get();
  if (adminSnap.exists) {
    throw conflict('Akun ini terdaftar sebagai admin. Cabut aksesnya lewat menu Admin Users terlebih dahulu.');
  }

  // Booking dicek dari dua sisi: pengguna bisa berperan sebagai pelanggan
  // maupun creator pada booking yang berbeda.
  for (const field of ['customerId', 'creatorId']) {
    const snap = await db.collection('bookings').where(field, '==', userId).get();
    const aktif = snap.docs.filter((d) => BOOKING_AKTIF.includes(d.data().status));
    if (aktif.length) {
      throw conflict(
        `Masih ada ${aktif.length} booking berjalan pada akun ini. Selesaikan atau batalkan dulu sebelum menghapus.`
      );
    }
  }

  const walletSnap = await db.collection('wallets').doc(userId).get();
  if (walletSnap.exists) {
    const w = walletSnap.data() || {};
    const sisa = (Number(w.availableBalance) || 0) + (Number(w.pendingBalance) || 0);
    if (sisa > 0) {
      throw conflict('Dompet creator ini masih menyimpan saldo. Proses penarikannya dulu sebelum menghapus.');
    }
  }

  let authDinonaktifkan = false;
  try {
    await auth.updateUser(userId, { disabled: true });
    authDinonaktifkan = true;
  } catch (err) {
    // Akun Auth boleh saja sudah tidak ada (mis. data contoh yang hanya berupa
    // dokumen Firestore). Itu bukan alasan membatalkan penghapusan profil.
    if (!err || err.code !== 'auth/user-not-found') throw err;
  }

  const creatorSnap = await db.collection('creators').doc(userId).get();
  const adalahCreator = creatorSnap.exists;
  if (adalahCreator) await db.collection('creators').doc(userId).delete();

  const userSnap = await db.collection('users').doc(userId).get();
  if (!userSnap.exists && !adalahCreator) throw notFound('Pengguna tidak ditemukan.');
  if (userSnap.exists) {
    await db.collection('users').doc(userId).set(
      { status: 'deleted', deletedAt: FieldValue.serverTimestamp(), deletedBy: actor.uid },
      { merge: true }
    );
  }

  await writeAudit(db, actor, {
    action: 'delete_user',
    targetType: adalahCreator ? 'creator' : 'user',
    targetId: userId,
    reason: reason || null,
    metadata: { authDinonaktifkan, profilCreatorDihapus: adalahCreator },
  });

  return { userId, authDinonaktifkan, profilCreatorDihapus: adalahCreator };
}

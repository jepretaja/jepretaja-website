import { FieldValue } from './firebaseAdmin.js';

/**
 * Tulis satu baris audit_logs. adminId SELALU diambil dari token yang sudah
 * diverifikasi server, bukan dari body request, supaya admin tidak bisa
 * mengaku-ngaku sebagai admin lain.
 *
 * Kalau `tx` diisi (Firestore Transaction), penulisan ikut transaksi supaya
 * audit log dan perubahan saldo terjadi bersamaan atau tidak sama sekali.
 */
export function writeAudit(db, actor, entry, tx = null) {
  const ref = db.collection('audit_logs').doc();
  const data = {
    adminId: actor.uid,
    adminEmail: actor.email || null,
    adminRole: actor.role || null,
    action: entry.action,
    targetType: entry.targetType || null,
    targetId: entry.targetId || null,
    reason: entry.reason || null,
    metadata: entry.metadata || null,
    createdAt: FieldValue.serverTimestamp(),
  };
  if (tx) {
    tx.set(ref, data);
    return ref.id;
  }
  return ref.set(data).then(() => ref.id);
}

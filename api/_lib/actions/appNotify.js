import { adminDb } from '../firebaseAdmin.js';
import { requireUser } from '../authUser.js';
import { readBody, requireString, requireOneOf, notFound, conflict, forbidden } from '../http.js';
import { writeNotification } from '../notify.js';

/**
 * Notifikasi suka / komentar / follow.
 *
 * Kenapa lewat server padahal aksinya terjadi di aplikasi? Karena aturan
 * Firestore menutup pembuatan dokumen `notifications` dari klien sepenuhnya
 * (`allow create: if false`), dan itu memang benar: kalau aplikasi boleh
 * menulis sendiri, siapa pun bisa mengirim notifikasi karangan atas nama orang
 * lain tanpa pernah menyukai apa pun.
 *
 * Karena itu aksi ini TIDAK memercayai isi request. Ia memeriksa dulu bahwa
 * penanda sukanya benar-benar ada, komentarnya benar-benar milik pemanggil,
 * atau dokumen follow-nya benar-benar tercatat — baru notifikasinya ditulis.
 *
 * Id dokumen untuk suka dan follow dibuat deterministik
 * (`like_{postId}_{uid}`), sehingga menekan hati atau tombol Ikuti bolak-balik
 * tidak menumpuk notifikasi baru di inbox orang lain; yang ada hanya diperbarui.
 */
export async function notifyInteraction(req) {
  const actor = await requireUser(req);
  const body = readBody(req);
  const type = requireOneOf(body.type, 'type', ['like', 'comment', 'follow']);
  const db = adminDb();
  const namaAktor = actor.profile?.name || 'Seseorang';

  if (type === 'follow') {
    const creatorId = requireString(body.creatorId, 'creatorId');
    if (creatorId === actor.uid) return { skipped: 'diri sendiri' };

    const follow = await db.collection('follows').doc(`${creatorId}_${actor.uid}`).get();
    if (!follow.exists) throw conflict('Belum tercatat mengikuti creator ini.');

    writeNotification(db, null, {
      docId: `follow_${creatorId}_${actor.uid}`,
      userId: creatorId,
      type: 'follow',
      title: 'Pengikut baru',
      body: `${namaAktor} mulai mengikutimu.`,
      referenceId: actor.uid,
    });
    return { ok: true };
  }

  const postId = requireString(body.postId, 'postId');
  const postSnap = await db.collection('explore_posts').doc(postId).get();
  if (!postSnap.exists) throw notFound('Post tidak ditemukan.');
  const pemilikPost = postSnap.data().creatorId;

  if (type === 'like') {
    if (!pemilikPost || pemilikPost === actor.uid) return { skipped: 'karya sendiri' };
    const like = await db.collection('explore_likes').doc(`${postId}_${actor.uid}`).get();
    if (!like.exists) throw conflict('Belum tercatat menyukai post ini.');

    writeNotification(db, null, {
      docId: `like_${postId}_${actor.uid}`,
      userId: pemilikPost,
      type: 'like',
      title: 'Karyamu disukai',
      body: `${namaAktor} menyukai karyamu.`,
      referenceId: postId,
    });
    return { ok: true };
  }

  // type === 'comment'
  const commentId = requireString(body.commentId, 'commentId');
  const commentSnap = await db.collection('explore_comments').doc(commentId).get();
  if (!commentSnap.exists) throw notFound('Komentar tidak ditemukan.');
  const komentar = commentSnap.data();
  if (komentar.userId !== actor.uid) throw forbidden('Komentar ini bukan milik Anda.');

  // Balasan memberi tahu penulis komentar induknya, bukan pemilik post —
  // orang yang dibalas itulah yang sedang diajak bicara.
  let tujuan = pemilikPost;
  let judul = 'Komentar baru';
  let isi = `${namaAktor} mengomentari karyamu: "${(komentar.text || '').slice(0, 80)}"`;

  if (komentar.parentId) {
    const induk = await db.collection('explore_comments').doc(komentar.parentId).get();
    if (induk.exists && induk.data().userId) {
      tujuan = induk.data().userId;
      judul = 'Balasan komentar';
      isi = `${namaAktor} membalas komentarmu: "${(komentar.text || '').slice(0, 80)}"`;
    }
  }

  if (!tujuan || tujuan === actor.uid) return { skipped: 'kontennya sendiri' };

  writeNotification(db, null, {
    userId: tujuan,
    type: 'comment',
    title: judul,
    body: isi,
    referenceId: postId,
  });
  return { ok: true };
}

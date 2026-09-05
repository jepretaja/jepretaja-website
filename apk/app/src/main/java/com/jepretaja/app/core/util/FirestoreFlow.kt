package com.jepretaja.app.core.util

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val TAG_FIRESTORE = "JepretAjaFirestore"

/**
 * Daftar dokumen live.
 *
 * PENTING — kenapa kegagalan TIDAK lagi meruntuhkan flow:
 *
 * Versi sebelumnya memanggil `close(error)` saat listener gagal. Akibatnya
 * flow melempar, dan `collectAsState` di layar mana pun yang mengumpulkannya
 * tanpa `.catch` ikut melempar di dalam komposisi — aplikasinya tertutup.
 * Padahal dua penyebab kegagalan paling sering justru yang paling wajar
 * terjadi di lapangan:
 *
 * - indeks komposit belum di-deploy (FAILED_PRECONDITION),
 * - aturan Firestore menolak satu query (PERMISSION_DENIED).
 *
 * Keduanya semestinya berarti "bagian ini kosong", bukan "seluruh halaman
 * mati". Sekarang errornya dicatat ke logcat dengan tag JepretAjaFirestore —
 * termasuk tautan pembuatan indeks yang disertakan Firestore di pesannya —
 * lalu flow meneruskan daftar kosong.
 *
 * Satu dokumen yang gagal dipetakan juga tidak lagi menjatuhkan sisanya:
 * dokumen lama yang bentuk fieldnya berbeda dilewati, bukan dilemparkan.
 */
inline fun <reified T : Any> Query.asFlow(): Flow<List<T>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            Log.e(TAG_FIRESTORE, "Query gagal: ${error.code} — ${error.message}", error)
            trySend(emptyList())
            return@addSnapshotListener
        }
        val items = snapshot?.documents?.mapNotNull { doc ->
            runCatching { doc.toObject(T::class.java) }
                .onFailure { Log.e(TAG_FIRESTORE, "Dokumen ${doc.id} gagal dipetakan", it) }
                .getOrNull()
        } ?: emptyList()
        trySend(items)
    }
    awaitClose { registration.remove() }
}

/** Satu dokumen live. Kegagalan diterjemahkan jadi null, bukan meruntuhkan layar. */
inline fun <reified T : Any> DocumentReference.asFlow(): Flow<T?> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            Log.e(TAG_FIRESTORE, "Dokumen $path gagal dibaca: ${error.code} — ${error.message}", error)
            trySend(null)
            return@addSnapshotListener
        }
        val item = if (snapshot?.exists() == true) {
            runCatching { snapshot.toObject(T::class.java) }
                .onFailure { Log.e(TAG_FIRESTORE, "Dokumen $path gagal dipetakan", it) }
                .getOrNull()
        } else {
            null
        }
        trySend(item)
    }
    awaitClose { registration.remove() }
}

/**
 * Hanya "apakah dokumen ini ada?" secara live.
 *
 * Dipakai untuk penanda relasi seperti explore_likes/{postId}_{userId} dan
 * follows/{creatorId}_{userId}, yang isinya tidak pernah dibaca — yang penting
 * hanya ada/tidaknya. Kegagalan diterjemahkan menjadi `false`.
 */
fun DocumentReference.existsAsFlow(): Flow<Boolean> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            trySend(false)
            return@addSnapshotListener
        }
        trySend(snapshot?.exists() == true)
    }
    awaitClose { registration.remove() }
}

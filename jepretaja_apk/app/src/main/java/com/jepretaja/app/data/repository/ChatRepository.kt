package com.jepretaja.app.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.core.util.asFlow
import com.jepretaja.app.data.model.ChatModel
import com.jepretaja.app.data.model.MessageModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(private val db: FirebaseFirestore) {

    fun streamChats(userId: String): Flow<List<ChatModel>> =
        db.collection(FirestorePaths.CHATS)
            .where(Filter.or(Filter.equalTo("customerId", userId), Filter.equalTo("creatorId", userId)))
            .orderBy("updatedAt", Query.Direction.DESCENDING).asFlow()

    fun streamChat(chatId: String) = db.collection(FirestorePaths.CHATS).document(chatId).asFlow<ChatModel>()

    /**
     * Jumlah pesan yang belum dibaca di SELURUH percakapan — angka untuk badge
     * pada tombol Chat.
     *
     * Penyaring `senderId` dikerjakan di sisi klien, bukan di query: Firestore
     * tidak mengizinkan `!=` digabung dengan array-contains, dan menambahkan
     * satu query per percakapan akan membengkak seiring jumlah chat. Pesan yang
     * kembali sudah pasti milik pengguna ini karena `participants` menyaringnya,
     * jadi yang tersisa hanya membuang pesan yang ia kirim sendiri.
     */
    fun streamUnreadCount(myUserId: String): Flow<Int> = callbackFlow {
        val registration = db.collection(FirestorePaths.MESSAGES)
            .whereArrayContains("participants", myUserId)
            .whereEqualTo("readAt", null)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(0)
                    return@addSnapshotListener
                }
                val jumlah = snapshot?.documents?.count { it.getString("senderId") != myUserId } ?: 0
                trySend(jumlah)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Isi satu ruang chat.
     *
     * Filter `participants array-contains myUserId` WAJIB ada, bukan sekadar
     * pengaman ganda. Untuk operasi query (bukan ambil satu dokumen), Firestore
     * tidak mengambil dulu lalu menyaring: ia menolak query yang batasannya
     * tidak MEMBUKTIKAN bahwa semua hasilnya lolos aturan. Aturan pesan menilai
     * keanggotaan lewat field `participants`, jadi query yang hanya menyaring
     * chatId ditolak seluruhnya — isi chat tampak kosong padahal datanya ada.
     */
    fun streamMessages(chatId: String, myUserId: String, limit: Long = 40): Flow<List<MessageModel>> =
        db.collection(FirestorePaths.MESSAGES)
            .whereArrayContains("participants", myUserId)
            .whereEqualTo("chatId", chatId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            // Dibatasi, dan batasnya dinaikkan oleh layar saat pengguna meminta
            // pesan lama. Tanpa batas, membuka percakapan panjang menarik
            // SELURUH riwayatnya sekaligus — satu pembacaan per pesan, setiap
            // kali ruang chat dibuka.
            .limit(limit).asFlow()

    /**
     * Jumlah pesan belum dibaca PER percakapan, untuk lencana di daftar chat.
     *
     * Satu listener untuk semua percakapan, memakai query yang sama dengan
     * [streamUnreadCount] lalu dikelompokkan menurut chatId di klien. Membuat
     * satu query per baris daftar akan berlipat seiring bertambahnya percakapan,
     * padahal jawabannya ada di kumpulan dokumen yang itu-itu juga.
     */
    fun streamUnreadPerChat(myUserId: String): Flow<Map<String, Int>> = callbackFlow {
        val registration = db.collection(FirestorePaths.MESSAGES)
            .whereArrayContains("participants", myUserId)
            .whereEqualTo("readAt", null)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyMap())
                    return@addSnapshotListener
                }
                val hasil = snapshot?.documents.orEmpty()
                    .filter { it.getString("senderId") != myUserId }
                    .mapNotNull { it.getString("chatId") }
                    .groupingBy { it }
                    .eachCount()
                trySend(hasil)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Daftar peserta chat, dibaca sekali lalu ikut ditulis ke setiap pesan.
     *
     * Aturan Firestore memeriksa keanggotaan lewat field `participants` di
     * dokumen pesan itu sendiri, bukan dengan menengok dokumen chat induknya:
     * aturan hanya boleh melakukan sedikit pemanggilan get() per permintaan,
     * sedangkan layar chat menarik seluruh riwayat sekaligus. Menyalin dua id
     * ini ke tiap pesan menukar sedikit duplikasi data dengan pembacaan yang
     * tidak pernah menabrak batas kuota.
     */
    private suspend fun participantsOf(chatId: String): List<String> {
        val chat = db.collection(FirestorePaths.CHATS).document(chatId).get().await()
        return listOfNotNull(chat.getString("customerId"), chat.getString("creatorId"))
    }

    suspend fun sendMessage(chatId: String, senderId: String, text: String) {
        val participants = participantsOf(chatId)
        catatWaktuBalas(chatId, senderId)
        db.collection(FirestorePaths.MESSAGES).add(
            mapOf(
                "chatId" to chatId, "senderId" to senderId, "participants" to participants,
                "type" to "text", "text" to text,
                "createdAt" to FieldValue.serverTimestamp(), "readAt" to null,
            )
        ).await()
        db.collection(FirestorePaths.CHATS).document(chatId).update("lastMessage", text, "updatedAt", FieldValue.serverTimestamp()).await()
    }

    /**
     * Memperbarui rata-rata waktu balas creator.
     *
     * Diukur di perangkat creator saat ia menekan kirim: selisih antara pesan
     * terakhir dari lawan bicara dan balasannya. Rata-ratanya bergerak (bobot
     * 1:4 terhadap nilai lama) supaya satu balasan yang kebetulan sangat cepat
     * atau sangat lambat tidak langsung mengubah angka yang dilihat calon
     * pelanggan.
     *
     * Batasnya jujur: karena ditulis dari perangkat creator sendiri, angka ini
     * pada dasarnya laporan diri. Ia berguna sebagai perkiraan kasar, bukan
     * jaminan — dan sengaja TIDAK dipakai untuk apa pun yang menentukan uang.
     */
    private suspend fun catatWaktuBalas(chatId: String, senderId: String) {
        runCatching {
            val chat = db.collection(FirestorePaths.CHATS).document(chatId).get().await()
            if (chat.getString("creatorId") != senderId) return

            val terakhir = db.collection(FirestorePaths.MESSAGES)
                .whereEqualTo("chatId", chatId)
                .whereArrayContains("participants", senderId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(1).get().await()
                .documents.firstOrNull() ?: return

            if (terakhir.getString("senderId") == senderId) return
            val waktu = terakhir.getTimestamp("createdAt")?.toDate()?.time ?: return
            val menit = ((System.currentTimeMillis() - waktu) / 60000).toInt().coerceIn(0, 60 * 24 * 3)

            val creatorRef = db.collection(FirestorePaths.CREATORS).document(senderId)
            val lama = creatorRef.get().await().getLong("avgResponseMinutes")?.toInt()
            val baru = if (lama == null) menit else ((lama * 4 + menit) / 5)
            creatorRef.set(mapOf("avgResponseMinutes" to baru), SetOptions.merge()).await()
        }
    }

    suspend fun sendImageMessage(chatId: String, senderId: String, imageUrl: String) {
        val participants = participantsOf(chatId)
        db.collection(FirestorePaths.MESSAGES).add(
            mapOf(
                "chatId" to chatId, "senderId" to senderId, "participants" to participants,
                "type" to "image", "mediaUrl" to imageUrl,
                "createdAt" to FieldValue.serverTimestamp(), "readAt" to null,
            )
        ).await()
        db.collection(FirestorePaths.CHATS).document(chatId).update("lastMessage", "\uD83D\uDCF7 Foto", "updatedAt", FieldValue.serverTimestamp()).await()
    }

    suspend fun getOrCreateChatForBooking(bookingId: String, customerId: String, creatorId: String, otherPartyName: String): String {
        val existing = db.collection(FirestorePaths.CHATS).whereEqualTo("bookingId", bookingId).limit(1).get().await()
        if (!existing.isEmpty) return existing.documents.first().id
        val ref = db.collection(FirestorePaths.CHATS).add(
            mapOf("bookingId" to bookingId, "customerId" to customerId, "creatorId" to creatorId, "otherPartyName" to otherPartyName, "lastMessage" to "", "updatedAt" to FieldValue.serverTimestamp())
        ).await()
        return ref.id
    }

    suspend fun getOrCreateDirectChat(customerId: String, creatorId: String, otherPartyName: String): String {
        val existing = db.collection(FirestorePaths.CHATS)
            .whereEqualTo("customerId", customerId).whereEqualTo("creatorId", creatorId)
            .whereEqualTo("bookingId", null).limit(1).get().await()
        if (!existing.isEmpty) return existing.documents.first().id
        val ref = db.collection(FirestorePaths.CHATS).add(
            mapOf("bookingId" to null, "customerId" to customerId, "creatorId" to creatorId, "otherPartyName" to otherPartyName, "lastMessage" to "", "updatedAt" to FieldValue.serverTimestamp())
        ).await()
        return ref.id
    }

    suspend fun markMessagesAsRead(chatId: String, myUserId: String) {
        // Alasan yang sama seperti streamMessages: tanpa batasan participants,
        // query ini ditolak aturan sehingga tidak ada pesan yang pernah ditandai
        // sudah dibaca.
        val unread = db.collection(FirestorePaths.MESSAGES)
            .whereArrayContains("participants", myUserId)
            .whereEqualTo("chatId", chatId).whereEqualTo("readAt", null).get().await()
        if (unread.isEmpty) return
        val batch = db.batch()
        unread.documents.forEach { doc ->
            if (doc.getString("senderId") != myUserId) batch.update(doc.reference, "readAt", FieldValue.serverTimestamp())
        }
        batch.commit().await()
    }

    suspend fun blockUser(blockerId: String, blockedId: String) {
        db.collection(FirestorePaths.BLOCKED_USERS).document("${blockerId}_$blockedId").set(
            mapOf("blockerId" to blockerId, "blockedId" to blockedId, "createdAt" to FieldValue.serverTimestamp())
        ).await()
    }

    suspend fun unblockUser(blockerId: String, blockedId: String) {
        db.collection(FirestorePaths.BLOCKED_USERS).document("${blockerId}_$blockedId").delete().await()
    }

    suspend fun isBlockedByMe(myUserId: String, otherUserId: String): Boolean =
        db.collection(FirestorePaths.BLOCKED_USERS).document("${myUserId}_$otherUserId").get().await().exists()

    /** True bila salah satu pihak sudah memblokir pihak lain — gabungan 2
     * listener dokumen tanpa dependency tambahan. */
    fun streamIsBlocked(userA: String, userB: String): Flow<Boolean> = callbackFlow {
        var aBlockedB = false; var bBlockedA = false; var aReady = false; var bReady = false
        fun emit() { if (aReady && bReady) trySend(aBlockedB || bBlockedA) }
        val reg1 = db.collection(FirestorePaths.BLOCKED_USERS).document("${userA}_$userB")
            .addSnapshotListener { doc, _ -> aBlockedB = doc?.exists() == true; aReady = true; emit() }
        val reg2 = db.collection(FirestorePaths.BLOCKED_USERS).document("${userB}_$userA")
            .addSnapshotListener { doc, _ -> bBlockedA = doc?.exists() == true; bReady = true; emit() }
        awaitClose { reg1.remove(); reg2.remove() }
    }
}

package com.jepretaja.app.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jepretaja.app.core.util.CaptionParser
import com.jepretaja.app.core.util.CloudFunctions
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.core.util.asFlow
import com.jepretaja.app.core.util.existsAsFlow
import com.jepretaja.app.data.model.CreatorModel
import com.jepretaja.app.data.model.ExplorePostModel
import com.jepretaja.app.data.model.FollowModel
import com.jepretaja.app.data.model.ReportModel
import com.jepretaja.app.data.remote.ApiClient
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExploreRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val api: ApiClient,
) {

    private companion object {
        /** Penanda "siapa menyukai komentar mana" — sengaja koleksi terpisah,
         * bukan array di dalam dokumen komentar, supaya jumlah penyuka tidak
         * dibatasi ukuran satu dokumen. Namanya diambil dari FirestorePaths
         * supaya satu-satunya daftar nama koleksi tetap di satu tempat. */
        const val COMMENT_LIKES = FirestorePaths.EXPLORE_COMMENT_LIKES
    }

    /**
     * Feed utama, dengan batas yang bisa dinaikkan.
     *
     * Halaman berikutnya diambil dengan MENAIKKAN limit dan berlangganan ulang,
     * bukan dengan `startAfter`. Sebabnya feed ini memakai snapshot listener:
     * dengan kursor, karya yang dihapus atau berubah status di halaman pertama
     * akan menggeser batas halaman berikutnya dan membuat post terlewat atau
     * terduplikasi. Batas yang tumbuh selalu memberi satu daftar yang konsisten.
     */
    fun streamFeed(category: String? = null, limit: Long = 30): Flow<List<ExplorePostModel>> {
        var q: Query = db.collection(FirestorePaths.EXPLORE_POSTS).whereEqualTo("status", "published")
        if (category != null && category !in listOf("For You", "Following", "Nearby")) {
            q = q.whereEqualTo("category", category)
        }
        return q.orderBy("createdAt", Query.Direction.DESCENDING).limit(limit).asFlow()
    }

    /**
     * Creator yang layak disarankan: aktif, dengan pengikut terbanyak.
     *
     * Dipakai tab Following yang masih kosong. Layar kosong pada pengguna baru
     * adalah jalan buntu — ia diminta mengikuti seseorang tanpa diberi satu pun
     * nama untuk mulai.
     */
    fun streamSuggestedCreators(limit: Long = 10): Flow<List<CreatorModel>> =
        db.collection(FirestorePaths.CREATORS)
            .whereEqualTo("status", "active")
            .orderBy("followerCount", Query.Direction.DESCENDING)
            .limit(limit).asFlow()

    /** Id creator yang diikuti pengguna — bahan kecocokan untuk peringkat feed. */
    fun streamFollowedIds(userId: String): Flow<Set<String>> =
        db.collection(FirestorePaths.FOLLOWS)
            .whereEqualTo("userId", userId)
            .limit(200).asFlow<FollowModel>()
            .map { daftar -> daftar.map { it.creatorId }.toSet() }

    /** Satu post, untuk riwayat tontonan yang hanya menyimpan id. */
    suspend fun getPost(postId: String): ExplorePostModel? =
        db.collection(FirestorePaths.EXPLORE_POSTS).document(postId).get().await()
            .toObject(ExplorePostModel::class.java)

    /** Menyematkan satu komentar di puncak daftar komentar sebuah post. */
    suspend fun setPinnedComment(postId: String, requesterId: String, commentId: String?) {
        val ref = db.collection(FirestorePaths.EXPLORE_POSTS).document(postId)
        if (ref.get().await().getString("creatorId") != requesterId) {
            throw IllegalStateException("Hanya pemilik karya yang bisa menyematkan komentar.")
        }
        ref.update("pinnedCommentId", commentId).await()
    }

    fun streamByCreator(creatorId: String): Flow<List<ExplorePostModel>> =
        db.collection(FirestorePaths.EXPLORE_POSTS)
            .whereEqualTo("creatorId", creatorId)
            .whereEqualTo("status", "published")
            .orderBy("createdAt", Query.Direction.DESCENDING).asFlow()

    /**
     * Semua karya milik satu creator, TANPA menyaring status.
     *
     * streamByCreator hanya mengembalikan yang sudah `published`, jadi karya
     * yang sedang ditinjau atau ditolak seolah lenyap tanpa jejak — creator
     * mengira unggahannya gagal padahal sedang menunggu moderasi.
     */
    fun streamMyPosts(creatorId: String): Flow<List<ExplorePostModel>> =
        db.collection(FirestorePaths.EXPLORE_POSTS)
            .whereEqualTo("creatorId", creatorId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100).asFlow()

    fun streamComments(postId: String) =
        db.collection(FirestorePaths.EXPLORE_COMMENTS)
            .whereEqualTo("postId", postId)
            .orderBy("createdAt", Query.Direction.DESCENDING)

    suspend fun like(postId: String, userId: String) {
        val likeRef = db.collection(FirestorePaths.EXPLORE_LIKES).document("${postId}_$userId")
        val postRef = db.collection(FirestorePaths.EXPLORE_POSTS).document(postId)
        val jadiSuka = db.runTransaction { tx ->
            val existing = tx.get(likeRef)
            if (existing.exists()) {
                tx.delete(likeRef)
                tx.update(postRef, "metrics.like", FieldValue.increment(-1))
                false
            } else {
                tx.set(likeRef, mapOf("postId" to postId, "userId" to userId, "createdAt" to FieldValue.serverTimestamp()))
                tx.update(postRef, "metrics.like", FieldValue.increment(1))
                true
            }
        }.await()

        // Membatalkan suka tidak mengirim apa-apa — kalau tidak, menekan hati
        // bolak-balik jadi cara mengirim notifikasi berulang ke orang lain.
        if (jadiSuka == true) {
            beriTahu("like", mapOf("postId" to postId))
        }
    }

    suspend fun save(postId: String, userId: String) {
        val saveRef = db.collection(FirestorePaths.EXPLORE_SAVES).document("${postId}_$userId")
        val postRef = db.collection(FirestorePaths.EXPLORE_POSTS).document(postId)
        db.runTransaction { tx ->
            val existing = tx.get(saveRef)
            if (existing.exists()) {
                tx.delete(saveRef)
                tx.update(postRef, "metrics.save", FieldValue.increment(-1))
            } else {
                tx.set(saveRef, mapOf("postId" to postId, "userId" to userId, "createdAt" to FieldValue.serverTimestamp()))
                tx.update(postRef, "metrics.save", FieldValue.increment(1))
            }
        }.await()
    }

    /**
     * [parentId] diisi saat komentar ini adalah balasan atas komentar lain.
     *
     * Nama & foto penulis ikut disimpan di dokumen komentar (denormalisasi),
     * mengikuti pola yang sudah dipakai explore_posts dengan creatorName. Ini
     * disengaja: koleksi `users` menyimpan email, telepon, dan token FCM, jadi
     * aturan Firestore hanya mengizinkan pemiliknya sendiri membacanya. Kalau
     * layar komentar menerjemahkan userId lewat pembacaan `users`, seluruh nama
     * pasti gagal dibaca — dan satu-satunya cara membuatnya berhasil adalah
     * membuka data pribadi semua pengguna ke semua orang.
     */
    suspend fun addComment(
        postId: String,
        userId: String,
        text: String,
        parentId: String? = null,
        authorName: String = "",
        authorPhotoUrl: String? = null,
    ) {
        val ref = db.collection(FirestorePaths.EXPLORE_COMMENTS).add(
            mapOf(
                "postId" to postId, "userId" to userId, "text" to text,
                "parentId" to parentId, "likeCount" to 0L,
                "authorName" to authorName, "authorPhotoUrl" to authorPhotoUrl,
                "status" to "published", "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        db.collection(FirestorePaths.EXPLORE_POSTS).document(postId).update("metrics.comment", FieldValue.increment(1)).await()
        beriTahu(
            "comment",
            mapOf("postId" to postId, "commentId" to ref.id),
        )
    }

    /**
     * Memberi tahu pemilik konten soal suka / komentar / follow.
     *
     * Ditulis SERVER, bukan di sini: aturan Firestore menutup pembuatan dokumen
     * `notifications` dari klien sepenuhnya (`allow create: if false`). Kalau
     * dibuka supaya aplikasi bisa menulis sendiri, siapa pun bisa mengarang
     * notifikasi atas nama orang lain. Server memverifikasi dulu bahwa suka /
     * komentar / follow-nya memang ada sebelum menulis.
     *
     * Kegagalannya sengaja ditelan: notifikasi adalah efek samping, dan like
     * yang sudah tercatat tidak boleh ikut dianggap gagal hanya karena server
     * notifikasi tidak terjangkau.
     */
    private suspend fun beriTahu(type: String, payload: Map<String, Any?>) {
        runCatching { api.call(CloudFunctions.NOTIFY_INTERACTION, payload + ("type" to type)) }
    }

    /** Suka/batal suka satu komentar — penanda disimpan di explore_comment_likes. */
    suspend fun toggleCommentLike(commentId: String, userId: String) {
        val likeRef = db.collection(COMMENT_LIKES).document("${commentId}_$userId")
        val commentRef = db.collection(FirestorePaths.EXPLORE_COMMENTS).document(commentId)
        db.runTransaction { tx ->
            val existing = tx.get(likeRef)
            if (existing.exists()) {
                tx.delete(likeRef)
                tx.update(commentRef, "likeCount", FieldValue.increment(-1))
            } else {
                tx.set(likeRef, mapOf("commentId" to commentId, "userId" to userId, "createdAt" to FieldValue.serverTimestamp()))
                tx.update(commentRef, "likeCount", FieldValue.increment(1))
            }
        }.await()
    }

    fun isCommentLiked(commentId: String, userId: String): Flow<Boolean> =
        db.collection(COMMENT_LIKES).document("${commentId}_$userId").existsAsFlow()

    /**
     * Tambah satu tayangan.
     *
     * `metrics.view` sudah ada di model sejak awal tapi tidak pernah ada yang
     * menulisinya, jadi semua post selamanya menunjukkan 0 tayangan. Pemanggilnya
     * (feed) menahan diri agar satu post hanya dihitung sekali per sesi, supaya
     * menggulir bolak-balik tidak menggelembungkan angkanya.
     */
    suspend fun incrementViewCount(postId: String) {
        db.collection(FirestorePaths.EXPLORE_POSTS).document(postId)
            .update("metrics.view", FieldValue.increment(1)).await()
    }

    /**
     * Follow/unfollow dalam satu transaksi.
     *
     * Sebelumnya `follow()` selalu `set()` dokumen lalu `increment(1)` tanpa
     * memeriksa apa pun — menekan tombol Follow dua kali menambah followerCount
     * dua kali padahal pengikutnya tetap satu orang, dan tidak ada jalan untuk
     * berhenti mengikuti sama sekali. Sekarang keberadaan dokumen yang menentukan
     * arah aksi, dan counter hanya bergerak saat status benar-benar berubah.
     */
    suspend fun toggleFollow(
        creatorId: String,
        userId: String,
        userName: String = "",
        userPhotoUrl: String? = null,
        creatorName: String = "",
        creatorPhotoUrl: String? = null,
    ) {
        val followRef = db.collection(FirestorePaths.FOLLOWS).document("${creatorId}_$userId")
        val creatorRef = db.collection(FirestorePaths.CREATORS).document(creatorId)
        val jadiMengikuti = db.runTransaction { tx ->
            val existing = tx.get(followRef)
            if (existing.exists()) {
                tx.delete(followRef)
                tx.update(creatorRef, "followerCount", FieldValue.increment(-1))
                false
            } else {
                tx.set(
                    followRef,
                    mapOf(
                        "creatorId" to creatorId, "userId" to userId,
                        // Lihat FollowModel: nama kedua pihak disimpan di sini
                        // supaya daftar Pengikut/Mengikuti tidak perlu membaca
                        // koleksi `users` yang tertutup aturan.
                        "userName" to userName, "userPhotoUrl" to userPhotoUrl,
                        "creatorName" to creatorName, "creatorPhotoUrl" to creatorPhotoUrl,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
                tx.update(creatorRef, "followerCount", FieldValue.increment(1))
                true
            }
        }.await()

        if (jadiMengikuti == true) {
            beriTahu("follow", mapOf("creatorId" to creatorId))
        }
    }

    /** Daftar pengikut satu creator. */
    fun streamFollowers(creatorId: String): Flow<List<FollowModel>> =
        db.collection(FirestorePaths.FOLLOWS)
            .whereEqualTo("creatorId", creatorId)
            .limit(200).asFlow()

    /** Daftar creator yang diikuti seorang pengguna. */
    fun streamFollowing(userId: String): Flow<List<FollowModel>> =
        db.collection(FirestorePaths.FOLLOWS)
            .whereEqualTo("userId", userId)
            .limit(200).asFlow()

    /** Status like/save/follow milik user saat ini — dipakai UI supaya ikon
     * bisa menunjukkan keadaan sebenarnya (hati merah kalau sudah disukai),
     * bukan tampil sama saja baik sudah ditekan maupun belum. */
    fun isLiked(postId: String, userId: String): Flow<Boolean> =
        db.collection(FirestorePaths.EXPLORE_LIKES).document("${postId}_$userId").existsAsFlow()

    fun isSaved(postId: String, userId: String): Flow<Boolean> =
        db.collection(FirestorePaths.EXPLORE_SAVES).document("${postId}_$userId").existsAsFlow()

    fun isFollowing(creatorId: String, userId: String): Flow<Boolean> =
        db.collection(FirestorePaths.FOLLOWS).document("${creatorId}_$userId").existsAsFlow()

    /** Berapa creator yang diikuti pengguna ini — angka "Mengikuti" di profil. */
    fun streamFollowingCount(userId: String): Flow<Int> = callbackFlow {
        val registration = db.collection(FirestorePaths.FOLLOWS)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                trySend(if (error != null) 0 else snapshot?.size() ?: 0)
            }
        awaitClose { registration.remove() }
    }

    /** Feed tab "Following": hanya post dari creator yang diikuti user ini.
     * Sebelumnya tab ini diam-diam menampilkan feed umum karena streamFeed()
     * membuang nilai "Following" tanpa menggantinya dengan apa pun. */
    fun streamFollowingFeed(userId: String): Flow<List<ExplorePostModel>> = flow {
        val follows = db.collection(FirestorePaths.FOLLOWS).whereEqualTo("userId", userId).get().await()
        val creatorIds = follows.documents.mapNotNull { it.getString("creatorId") }
        if (creatorIds.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        // whereIn dibatasi 30 nilai per query oleh Firestore, jadi dipecah.
        val posts = creatorIds.chunked(30).flatMap { chunk ->
            db.collection(FirestorePaths.EXPLORE_POSTS)
                .whereEqualTo("status", "published")
                .whereIn("creatorId", chunk)
                .get().await()
                .toObjects(ExplorePostModel::class.java)
        }
        emit(posts)
    }

    /**
     * Feed tab "Nearby": post dari creator yang lokasi layanannya dekat.
     *
     * Sama seperti "Following", tab ini sebelumnya diam-diam jatuh ke feed umum.
     * Jaraknya dihitung dari `serviceLat`/`serviceLng` milik creator dengan rumus
     * yang sama seperti layar Nearby, lalu post diurutkan dari creator terdekat.
     */
    fun streamNearbyFeed(lat: Double, lng: Double, radiusKm: Double = 50.0): Flow<List<ExplorePostModel>> = flow {
        val creators = db.collection(FirestorePaths.CREATORS).whereEqualTo("status", "active").get().await()
        val nearbyIds = creators.documents.mapNotNull { doc ->
            val cLat = doc.getDouble("serviceLat") ?: return@mapNotNull null
            val cLng = doc.getDouble("serviceLng") ?: return@mapNotNull null
            val distance = haversineKm(lat, lng, cLat, cLng)
            if (distance <= radiusKm) doc.id to distance else null
        }.sortedBy { it.second }

        if (nearbyIds.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        val order = nearbyIds.withIndex().associate { (index, pair) -> pair.first to index }
        val posts = nearbyIds.map { it.first }.chunked(30).flatMap { chunk ->
            db.collection(FirestorePaths.EXPLORE_POSTS)
                .whereEqualTo("status", "published")
                .whereIn("creatorId", chunk)
                .get().await()
                .toObjects(ExplorePostModel::class.java)
        }.sortedBy { order[it.creatorId] ?: Int.MAX_VALUE }
        emit(posts)
    }

    /** Jarak lingkaran besar dalam km. */
    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        return 2 * r * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }

    /**
     * Bersihkan jejak seorang pengguna saat akunnya dihapus: post, komentar,
     * like, save, dan follow miliknya.
     *
     * Sebelumnya penghapusan akun hanya menandai dokumen user sebagai "deleted",
     * sehingga post dan komentar lamanya tetap tayang atas nama akun yang sudah
     * tidak ada. Dikerjakan best-effort per koleksi: satu koleksi yang gagal
     * (mis. ditolak aturan) tidak boleh membatalkan pembersihan sisanya.
     */
    suspend fun purgeUserContent(userId: String) {
        suspend fun deleteWhere(collection: String, field: String) {
            runCatching {
                val snap = db.collection(collection).whereEqualTo(field, userId).get().await()
                snap.documents.chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            }
        }
        deleteWhere(FirestorePaths.EXPLORE_POSTS, "creatorId")
        deleteWhere(FirestorePaths.EXPLORE_COMMENTS, "userId")
        deleteWhere(FirestorePaths.EXPLORE_LIKES, "userId")
        deleteWhere(FirestorePaths.EXPLORE_SAVES, "userId")
        deleteWhere(FirestorePaths.FOLLOWS, "userId")
        deleteWhere(COMMENT_LIKES, "userId")
    }

    /** Post yang disimpan user — isi layar "Tersimpan". */
    /**
     * Post yang pernah disukai pengguna — isi tab "Disukai" di profil.
     *
     * Penandanya sudah lama ditulis setiap kali tombol hati ditekan, tapi tidak
     * pernah ada satu layar pun yang membacanya kembali.
     */
    fun streamLikedPosts(userId: String): Flow<List<ExplorePostModel>> = flow {
        val likes = db.collection(FirestorePaths.EXPLORE_LIKES).whereEqualTo("userId", userId).get().await()
        val postIds = likes.documents.mapNotNull { it.getString("postId") }
        val posts = postIds.mapNotNull { id ->
            runCatching {
                db.collection(FirestorePaths.EXPLORE_POSTS).document(id).get().await()
                    .toObject(ExplorePostModel::class.java)
            }.getOrNull()
        }
        emit(posts)
    }

    fun streamSavedPosts(userId: String): Flow<List<ExplorePostModel>> = flow {
        val saves = db.collection(FirestorePaths.EXPLORE_SAVES).whereEqualTo("userId", userId).get().await()
        val postIds = saves.documents.mapNotNull { it.getString("postId") }
        val posts = postIds.mapNotNull { id ->
            runCatching {
                db.collection(FirestorePaths.EXPLORE_POSTS).document(id).get().await()
                    .toObject(ExplorePostModel::class.java)
            }.getOrNull()
        }
        emit(posts)
    }

    /**
     * Hapus post milik sendiri. Aturan Firestore tetap penjaga terakhirnya —
     * pemeriksaan pemilik di sini hanya supaya UI tidak mengirim permintaan
     * yang pasti ditolak.
     */
    suspend fun deletePost(postId: String, requesterId: String) {
        val ref = db.collection(FirestorePaths.EXPLORE_POSTS).document(postId)
        val snap = ref.get().await()
        if (snap.getString("creatorId") != requesterId) {
            throw IllegalStateException("Hanya pemilik post yang bisa menghapus.")
        }
        ref.delete().await()
    }

    /**
     * Edit caption/kategori post yang sudah tayang.
     *
     * `tags` ikut ditulis ulang dari caption barunya — kalau tidak, hashtag lama
     * tetap menempel dan post terus muncul di halaman tagar yang sudah dihapus
     * penulisnya dari caption.
     */
    suspend fun updatePost(postId: String, requesterId: String, caption: String, category: String) {
        val ref = db.collection(FirestorePaths.EXPLORE_POSTS).document(postId)
        val snap = ref.get().await()
        if (snap.getString("creatorId") != requesterId) {
            throw IllegalStateException("Hanya pemilik post yang bisa mengubah.")
        }
        ref.update(
            mapOf(
                "caption" to caption,
                "category" to category,
                "tags" to CaptionParser.hashtags(caption),
            )
        ).await()
    }

    /** all | followers | off. Ditegakkan juga di firestore.rules, bukan hanya UI. */
    suspend fun setCommentPolicy(postId: String, requesterId: String, policy: String) {
        val ref = db.collection(FirestorePaths.EXPLORE_POSTS).document(postId)
        val snap = ref.get().await()
        if (snap.getString("creatorId") != requesterId) {
            throw IllegalStateException("Hanya pemilik post yang bisa mengubah.")
        }
        ref.update("commentPolicy", policy).await()
    }

    /**
     * Feed satu hashtag.
     *
     * Memakai array `tags` yang diisi saat unggah, bukan mencari di dalam teks
     * caption: Firestore tidak bisa mencari substring, dan `array-contains`
     * adalah satu-satunya cara mencocokkan tagar tanpa membaca seluruh koleksi.
     */
    fun streamByTag(tag: String): Flow<List<ExplorePostModel>> =
        db.collection(FirestorePaths.EXPLORE_POSTS)
            .whereArrayContains("tags", tag.lowercase().removePrefix("#"))
            .whereEqualTo("status", "published")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(60).asFlow()

    /** Hapus komentar sendiri, sekaligus mengoreksi penghitung komentar post. */
    suspend fun deleteComment(commentId: String, postId: String, requesterId: String) {
        val ref = db.collection(FirestorePaths.EXPLORE_COMMENTS).document(commentId)
        val snap = ref.get().await()
        if (snap.getString("userId") != requesterId) {
            throw IllegalStateException("Hanya penulis komentar yang bisa menghapus.")
        }
        ref.delete().await()
        db.collection(FirestorePaths.EXPLORE_POSTS).document(postId)
            .update("metrics.comment", FieldValue.increment(-1)).await()
    }

    suspend fun incrementShareCount(postId: String) {
        db.collection(FirestorePaths.EXPLORE_POSTS).document(postId).update("metrics.share", FieldValue.increment(1)).await()
    }

    suspend fun report(postId: String, userId: String, reason: String) {
        db.collection(FirestorePaths.REPORTS).add(
            mapOf("reporterId" to userId, "targetType" to "explore_post", "targetId" to postId, "reason" to reason, "status" to "open", "createdAt" to FieldValue.serverTimestamp())
        ).await()
    }

    /**
     * Melaporkan seorang creator.
     *
     * targetType-nya "creator", bukan "explore_post", supaya panel admin bisa
     * membedakan laporan tentang satu karya dari laporan tentang orangnya —
     * dua hal yang ditindaklanjuti dengan cara berbeda.
     */
    suspend fun reportCreator(creatorId: String, userId: String, reason: String) {
        db.collection(FirestorePaths.REPORTS).add(
            mapOf(
                "reporterId" to userId, "targetType" to "creator", "targetId" to creatorId,
                "reason" to reason, "status" to "open", "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    /** Laporan yang pernah diajukan user ini, dari semua targetType — dipakai di Profile > Laporan. */
    fun streamMyReports(userId: String): Flow<List<ReportModel>> =
        db.collection(FirestorePaths.REPORTS)
            .whereEqualTo("reporterId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .asFlow<ReportModel>()
            .catch { emit(emptyList()) }
}

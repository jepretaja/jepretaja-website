package com.jepretaja.app.ui.screens.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.data.model.ExplorePostModel
import com.jepretaja.app.data.repository.ExploreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** Satu komentar siap tampil — sudah digabung dengan profil penulisnya
 * dan dengan balasan-balasannya. */
/** Urutan daftar komentar yang bisa dipilih pengguna. */
enum class CommentSort(val label: String) { TERBARU("Terbaru"), TERATAS("Teratas") }

data class CommentUi(
    val commentId: String,
    val userId: String,
    val text: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val likeCount: Long = 0,
    val parentId: String? = null,
    val replies: List<CommentUi> = emptyList(),
)

@HiltViewModel
class ExploreDetailViewModel @Inject constructor(
    private val repository: ExploreRepository,
    private val db: FirebaseFirestore,
) : ViewModel() {

    private val _comments = MutableStateFlow<List<CommentUi>>(emptyList())
    val comments: StateFlow<List<CommentUi>> = _comments.asStateFlow()

    private var registration: ListenerRegistration? = null

    /** Hasil mentah dari listener, sebelum diurutkan. */
    private var induk: List<CommentUi> = emptyList()

    private val _post = MutableStateFlow<ExplorePostModel?>(null)
    val post: StateFlow<ExplorePostModel?> = _post.asStateFlow()

    private var postRegistration: ListenerRegistration? = null

    /** Post-nya diikuti supaya perubahan kebijakan komentar langsung terasa. */
    fun observePost(postId: String) {
        postRegistration?.remove()
        postRegistration = db.collection(FirestorePaths.EXPLORE_POSTS).document(postId)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                _post.value = snap?.toObject(ExplorePostModel::class.java)
                // Urutan dihitung ulang: sematan bisa berubah kapan saja.
                terapkanUrutan()
            }
    }

    fun isFollowing(creatorId: String, userId: String) = repository.isFollowing(creatorId, userId)

    /**
     * Menyematkan / melepas sematan komentar.
     *
     * Penandanya disimpan di dokumen post (`pinnedCommentId`), bukan di dokumen
     * komentar: yang tersemat selalu satu per karya, dan menyimpannya di satu
     * tempat membuat mustahil ada dua komentar yang sama-sama mengaku tersemat.
     */
    fun togglePinComment(postId: String, requesterId: String, commentId: String) {
        viewModelScope.launch {
            val sekarang = _post.value?.pinnedCommentId
            runCatching {
                repository.setPinnedComment(postId, requesterId, if (sekarang == commentId) null else commentId)
            }
        }
    }

    private val _sort = MutableStateFlow(CommentSort.TERBARU)
    val sort: StateFlow<CommentSort> = _sort.asStateFlow()

    /**
     * Urutan diubah di memori, bukan lewat query baru.
     *
     * Mengurutkan dengan `orderBy("likeCount")` menuntut indeks komposit
     * (postId, likeCount) yang harus di-deploy lebih dulu, dan komentar satu
     * post sudah seluruhnya ada di tangan kita — mengurutkannya di sini tidak
     * menambah satu pun pembacaan Firestore.
     */
    fun setSort(value: CommentSort) {
        _sort.value = value
        terapkanUrutan()
    }

    private fun terapkanUrutan() {
        val urut = when (_sort.value) {
            CommentSort.TERBARU -> induk
            CommentSort.TERATAS -> induk.sortedByDescending { it.likeCount }
        }
        // Komentar tersemat selalu di puncak, apa pun urutan yang dipilih —
        // itulah arti "disematkan".
        val idTersemat = _post.value?.pinnedCommentId
        _comments.value = if (idTersemat == null) {
            urut
        } else {
            val tersemat = urut.firstOrNull { it.commentId == idTersemat }
            if (tersemat == null) urut else listOf(tersemat) + urut.filterNot { it.commentId == idTersemat }
        }
    }

    /**
     * Nama & foto penulis dibaca langsung dari dokumen komentarnya sendiri.
     *
     * Sebelumnya layar ini menuliskan "Pengguna" untuk SEMUA orang karena
     * dokumen komentar cuma menyimpan userId. Terjemahan userId -> profil TIDAK
     * bisa dilakukan dengan membaca koleksi `users`: koleksi itu memuat email,
     * nomor telepon, dan token FCM, dan aturan Firestore hanya mengizinkan
     * pemiliknya sendiri membacanya. Karena itu nama & foto ikut disimpan saat
     * komentar dibuat (lihat ExploreRepository.addComment).
     *
     * Komentar lama yang dibuat sebelum perubahan ini belum punya field tersebut,
     * jadi tetap ditampilkan sebagai "Pengguna" — bukan kegagalan, hanya data
     * lama yang tidak lengkap.
     */
    fun observeComments(postId: String) {
        registration?.remove()
        registration = db.collection(FirestorePaths.EXPLORE_COMMENTS)
            .whereEqualTo("postId", postId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                val flat = snap.documents.map { doc ->
                    CommentUi(
                        commentId = doc.id,
                        userId = doc.getString("userId").orEmpty(),
                        text = doc.getString("text").orEmpty(),
                        authorName = doc.getString("authorName")?.takeIf { it.isNotBlank() } ?: "Pengguna",
                        authorPhotoUrl = doc.getString("authorPhotoUrl"),
                        likeCount = doc.getLong("likeCount") ?: 0L,
                        parentId = doc.getString("parentId"),
                    )
                }

                // Disusun jadi dua tingkat: komentar utama (terbaru dulu) dan
                // balasannya (terlama dulu, supaya percakapan terbaca urut).
                val repliesByParent = flat.filter { it.parentId != null }.groupBy { it.parentId }
                induk = flat
                    .filter { it.parentId == null }
                    .map { parent ->
                        parent.copy(replies = repliesByParent[parent.commentId].orEmpty().reversed())
                    }
                terapkanUrutan()
            }
    }

    override fun onCleared() {
        registration?.remove()
        postRegistration?.remove()
        super.onCleared()
    }

    fun addComment(
        postId: String,
        userId: String,
        text: String,
        parentId: String? = null,
        authorName: String = "",
        authorPhotoUrl: String? = null,
    ) {
        viewModelScope.launch {
            runCatching { repository.addComment(postId, userId, text, parentId, authorName, authorPhotoUrl) }
        }
    }

    fun toggleCommentLike(commentId: String, userId: String) {
        viewModelScope.launch { runCatching { repository.toggleCommentLike(commentId, userId) } }
    }

    fun isCommentLiked(commentId: String, userId: String) = repository.isCommentLiked(commentId, userId)

    fun deleteComment(commentId: String, postId: String, userId: String) {
        viewModelScope.launch { runCatching { repository.deleteComment(commentId, postId, userId) } }
    }
}

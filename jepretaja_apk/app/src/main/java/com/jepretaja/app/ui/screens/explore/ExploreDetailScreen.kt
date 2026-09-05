package com.jepretaja.app.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.ui.components.AppAvatar
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.state.AuthViewModel
import kotlinx.coroutines.flow.flowOf

/**
 * Satu baris komentar — dipakai untuk komentar utama maupun balasannya
 * ([compact] mengecilkan avatar dan teks untuk tingkat balasan).
 */
@Composable
private fun CommentRow(
    comment: CommentUi,
    currentUserId: String?,
    viewModel: ExploreDetailViewModel,
    postId: String,
    onReply: () -> Unit,
    compact: Boolean = false,
    /** Pemilik karya boleh menyematkan komentar; null untuk orang lain. */
    onTogglePin: (() -> Unit)? = null,
    isPinned: Boolean = false,
) {
    val liked by remember(comment.commentId, currentUserId) {
        if (currentUserId != null) viewModel.isCommentLiked(comment.commentId, currentUserId) else flowOf(false)
    }.collectAsState(initial = false)

    Row(verticalAlignment = Alignment.Top) {
        AppAvatar(url = comment.authorPhotoUrl, name = comment.authorName, size = if (compact) 28.dp else 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.authorName,
                    style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                    color = AppColors.TextPrimary,
                )
                if (isPinned) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Disematkan",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.Primary,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(comment.text, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Balas",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.clickable(onClick = onReply),
                )
                if (onTogglePin != null) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        if (isPinned) "Lepas sematan" else "Sematkan",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.Primary,
                        modifier = Modifier.clickable(onClick = onTogglePin),
                    )
                }
                if (comment.userId == currentUserId) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "Hapus",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.Danger,
                        modifier = Modifier.clickable { viewModel.deleteComment(comment.commentId, postId, comment.userId) },
                    )
                }
            }
        }
        // Suka komentar — jumlahnya di bawah ikon, pola yang sama seperti feed.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = { currentUserId?.let { viewModel.toggleCommentLike(comment.commentId, it) } },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (liked) "Batal suka" else "Suka komentar",
                    tint = if (liked) AppColors.Danger else AppColors.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (comment.likeCount > 0) {
                Text("${comment.likeCount}", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
            }
        }
    }
}

/** Explore Detail / Comments (section 28). */
@Composable
fun ExploreDetailScreen(
    postId: String,
    onBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ExploreDetailViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    var commentText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<CommentUi?>(null) }
    val comments by viewModel.comments.collectAsState()
    val sort by viewModel.sort.collectAsState()

    LaunchedEffect(postId) {
        viewModel.observeComments(postId)
        viewModel.observePost(postId)
    }

    val post by viewModel.post.collectAsState()
    val mengikuti by remember(post?.creatorId, authState.uid) {
        val creatorId = post?.creatorId
        val uid = authState.uid
        if (creatorId != null && uid != null) viewModel.isFollowing(creatorId, uid) else flowOf(false)
    }.collectAsState(initial = false)

    // Kebijakan yang sama juga ditegakkan firestore.rules; penyaringan di sini
    // hanya supaya pengguna tidak mengetik komentar panjang lalu ditolak server.
    val bolehKomentar = when (post?.commentPolicy) {
        "off" -> post?.creatorId == authState.uid
        "followers" -> mengikuti || post?.creatorId == authState.uid
        else -> true
    }
    val alasanTertutup = when (post?.commentPolicy) {
        "off" -> "Komentar dimatikan untuk post ini."
        "followers" -> "Hanya pengikut creator yang bisa berkomentar di post ini."
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Komentar", style = MaterialTheme.typography.headlineSmall) }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
            })
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth()) {
                // Saat sedang membalas, konteksnya ditampilkan di atas kolom input
                // supaya jelas komentar siapa yang sedang dibalas — dan bisa dibatalkan.
                replyingTo?.let { target ->
                    Row(
                        Modifier.fillMaxWidth().background(AppColors.SurfaceVariant)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Membalas ${target.authorName}",
                            style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { replyingTo = null }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Batal membalas", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = commentText, onValueChange = { commentText = it },
                    enabled = bolehKomentar,
                    placeholder = {
                        Text(
                            alasanTertutup
                                ?: if (replyingTo != null) "Tulis balasan..." else "Tulis komentar...",
                        )
                    },
                    shape = RoundedCornerShape(percent = 50),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedBorderColor = AppColors.Primary,
                        unfocusedContainerColor = AppColors.SurfaceVariant,
                        focusedContainerColor = AppColors.SurfaceVariant,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    enabled = bolehKomentar,
                    onClick = {
                        val uid = authState.uid
                        if (uid != null && commentText.isNotBlank()) {
                            viewModel.addComment(
                                postId = postId,
                                userId = uid,
                                text = commentText.trim(),
                                parentId = replyingTo?.commentId,
                                authorName = authState.profile?.name.orEmpty(),
                                authorPhotoUrl = authState.profile?.photoUrl,
                            )
                            commentText = ""
                            replyingTo = null
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim") }
            }
            }
        },
    ) { padding ->
        if (comments.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(icon = Icons.Default.ChatBubbleOutline, title = "Belum ada komentar", description = "Jadilah yang pertama berkomentar di post ini.")
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Pemilih urutan ikut menggulir bersama daftarnya: menempelkannya
                // permanen di atas hanya memakan tinggi layar yang seharusnya
                // dipakai membaca komentar.
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${comments.size} komentar",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        CommentSort.entries.forEach { pilihan ->
                            FilterChip(
                                selected = sort == pilihan,
                                onClick = { viewModel.setSort(pilihan) },
                                label = { Text(pilihan.label, style = MaterialTheme.typography.labelMedium) },
                                shape = RoundedCornerShape(percent = 50),
                            )
                        }
                    }
                }
                items(comments) { c ->
                    PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 3.dp) {
                        CommentRow(
                            comment = c,
                            currentUserId = authState.uid,
                            viewModel = viewModel,
                            postId = postId,
                            onReply = { replyingTo = c },
                            // Hanya pemilik karya yang boleh menyematkan, dan
                            // hanya komentar tingkat pertama — balasan yang
                            // tersemat tanpa induknya akan kehilangan konteks.
                            onTogglePin = if (authState.uid != null && authState.uid == post?.creatorId) {
                                { viewModel.togglePinComment(postId, authState.uid!!, c.commentId) }
                            } else {
                                null
                            },
                            isPinned = post?.pinnedCommentId == c.commentId,
                        )
                        // Balasan menjorok ke dalam dengan garis penanda, bukan
                        // kartu terpisah — supaya terbaca sebagai satu utas.
                        if (c.replies.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Row {
                                Box(
                                    Modifier.padding(start = 17.dp).width(2.dp).height((c.replies.size * 62).dp.coerceAtMost(400.dp))
                                        .background(AppColors.Border),
                                )
                                Column(Modifier.padding(start = 12.dp)) {
                                    c.replies.forEach { reply ->
                                        CommentRow(
                                            comment = reply,
                                            currentUserId = authState.uid,
                                            viewModel = viewModel,
                                            postId = postId,
                                            // Balasan atas balasan tetap menempel ke
                                            // utas yang sama (maksimal dua tingkat).
                                            onReply = { replyingTo = c },
                                            compact = true,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

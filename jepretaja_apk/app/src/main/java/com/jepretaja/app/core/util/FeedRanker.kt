package com.jepretaja.app.core.util

import com.jepretaja.app.data.model.ExplorePostModel

/**
 * Peringkat sederhana untuk tab "For You".
 *
 * Sebelumnya "For You" hanya post terbaru diurutkan waktu — sama persis untuk
 * semua orang, dan karya bagus dari minggu lalu tenggelam selamanya di bawah
 * unggahan hari ini yang belum ditonton siapa pun.
 *
 * Peringkat ini dihitung DI PERANGKAT atas kumpulan kandidat yang sudah
 * terambil, bukan di server. Alasannya jujur: peringkat sungguhan butuh riwayat
 * perilaku, indeks tersendiri, dan pekerjaan latar yang berjalan terus — itu
 * proyek sendiri. Yang di sini cukup untuk membuat feed terasa dipilihkan,
 * tanpa satu pun infrastruktur baru.
 *
 * Empat bahan:
 * - **keterlibatan**: suka, komentar, dan simpan dibandingkan jumlah tayangan,
 *   bukan angka mentahnya. Tanpa pembagian ini, karya lama selalu menang hanya
 *   karena sudah lebih lama tayang.
 * - **kesegaran**: peluruhan halus menurut umur, bukan pemotongan keras.
 * - **kecocokan**: kategori yang diminati dan creator yang diikuti.
 * - **hukuman**: sudah pernah ditonton, dan "tidak tertarik" yang dibuang penuh.
 */
object FeedRanker {

    fun rank(
        posts: List<ExplorePostModel>,
        interests: Set<String>,
        followedCreatorIds: Set<String>,
        watched: Set<String>,
        notInterested: Set<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<ExplorePostModel> =
        posts
            .filterNot { it.postId in notInterested }
            .sortedByDescending { skor(it, interests, followedCreatorIds, watched, nowMillis) }

    private fun skor(
        post: ExplorePostModel,
        interests: Set<String>,
        followed: Set<String>,
        watched: Set<String>,
        now: Long,
    ): Double {
        val tayangan = maxOf(post.viewCount, 1L).toDouble()
        val keterlibatan =
            (post.likeCount + post.commentCount * 2 + post.saveCount * 3) / tayangan

        val umurJam = post.createdAt?.toDate()?.let { (now - it.time) / 3_600_000.0 } ?: 999.0
        // Separuh nilai kesegaran hilang tiap 72 jam. Angkanya dipilih supaya
        // karya berumur seminggu masih bisa menang kalau keterlibatannya jauh
        // lebih baik — feed yang hanya menghargai kebaruan menghukum creator
        // yang jarang mengunggah tapi karyanya kuat.
        val kesegaran = Math.pow(0.5, umurJam / 72.0)

        var skor = keterlibatan * 2.0 + kesegaran * 1.5
        if (post.category in interests) skor += 1.2
        if (post.creatorId in followed) skor += 1.0
        if (post.postId in watched) skor -= 2.0
        return skor
    }
}

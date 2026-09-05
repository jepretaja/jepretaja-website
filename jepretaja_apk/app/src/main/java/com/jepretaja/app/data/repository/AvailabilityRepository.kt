package com.jepretaja.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.jepretaja.app.data.remote.ApiClient
import com.jepretaja.app.core.util.CloudFunctions
import com.jepretaja.app.core.util.FirestorePaths
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvailabilityRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val api: ApiClient,
) {
    suspend fun blockDate(dateIso: String, reason: String? = null) {
        api.call(CloudFunctions.BLOCK_AVAILABILITY_DATE, mapOf("date" to dateIso, "reason" to reason))
    }

    suspend fun unblockDate(dateIso: String) {
        api.call(CloudFunctions.UNBLOCK_AVAILABILITY_DATE, mapOf("date" to dateIso))
    }

    fun streamBlockedDates(creatorId: String): Flow<List<LocalDate>> = callbackFlow {
        val registration = db.collection(FirestorePaths.AVAILABILITY_BLOCKS)
            .whereEqualTo("creatorId", creatorId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                // Satu dokumen dengan `date` yang formatnya tidak terduga
                // sebelumnya melempar dari DALAM callback listener — bukan
                // sekadar membuang satu tanggal, tapi merobohkan seluruh
                // aliran ketersediaan. Kini yang tidak bisa dibaca dilewati.
                // `blocked` WAJIB diperiksa. Membuka blokir tidak menghapus
                // dokumennya — server hanya menyetel `blocked: false` (lihat
                // unblockAvailabilityDate) — jadi tanpa penyaringan ini tanggal
                // yang sudah dibuka tetap tampil tertutup selamanya, di kalender
                // creator MAUPUN di halaman booking pelanggan.
                val dates = snapshot?.documents.orEmpty()
                    .filter { it.getBoolean("blocked") != false }
                    .mapNotNull { doc ->
                        runCatching { LocalDate.parse(doc.getString("date")!!.substring(0, 10)) }.getOrNull()
                    }
                trySend(dates)
            }
        awaitClose { registration.remove() }
    }
}

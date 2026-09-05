package com.jepretaja.app.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Satu baris antrean unggah, sudah diringkas untuk ditampilkan. */
data class AntreanUnggah(
    val id: UUID,
    val state: WorkInfo.State,
    val progress: Float,
    val error: String?,
)

/**
 * Antrean unggahan.
 *
 * Semua unggahan lewat WorkManager, termasuk yang tidak dijadwalkan. Dengan
 * begitu hanya ada satu jalur yang perlu dipikirkan: kemajuan, percobaan ulang,
 * dan penjadwalan diperlakukan sama, dan menutup layar tidak pernah lagi berarti
 * kehilangan unggahan yang sedang berjalan.
 */
object UploadQueue {

    fun enqueue(
        context: Context,
        data: androidx.work.Data,
        delayMs: Long = 0L,
    ): UUID {
        val permintaan = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(data)
            .addTag(UploadWorker.TAG)
            // Menunggu jaringan, bukan langsung gagal saat sedang offline.
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .apply { if (delayMs > 0) setInitialDelay(delayMs, TimeUnit.MILLISECONDS) }
            .build()

        // Nama unik per permintaan (bukan per creator): dua unggahan berbeda
        // harus bisa mengantre bersamaan, bukan saling membatalkan.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "unggah_${permintaan.id}",
            ExistingWorkPolicy.KEEP,
            permintaan,
        )
        return permintaan.id
    }

    fun batal(context: Context, id: UUID) {
        WorkManager.getInstance(context).cancelWorkById(id)
    }

    fun stream(context: Context): Flow<List<AntreanUnggah>> =
        WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(UploadWorker.TAG)
            .map { daftar ->
                daftar
                    .filterNot { it.state == WorkInfo.State.SUCCEEDED && it.outputData.keyValueMap.isEmpty() }
                    .map { info ->
                        AntreanUnggah(
                            id = info.id,
                            state = info.state,
                            progress = info.progress.getFloat(UploadWorker.KEY_PROGRESS, 0f),
                            error = info.outputData.getString(UploadWorker.KEY_ERROR),
                        )
                    }
            }
}

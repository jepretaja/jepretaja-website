package com.jepretaja.app.data.model

import com.google.firebase.firestore.DocumentId

data class PackageModel(
    @DocumentId val packageId: String = "",
    val creatorId: String = "",
    val name: String = "",
    val price: Long = 0,
    val duration: String = "",
    val personnel: String? = null,
    val output: String? = null,
    val description: String = "",
    val active: Boolean = true,
)

/**
 * Add-on paket. Disimpan sebagai elemen array `addOns` di dalam dokumen
 * paket ({ id, name, price }), jadi TIDAK punya document id sendiri —
 * @DocumentId sengaja dihapus dari sini.
 */
data class PackageAddOnModel(
    val addOnId: String = "",
    val name: String = "",
    val price: Long = 0,
)

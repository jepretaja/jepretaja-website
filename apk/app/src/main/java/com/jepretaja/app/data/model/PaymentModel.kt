package com.jepretaja.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * Satu dokumen `payments`.
 *
 * Field transfer manual (kode unik, nominal, rekening, batas waktu) SEMUA
 * ditulis server di api/_lib/actions/appPayment.js, tapi sebelumnya tidak satu
 * pun dibaca di sini. Akibatnya layar pembayaran hanya bisa menampilkan
 * instruksi selama sesi tempat tombol "Tampilkan Cara Bayar" ditekan: begitu
 * layar ditutup dan dibuka lagi, nomor rekening, nominal, dan hitung mundurnya
 * hilang — padahal pembayarannya masih menunggu dan datanya ada di Firestore.
 */
data class PaymentModel(
    @DocumentId val paymentId: String = "",
    val bookingId: String = "",
    val customerId: String = "",
    val creatorId: String = "",
    val provider: String = "",
    val method: String = "",
    val orderId: String? = null,
    val amount: Long = 0,
    val uniqueCode: Long = 0,
    val transferAmount: Long = 0,
    val bankName: String = "",
    val bankAccountNumber: String = "",
    val bankAccountName: String = "",
    /** awaiting_transfer | paid | rejected — kosakata server. */
    val status: String = "unpaid",
    val expiresAt: Timestamp? = null,
    val paidAt: Timestamp? = null,
    val rejectedReason: String? = null,
    val createdAt: Timestamp? = null,
)

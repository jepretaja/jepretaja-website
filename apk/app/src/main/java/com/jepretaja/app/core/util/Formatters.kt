package com.jepretaja.app.core.util

import com.google.firebase.Timestamp
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Format Rupiah/tanggal — setara Formatters.dart di versi Flutter. */
object Formatters {
    private val idLocale = Locale("in", "ID")
    private val currencyFormat = NumberFormat.getCurrencyInstance(idLocale).apply { maximumFractionDigits = 0 }
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", idLocale)
    private val dateShortFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", idLocale)

    fun currency(value: Number?): String = currencyFormat.format(value?.toLong() ?: 0L)

    fun date(timestamp: Timestamp?): String {
        if (timestamp == null) return "-"
        val localDate = Instant.ofEpochMilli(timestamp.toDate().time).atZone(ZoneId.systemDefault()).toLocalDate()
        return localDate.format(dateFormatter)
    }

    fun date(localDate: LocalDate): String = localDate.format(dateFormatter)

    fun dateShort(timestamp: Timestamp?): String {
        if (timestamp == null) return "-"
        val localDate = Instant.ofEpochMilli(timestamp.toDate().time).atZone(ZoneId.systemDefault()).toLocalDate()
        return localDate.format(dateShortFormatter)
    }

    fun compactCount(value: Long): String = when {
        value >= 1_000_000 -> "%.1fjt".format(value / 1_000_000.0)
        value >= 1_000 -> "%.1frb".format(value / 1_000.0)
        else -> value.toString()
    }
}

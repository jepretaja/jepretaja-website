package com.jepretaja.app.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.jepretaja.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Klien untuk endpoint server JepretAja di Vercel (`POST /api/app`).
 *
 * Menggantikan pemanggilan Firebase Cloud Functions. Alasannya sama dengan
 * yang tercatat di api/_lib/firebaseAdmin.js: Cloud Functions mensyaratkan
 * paket Blaze (wajib kartu kredit), sementara Firestore + Authentication
 * tetap gratis di paket Spark. Seluruh logika server karena itu berjalan
 * sebagai Vercel Serverless Function.
 *
 * Memakai HttpURLConnection bawaan Android, mengikuti pola StorageService,
 * supaya tidak menambah dependensi (Retrofit/OkHttp) ke APK.
 */
@Singleton
class ApiClient @Inject constructor(
    private val auth: FirebaseAuth,
) {
    /**
     * Memanggil satu aksi di server.
     *
     * @param action nama aksi, harus cocok dengan kunci HANDLERS di api/app.js
     * @param payload isi request selain `action`
     * @return isi field `data` dari respons, sebagai Map
     * @throws ApiException bila server membalas error, dengan pesan yang
     *         sudah layak ditampilkan ke pengguna
     */
    suspend fun call(action: String, payload: Map<String, Any?> = emptyMap()): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            val base = BuildConfig.API_BASE_URL.trimEnd('/')
            if (base.isBlank()) {
                throw ApiException(
                    "config",
                    "Alamat server belum diatur. Isi API_BASE_URL di gradle.properties " +
                        "dengan domain Vercel Anda, lalu build ulang."
                )
            }

            // Token wajib: server menentukan identitas pemanggil dari token ini,
            // bukan dari isi body. Tanpa itu siapa pun bisa memesan atas nama
            // orang lain hanya dengan mengganti satu field.
            val user = auth.currentUser
                ?: throw ApiException("unauthenticated", "Anda harus masuk terlebih dahulu.")
            val token = try {
                user.getIdToken(false).await().token
            } catch (e: Exception) {
                throw ApiException("unauthenticated", "Sesi Anda berakhir. Masuk kembali untuk melanjutkan.")
            } ?: throw ApiException("unauthenticated", "Gagal memperoleh token sesi.")

            val body = JSONObject().apply {
                put("action", action)
                payload.forEach { (k, v) -> put(k, toJsonValue(v)) }
            }

            val conn = (URL("$base/api/app").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $token")
            }

            try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val kode = conn.responseCode
                val teks = (if (kode in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (teks.isBlank()) {
                    throw ApiException("internal", "Server tidak memberi balasan. Coba lagi.")
                }

                val json = JSONObject(teks)
                if (kode !in 200..299) {
                    val err = json.optJSONObject("error")
                    throw ApiException(
                        err?.optString("code") ?: "internal",
                        err?.optString("message")?.takeIf { it.isNotBlank() }
                            ?: "Terjadi kesalahan di server ($kode)."
                    )
                }
                toMap(json.optJSONObject("data") ?: JSONObject())
            } catch (e: ApiException) {
                throw e
            } catch (e: java.net.SocketTimeoutException) {
                // Dibedakan dari kegagalan koneksi biasa: server terjangkau tapi
                // lambat menjawab, jadi menyuruh pengguna memeriksa internetnya
                // hanya menyesatkan.
                throw ApiException("timeout", "Server terlalu lama menjawab. Coba lagi sebentar lagi.")
            } catch (e: java.net.UnknownHostException) {
                throw ApiException("network", "Tidak ada koneksi internet. Periksa jaringan Anda lalu coba lagi.")
            } catch (e: IOException) {
                throw ApiException("network", "Tidak dapat terhubung ke server. Periksa koneksi internet Anda.")
            } finally {
                conn.disconnect()
            }
        }

    private fun toJsonValue(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is List<*> -> JSONArray().apply { v.forEach { put(toJsonValue(it)) } }
        is Map<*, *> -> JSONObject().apply { v.forEach { (k, value) -> put(k.toString(), toJsonValue(value)) } }
        else -> v
    }

    private fun toMap(obj: JSONObject): Map<String, Any?> =
        obj.keys().asSequence().associateWith { key ->
            when (val value = obj.get(key)) {
                JSONObject.NULL -> null
                is JSONObject -> toMap(value)
                is JSONArray -> (0 until value.length()).map { i ->
                    when (val item = value.get(i)) {
                        is JSONObject -> toMap(item)
                        JSONObject.NULL -> null
                        else -> item
                    }
                }
                else -> value
            }
        }
}

/** Error dari server yang pesannya sudah aman ditampilkan ke pengguna. */
class ApiException(val code: String, message: String) : Exception(message)

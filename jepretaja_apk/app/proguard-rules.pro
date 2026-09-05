# Model data DIPERTAHANKAN UTUH.
#
# Firestore mengisi objek lewat refleksi (toObject / @DocumentId), dan R8 pada
# build rilis mengganti nama field menjadi a, b, c. Begitu nama berubah, tidak
# ada lagi field yang cocok dengan dokumen di database: pemetaan diam-diam
# menghasilkan objek kosong, bukan error. Gejalanya paling menyesatkan —
# aplikasi berjalan normal saat debug lalu tampil serba kosong setelah dirilis.
-keep class com.jepretaja.app.data.model.** { *; }
-keepclassmembers class com.jepretaja.app.data.model.** {
    <init>();
    <fields>;
}

-keepattributes Signature
-keepattributes *Annotation*
# Dibutuhkan Firestore untuk membaca tipe generik seperti List<String> dan
# Map<String, Long> pada model.
-keepattributes InnerClasses, EnclosingMethod

# --- Firebase / Google Play Services ---
-keepnames class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# --- Kotlin coroutines ---
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- Media3 / ExoPlayer ---
# Komponen dimuat lewat refleksi berdasarkan nama kelas.
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }

# Baris log dibuang dari rilis: sebagian berisi id booking dan email pengguna
# yang tidak sepatutnya tersimpan di logcat perangkat.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

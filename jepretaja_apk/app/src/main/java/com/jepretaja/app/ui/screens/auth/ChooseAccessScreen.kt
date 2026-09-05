package com.jepretaja.app.ui.screens.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import com.jepretaja.app.R
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.premiumShadow
import com.jepretaja.app.ui.state.AuthActionsViewModel

/** Choose Access (section 4.1): Masuk, Daftar Konsumen, Daftar Creator,
 * Google Sign-In, atau Lanjut sebagai Tamu. */
@Composable
fun ChooseAccessScreen(
    onLogin: () -> Unit,
    onRegisterCustomer: () -> Unit,
    onRegisterCreator: () -> Unit,
    onContinueAsGuest: () -> Unit,
    // Dipisah dari onContinueAsGuest: keduanya sama-sama menuju Home, tapi
    // maknanya bertolak belakang. Login Google menghasilkan akun sungguhan,
    // sedangkan "lanjut sebagai tamu" menyimpan penanda tamu — memakai callback
    // yang sama membuat pengguna yang baru saja login ikut ditandai tamu.
    onGoogleSignedIn: () -> Unit = onContinueAsGuest,
    viewModel: AuthActionsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var googleLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Web Client ID otomatis tersedia dari google-services.json setelah
    // `flutterfire`-setara untuk Kotlin (google-services plugin) memproses
    // file itu — string resource `default_web_client_id` di-generate otomatis.
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    }
    val googleClient = remember { GoogleSignIn.getClient(context, gso) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            googleLoading = false
            // Sebelumnya kembali diam-diam tanpa pesan apa pun. Google
            // mengembalikan RESULT_CANCELED baik saat pengguna sengaja
            // membatalkan MAUPUN saat konfigurasi salah — akibatnya
            // kegagalan konfigurasi tampak seperti "tombol tidak berfungsi".
            error = "Login Google dibatalkan atau gagal. " +
                "Bila ini terjadi terus-menerus, sidik jari SHA-1 aplikasi " +
                "kemungkinan belum didaftarkan di Firebase Console."
            return@rememberLauncherForActivityResult
        }
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken == null) {
                googleLoading = false
                error = "Gagal mendapatkan token Google."
                return@rememberLauncherForActivityResult
            }
            viewModel.signInWithGoogle(
                idToken = idToken,
                onSuccess = { googleLoading = false; onGoogleSignedIn() },
                onError = { googleLoading = false; error = it },
            )
        } catch (e: ApiException) {
            googleLoading = false
            // statusCode 10 = DEVELOPER_ERROR, dan hampir selalu berarti
            // SHA-1 perangkat ini belum terdaftar di Firebase Console.
            // Pesan bawaannya ("10: ") tidak memberi petunjuk apa pun,
            // jadi diterjemahkan ke penyebab yang bisa ditindaklanjuti.
            error = when (e.statusCode) {
                CommonStatusCodes.DEVELOPER_ERROR ->
                    "Konfigurasi Google Sign-In belum lengkap. Daftarkan sidik jari " +
                    "SHA-1 aplikasi ini di Firebase Console (Project Settings > " +
                    "aplikasi Android > Add fingerprint), lalu build ulang."
                CommonStatusCodes.NETWORK_ERROR ->
                    "Tidak ada koneksi internet."
                else -> "Google Sign-In gagal (kode ${e.statusCode})."
            }
        }
    }

    // Tata letak sengaja dibagi dua bagian: panggung brand di atas (mengambil
    // porsi layar yang jelas, bukan ikon mengambang di ruang kosong) dan
    // kelompok aksi di bawah.
    //
    // Versi sebelumnya menumpuk empat tombol berukuran & bobot identik
    // (Masuk, Google, Daftar Konsumen, Daftar Creator). Tanpa hierarki, mata
    // tidak tahu mana yang utama dan layar terasa seperti daftar menu mentah.
    // Sekarang: satu aksi utama (Masuk), satu alternatif (Google), lalu
    // pendaftaran dipisahkan di bawah pembatas sebagai kelompok tersendiri.
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    Brush.verticalGradient(
                        listOf(AppColors.PrimarySoft, AppColors.Background),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Lambang brand: lingkaran gradient dengan shadow berwarna,
                // menyambung dengan logo yang baru saja dilihat di Splash
                // supaya perpindahannya terasa satu rangkaian, bukan dua layar
                // yang kebetulan berurutan.
                Box(
                    Modifier
                        .size(88.dp)
                        .premiumShadow(16.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(AppColors.Primary, AppColors.PrimaryDark))
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = AppColors.OnPrimary,
                        modifier = Modifier.size(42.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text("JepretAja", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Temukan & booking fotografer terbaik di sekitarmu",
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }
        }

        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = onLogin,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    contentColor = AppColors.OnPrimary,
                ),
            ) { Text("Masuk", style = MaterialTheme.typography.titleSmall) }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { googleLoading = true; error = null; launcher.launch(googleClient.signInIntent) },
                enabled = !googleLoading,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (googleLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Memproses...")
                } else {
                    Text("Lanjutkan dengan Google")
                }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = AppColors.Danger, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }

            // Pembatas bertuliskan — memisahkan "sudah punya akun" dari
            // "buat akun baru", sehingga empat tombol tadi tidak lagi
            // terbaca sebagai satu tumpukan yang setara.
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f), color = AppColors.Border)
                Text(
                    "  Belum punya akun?  ",
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider(Modifier.weight(1f), color = AppColors.Border)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onRegisterCustomer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                    modifier = Modifier.weight(1f).height(46.dp),
                ) { Text("Konsumen", style = MaterialTheme.typography.labelLarge) }
                OutlinedButton(
                    onClick = onRegisterCreator,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                    modifier = Modifier.weight(1f).height(46.dp),
                ) { Text("Creator", style = MaterialTheme.typography.labelLarge) }
            }

            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onContinueAsGuest) {
                Text("Lanjut sebagai Tamu", color = AppColors.TextSecondary)
            }
        }
    }
}

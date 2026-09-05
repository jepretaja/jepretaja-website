package com.jepretaja.app.ui.screens.auth

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.BigPrimaryButton
import com.jepretaja.app.ui.state.AuthActionsViewModel

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: AuthActionsViewModel = hiltViewModel(),
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Dialog "Lupa password?" — lihat komentar di tombolnya soal kenapa alur
    // ini perlu layar sendiri, bukan sekadar satu panggilan diam-diam.
    var showReset by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var resetLoading by remember { mutableStateOf(false) }
    var resetError by remember { mutableStateOf<String?>(null) }
    var pesan by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pesan) {
        pesan?.let {
            snackbarHostState.showSnackbar(it)
            pesan = null
        }
    }

    Scaffold(
        containerColor = AppColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 24.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(12.dp))
            Text("Selamat Datang", style = MaterialTheme.typography.displaySmall, color = AppColors.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Masuk untuk melanjutkan perjalanan visual kamu bersama para creator terbaik.",
                style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary,
            )
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                email, { email = it }, label = { Text("Email") }, singleLine = true,
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                password, { password = it }, label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = AppColors.Danger, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(28.dp))
            BigPrimaryButton(
                text = if (loading) "Memproses..." else "Masuk",
                loading = loading,
                enabled = !loading,
                onClick = {
                    loading = true; error = null
                    viewModel.login(email.trim(), password, onSuccess = { loading = false; onLoginSuccess() }, onError = { loading = false; error = it })
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                // Dulu: kalau kolom email kosong tombol ini TIDAK melakukan
                // apa pun, dan kalau terisi hasilnya ditelan diam-diam tanpa
                // pesan berhasil maupun gagal. Dari sisi pengguna, tombolnya
                // rusak. Sekarang selalu membuka dialog: emailnya bisa diisi di
                // sana, formatnya diperiksa, dan hasilnya selalu dilaporkan.
                TextButton(
                    onClick = {
                        resetEmail = email.trim()
                        resetError = null
                        showReset = true
                    },
                    shape = RoundedCornerShape(percent = 50),
                ) {
                    Text("Lupa password?", color = AppColors.Primary)
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (showReset) {
            AlertDialog(
                onDismissRequest = { if (!resetLoading) showReset = false },
                title = { Text("Lupa password?") },
                text = {
                    Column {
                        Text(
                            "Masukkan email akunmu. Kami kirim tautan untuk membuat password baru.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary,
                        )
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            resetEmail,
                            { resetEmail = it; resetError = null },
                            label = { Text("Email") },
                            singleLine = true,
                            isError = resetError != null,
                            enabled = !resetLoading,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        resetError?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, color = AppColors.Danger, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !resetLoading,
                        onClick = {
                            val tujuan = resetEmail.trim()
                            // Diperiksa di sini supaya kesalahan ketik terjawab
                            // langsung, bukan setelah menunggu balasan server.
                            if (!Patterns.EMAIL_ADDRESS.matcher(tujuan).matches()) {
                                resetError = "Format email tidak valid."
                                return@TextButton
                            }
                            resetLoading = true
                            resetError = null
                            viewModel.resetPassword(
                                tujuan,
                                onSuccess = {
                                    resetLoading = false
                                    showReset = false
                                    pesan = "Tautan reset dikirim ke $tujuan. Cek inbox dan folder spam."
                                },
                                onError = {
                                    resetLoading = false
                                    resetError = it
                                },
                            )
                        },
                    ) { Text(if (resetLoading) "Mengirim..." else "Kirim Tautan") }
                },
                dismissButton = {
                    TextButton(enabled = !resetLoading, onClick = { showReset = false }) { Text("Batal") }
                },
            )
        }
    }
}

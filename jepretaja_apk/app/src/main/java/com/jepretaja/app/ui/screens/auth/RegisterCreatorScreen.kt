package com.jepretaja.app.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.BigPrimaryButton
import com.jepretaja.app.ui.components.LocationField
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.state.AuthActionsViewModel

/** Setelah register, creator berstatus verified=false sampai dokumen
 * diverifikasi Admin (section 8, 14, 20). */
@Composable
fun RegisterCreatorScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: AuthActionsViewModel = hiltViewModel(),
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = AppColors.Background,
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
            Text("Bergabung Sebagai Creator", style = MaterialTheme.typography.displaySmall, color = AppColors.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Tampilkan portofoliomu dan mulai menerima booking dari klien di kotamu.",
                style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary,
            )
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Nama / Nama Studio") }, singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(phone, { phone = it }, label = { Text("No. HP") }, singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            // Kota domisili menentukan apakah creator ini muncul di "Creator
            // Terdekat", jadi salah ketik di sini berarti kehilangan pelanggan
            // tanpa pernah tahu sebabnya — layak diisi otomatis.
            LocationField(
                value = city,
                onValueChange = { city = it },
                label = "Kota Domisili",
                placeholder = "Ketik kota, atau ambil otomatis",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                password, { password = it }, label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 2.dp, color = AppColors.PrimarySoft) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Setelah daftar, lengkapi dokumen verifikasi di Creator Settings agar akun terverifikasi.",
                        color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = AppColors.Danger, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(28.dp))
            BigPrimaryButton(
                text = if (loading) "Memproses..." else "Daftar sebagai Creator",
                loading = loading,
                enabled = !loading,
                onClick = {
                    loading = true; error = null
                    viewModel.registerCreator(name.trim(), email.trim(), password, city.trim(), phone.trim(), onSuccess = { loading = false; onSuccess() }, onError = { loading = false; error = it })
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

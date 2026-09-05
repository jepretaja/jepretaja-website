package com.jepretaja.app.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.BigPrimaryButton
import com.jepretaja.app.ui.state.AuthActionsViewModel

@Composable
fun RegisterCustomerScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: AuthActionsViewModel = hiltViewModel(),
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
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
            Text("Buat Akun", style = MaterialTheme.typography.displaySmall, color = AppColors.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Daftar sebagai konsumen untuk mulai memesan sesi foto & video pilihanmu.",
                style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary,
            )
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Nama Lengkap") }, singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(phone, { phone = it }, label = { Text("No. HP") }, singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                password, { password = it }, label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = AppColors.Danger, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(28.dp))
            BigPrimaryButton(
                text = if (loading) "Memproses..." else "Daftar",
                loading = loading,
                enabled = !loading,
                onClick = {
                    loading = true; error = null
                    viewModel.registerCustomer(name.trim(), email.trim(), password, phone.trim(), onSuccess = { loading = false; onSuccess() }, onError = { loading = false; error = it })
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

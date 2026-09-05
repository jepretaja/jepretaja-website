package com.jepretaja.app.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.AppAvatar
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.BigPrimaryButton
import com.jepretaja.app.ui.components.SectionHeader
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel

/**
 * Edit profil untuk SEMUA pengguna.
 *
 * Sebelumnya hanya creator yang punya jalan mengubah profilnya (lewat Creator
 * Settings); konsumen sama sekali tidak bisa memperbaiki nama, nomor telepon,
 * atau memasang foto — padahal nama itulah yang dilihat creator saat menerima
 * booking dan membalas chat.
 */
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val profile = authState.profile

    var name by remember(profile?.userId) { mutableStateOf(profile?.name ?: "") }
    var phone by remember(profile?.userId) { mutableStateOf(profile?.phone ?: "") }
    var pickedPhoto by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pickedPhoto = uri
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            message = null
        }
    }

    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { AppTopBar(title = "Edit Profil", onBack = onBack, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.align(Alignment.CenterHorizontally).clickable { photoPicker.launch("image/*") }) {
                if (pickedPhoto != null) {
                    AsyncImage(
                        model = pickedPhoto, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(CircleShape),
                    )
                } else {
                    AppAvatar(url = profile?.photoUrl, name = profile?.name ?: "?", size = 96.dp)
                }
                Icon(
                    Icons.Default.PhotoCamera, contentDescription = "Ganti foto",
                    tint = AppColors.OnPrimary,
                    modifier = Modifier.align(Alignment.BottomEnd).size(30.dp)
                        .clip(CircleShape).background(AppColors.Primary).padding(6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Ketuk foto untuk mengganti",
                style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(28.dp))
            SectionHeader("Data Diri")
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                OutlinedTextField(
                    name, { name = it }, label = { Text("Nama") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    phone, { phone = it }, label = { Text("Nomor telepon") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                // Email sengaja hanya ditampilkan, tidak bisa diubah: mengganti
                // email adalah operasi Firebase Auth tersendiri yang butuh
                // verifikasi ulang, bukan sekadar menulis field di Firestore.
                OutlinedTextField(
                    value = profile?.email ?: "-", onValueChange = {}, enabled = false,
                    label = { Text("Email (tidak bisa diubah)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(28.dp))
                BigPrimaryButton(
                    text = "Simpan Perubahan",
                    loading = saving,
                    enabled = name.isNotBlank(),
                    onClick = {
                        saving = true
                        viewModel.save(
                            name = name.trim(),
                            phone = phone.trim().ifBlank { null },
                            photoUri = pickedPhoto,
                            userId = authState.uid,
                        ) { photoUrl, error ->
                            if (error != null) {
                                saving = false
                                message = error
                            } else {
                                authViewModel.updateProfile(name.trim(), phone.trim().ifBlank { null }, photoUrl) { saveError ->
                                    saving = false
                                    message = saveError ?: "Profil tersimpan"
                                    if (saveError == null) pickedPhoto = null
                                }
                            }
                        }
                    },
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

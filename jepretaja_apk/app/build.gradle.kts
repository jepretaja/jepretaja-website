// Import eksplisit: di dalam blok `android { }`, nama `java` merujuk ke
// ekstensi Gradle, bukan package java.* — tanpa import ini `java.util.Properties`
// gagal di-resolve.
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    // Tanpa ini, FirebaseCrashlytics.getInstance() di JepretAjaApplication.onCreate()
    // crash saat app dibuka dengan error "Crashlytics build ID is missing" —
    // plugin ini yang menyuntikkan resource build-ID yang dibutuhkan SDK saat
    // runtime, dependency firebase-crashlytics-ktx saja tidak cukup.
    id("com.google.firebase.crashlytics")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Konfigurasi Cloudinary (pengganti Firebase Storage yang sejak Feb 2026 wajib
// Blaze). Nilainya diambil dari gradle.properties saat build lokal, atau dari
// environment variable saat build di GitHub Actions.
// Keduanya BUKAN rahasia: cloud name dan nama unsigned preset memang terlihat
// dari sisi klien. Yang tidak boleh masuk APK adalah API secret Cloudinary.
val cloudinaryCloudName: String =
    (project.findProperty("CLOUDINARY_CLOUD_NAME") as String?)
        ?: System.getenv("CLOUDINARY_CLOUD_NAME") ?: ""
val cloudinaryUploadPreset: String =
    (project.findProperty("CLOUDINARY_UPLOAD_PRESET") as String?)
        ?: System.getenv("CLOUDINARY_UPLOAD_PRESET") ?: ""

// Alamat panel web di Vercel — di situlah endpoint server (/api/app)
// berjalan. Bukan rahasia: endpoint-nya memang publik dan dilindungi oleh
// verifikasi Firebase ID token di sisi server, bukan oleh kerahasiaan URL.
val apiBaseUrl: String =
    (project.findProperty("API_BASE_URL") as String?)
        ?: System.getenv("API_BASE_URL") ?: ""

android {
    namespace = "com.jepretaja.app"
    compileSdk = 35

    defaultConfig {
        // Sama persis dengan project Flutter sebelumnya — supaya google-services.json
        // yang sudah ada di Firebase Console tetap cocok, tidak perlu daftar app baru.
        applicationId = "com.jepretaja.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"$cloudinaryCloudName\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"$cloudinaryUploadPreset\"")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    // Kunci penandatanganan dibaca dari keystore.properties yang TIDAK ikut
    // masuk git (lihat .gitignore). Google Play menolak APK/AAB yang tidak
    // ditandatangani kunci rilis, dan kunci itu tidak boleh pernah tersimpan di
    // repositori: kehilangan atau kebocorannya berarti tidak ada lagi yang bisa
    // menerbitkan pembaruan untuk aplikasi ini.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Hanya dipasang bila keystore.properties benar-benar ada; tanpa itu
            // build release tetap jalan (menghasilkan berkas tak bertanda tangan)
            // daripada gagal total di mesin yang memang belum punya kuncinya.
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Banyak layar memakai TopAppBar, ModalBottomSheet, dll dari Material3
        // yang masih ditandai @RequiresOptIn. Tanpa opt-in project-wide ini,
        // Kotlin memperlakukan setiap pemakaiannya sebagai compile ERROR
        // (bukan cuma warning) — itu sumber mayoritas error "This material
        // API is experimental" di puluhan file.
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }

    buildFeatures {
        compose = true
        // Wajib di AGP 8: tanpa ini buildConfigField di atas tidak menghasilkan
        // kelas BuildConfig dan StorageService gagal dikompilasi.
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // --- Compose ---
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    // --- Navigation ---
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // --- Hilt (Dependency Injection) ---
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")

    // --- Firebase (BoM: satu versi konsisten untuk semua library Firebase) ---
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    // firebase-functions-ktx dihapus: seluruh logika server kini dipanggil
    // lewat ApiClient ke Vercel (/api/app), bukan Cloud Functions callable.
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    // debugImplementation, BUKAN implementation: penyedia debug App Check
    // sebelumnya ikut terbawa ke APK rilis. Ia memang hanya dipasang saat
    // BuildConfig.DEBUG, tapi mengirim kelas penerobos App Check ke aplikasi
    // yang dipublikasikan tidak ada gunanya dan hanya menambah permukaan risiko.
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.android.gms:play-services-auth:21.2.0") // Google Sign-In

    // --- Async ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1") // .await() utk Task Firebase

    // --- Image loading (setara cached_network_image di Flutter) ---
    implementation("io.coil-kt:coil-compose:2.7.0")

    // QR profil. zxing-core adalah pustaka Java murni tanpa dependensi Android,
    // dipakai hanya untuk menghasilkan matriks QR — bitmap-nya digambar sendiri
    // supaya tidak perlu menarik zxing-android-embedded beserta Activity dan
    // izin kameranya, yang sama sekali tidak dibutuhkan untuk menampilkan kode.
    implementation("com.google.zxing:core:3.5.3")

    // --- Lokasi (setara geolocator) ---
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // --- Media / video ---
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    // Transformer dipakai untuk memotong video di perangkat sebelum diunggah.
    // Versinya WAJIB sama dengan media3 lain — mencampur versi media3 memicu
    // NoSuchMethodError saat runtime, bukan error saat kompilasi.
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // --- Kamera dalam aplikasi ---
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-video:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // --- Unggahan latar belakang ---
    // WorkManager, bukan coroutine biasa: unggahan harus selamat dari layar yang
    // ditutup dan dari proses aplikasi yang dimatikan sistem saat pengguna
    // pindah aplikasi — dua hal yang tidak bisa dijamin viewModelScope.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // --- Local notification handling ---
    implementation("androidx.core:core-splashscreen:1.0.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

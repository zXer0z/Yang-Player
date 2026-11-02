import java.security.KeyStore
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun debugCertificateSha256(): String {
    val certificateFile = File(System.getProperty("user.home"), ".android/debug.keystore")
    require(certificateFile.isFile) { "Create the debug keystore first: tools/Prepare-DebugSigning.ps1 (see README)." }
    val keys = KeyStore.getInstance(KeyStore.getDefaultType())
    certificateFile.inputStream().use { keys.load(it, "android".toCharArray()) }
    return MessageDigest.getInstance("SHA-256").digest(keys.getCertificate("androiddebugkey").encoded)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

android {
    namespace = "com.yang.player"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    val configuredNdk = providers.gradleProperty("ndkPath").orNull
    if (configuredNdk != null) ndkPath = configuredNdk
    else rootProject.file(".tools/android-ndk-r27c").takeIf { it.isDirectory }?.let { ndkPath = it.absolutePath }
    ndkVersion = "27.2.12479018"
    externalNativeBuild { ndkBuild { path = file("src/main/cpp/Android.mk") } }

    defaultConfig {
        applicationId = "com.yang.player"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        val yinCert = providers.gradleProperty("yinCertSha256").orNull ?: debugCertificateSha256()
        require(yinCert.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid yinCertSha256" }
        buildConfigField("String", "YIN_CERT_SHA256", "\"${yinCert.lowercase()}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            // Support modern ABIs for native audio rendering & FFmpeg
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
        externalNativeBuild { ndkBuild { arguments += "APP_SUPPORT_FLEXIBLE_PAGE_SIZES=true" } }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("main").java.srcDir(rootProject.file("shared/yin-bridge/java"))

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // NewPipe uses Rhino directly; its desktop JSR-223 adapter is unnecessary on Android.
    implementation(fileTree("libs") { include("*.jar"); exclude("rhino-engine-*.jar") })
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Media3 (ExoPlayer with audiophile 32-bit float audio sink & extension preference)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // Coil for async image loading (local album art + YouTube avatar/thumbnails)
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation("junit:junit:4.13.2")
}


plugins {
    alias(libs.plugins.kotlin.android)
    id("com.android.library") version "8.5.0"
}

android {
    namespace = "com.tele.app.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // api: el motor expone tipos de libtorrent4j a :tv a traves del flujo de estado
    api(libs.libtorrent4j)
    // Binarios nativos: TV reales (arm64-v8a) y emulador (x86_64)
    runtimeOnly(libs.libtorrent4jNativeArm64)
    runtimeOnly(libs.libtorrent4jNativeX64)
    implementation(libs.kotlinx.coroutines.android)
}

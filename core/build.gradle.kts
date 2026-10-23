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
    implementation(libs.libtorrent4j)
    implementation(libs.kotlinx.coroutines.android)
}

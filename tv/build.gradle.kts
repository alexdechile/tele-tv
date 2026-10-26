plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("com.android.application") version "8.5.0"
}

android {
    namespace = "com.tele.app.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tele.app.tv"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)

    implementation(libs.compose.ui)
    implementation(libs.compose.material)
    implementation(libs.compose.tv.material)
    implementation(libs.coil.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    annotationProcessor(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)

    implementation(libs.kotlinx.coroutines.android)

    implementation(project(":core"))
    implementation(project(":data"))
}

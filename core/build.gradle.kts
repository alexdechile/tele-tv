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

    testImplementation(libs.junit)
}

// En la JVM de los tests usamos el binario nativo de Linux para ejercitar
// libtorrent4j de verdad (las .so de Android son bionic y no cargan aca).
val linuxNative: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false // solo el jar del binario, no sus dependencias
}

dependencies {
    add(linuxNative.name, libs.libtorrent4jNativeLinux)
}

val nativeTestDir = layout.buildDirectory.dir("native-test")

// Extrae lib/x86_64/libtorrent4j.so del jar de Linux para que la JVM lo cargue.
val extractLinuxNative = tasks.register<Copy>("extractLinuxNative") {
    from({
        val jar = linuxNative.files.first { it.name.startsWith("libtorrent4j-linux") }
        zipTree(jar)
    }) {
        include("lib/x86_64/*.so")
    }
    into(nativeTestDir)
}

tasks.withType<Test>().configureEach {
    dependsOn(extractLinuxNative)
    systemProperty(
        "java.library.path",
        nativeTestDir.get().dir("lib/x86_64").asFile.absolutePath
    )
}

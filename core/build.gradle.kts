plugins {
    alias(libs.plugins.kotlin.android)
    id("com.android.library") version "8.5.0"
}

android {
    namespace = "com.tele.app.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
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
    // api: el motor expone tipos de jlibtorrent a :tv a traves del flujo de estado.
    // Se usa el AAR de Android, que es autosuficiente: trae classes.jar (las clases
    // Java completas) + libjlibtorrent.so para arm64-v8a, armeabi-v7a, x86 y x86_64.
    // NO se agrega el jar `jlibtorrent` por separado: al mezclarlo con el AAR la
    // tarea de ensamblado falla con "Duplicate class com.frostwire.jlibtorrent.swig.*".
    api(libs.jlibtorrentAndroid)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}

// En la JVM de los tests usamos el binario nativo de Linux para ejercitar
// jlibtorrent de verdad (las .so de Android son bionic y no cargan aca).
val linuxNative: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false // solo el jar del binario, no sus dependencias
}

dependencies {
    // El AAR no incluye linux; usamos el jar nativo linux separado para tests JVM
    add(linuxNative.name, "com.frostwire:jlibtorrent-linux-x86_64:2.0.15.3")
}

val nativeTestDir = layout.buildDirectory.dir("native-test")

// Extrae lib/x86_64/libjlibtorrent.so del jar de Linux para que la JVM lo cargue.
val extractLinuxNative = tasks.register<Copy>("extractLinuxNative") {
    from({
        val jar = linuxNative.files.first { it.name.startsWith("jlibtorrent-linux") }
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

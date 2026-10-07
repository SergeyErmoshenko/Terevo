import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

group = "me.terevo"
version = providers.gradleProperty("version").getOrElse("1.0.1")

val graphvizVersion = "12.2.1"
val graphvizWindowsUrl =
    "https://gitlab.com/api/v4/projects/4207231/packages/generic/graphviz-releases/$graphvizVersion/" +
        "windows_10_cmake_Release_Graphviz-$graphvizVersion-win64.zip"
val graphvizWindowsSha256 = "82c34e6a73b8158bee357d1c51aac08925edc8f4a9f517f591fb8df989405ad4"

fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { stream ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val graphvizWindowsZip = layout.buildDirectory.file("graphviz/Graphviz-$graphvizVersion-win64.zip")

val downloadGraphvizWindows = tasks.register("downloadGraphvizWindows") {
    description = "Downloads the Graphviz $graphvizVersion Windows zip from the official release."
    outputs.file(graphvizWindowsZip)
    onlyIf { OperatingSystem.current().isWindows }
    doLast {
        val dest = graphvizWindowsZip.get().asFile
        dest.parentFile.mkdirs()
        if (!dest.exists()) {
            URI.create(graphvizWindowsUrl).toURL().openStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val actual = sha256Of(dest)
        check(actual == graphvizWindowsSha256) {
            "Graphviz $graphvizVersion checksum mismatch: got $actual, expected $graphvizWindowsSha256"
        }
    }
}

val graphvizWindows = tasks.register<Sync>("graphvizWindows") {
    description = "Unpacks the Graphviz windows build that ships inside the Terevo installer."
    dependsOn(downloadGraphvizWindows)
    onlyIf { OperatingSystem.current().isWindows }
    into(layout.buildDirectory.dir("graphviz/windows"))
    from(zipTree(graphvizWindowsZip)) {
        include("Graphviz-*-win64/bin/**")
        include("Graphviz-*-win64/share/doc/graphviz/COPYING")
        eachFile { path = path.substringAfter("/") }
        includeEmptyDirs = false
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict")
        allWarningsAsErrors.set(providers.environmentVariable("CI").map { true }.orElse(false))
    }
}

configurations.all {
    resolutionStrategy {
        // Transitive-only artifact (konsist -> kotlin-stdlib-jdk8 -> kotlin-stdlib-jdk7); nothing
        // in this project calls into it directly. Removing the Compose plugin changed conflict
        // resolution enough that Gradle now wants exactly 2.0.21, which happens not to be cached
        // here. Pin to the closest cached release instead of forcing a network fetch.
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.21")
    }
}

dependencies {
    implementation(libs.bundles.kotlinx)
    implementation(libs.bundles.sqldelight)
    implementation(libs.pdfbox)
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junit.platform.launcher)
}

sqldelight {
    databases {
        create("TerevoDatabase") {
            packageName.set("me.terevo.persistence.db")
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

application {
    mainClass = "me.terevo.server.ServerMainKt"
    applicationName = "terevo-backend"
}

// electron-builder stages this distribution as an extra resource for macOS and Windows.
tasks.named("installDist") {
    dependsOn(tasks.jar)
}

tasks.register("desktopDistribution") {
    group = "distribution"
    description = "Builds the Kotlin backend distribution used by the Electron desktop app."
    dependsOn("installDist")
}

val sqliteNativeDir: File = layout.buildDirectory.dir("tmp/sqlite-native").get().asFile

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2g"
    systemProperty("org.sqlite.tmpdir", sqliteNativeDir.absolutePath)
    systemProperty("java.awt.headless", "true")
    systemProperty("java.io.tmpdir", layout.buildDirectory.dir("tmp/test").get().asFile.absolutePath)
    doFirst {
        sqliteNativeDir.mkdirs()
        layout.buildDirectory.dir("tmp/test").get().asFile.mkdirs()
    }
}

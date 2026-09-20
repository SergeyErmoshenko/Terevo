import org.gradle.internal.os.OperatingSystem
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.sqldelight)
}

group = "me.terevo"
version = "0.1.0"

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

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.material.kolor)
    implementation(libs.tabler.icons)

    implementation(libs.bundles.kotlinx)
    implementation(libs.kotlinx.coroutines.swing)
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

compose.desktop {
    application {
        mainClass = "me.terevo.app.MainKt"
        if (OperatingSystem.current().isMacOsX) {
            jvmArgs += listOf(
                "-Xdock:name=Terevo",
                "-Xdock:icon=${rootProject.file("packaging/icons/icon.icns")}",
                "-Dapple.awt.application.name=Terevo",
            )
        }
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "Terevo"
            packageVersion = "1.0.0"
            description = "Genealogy tree editor"
            vendor = "Terevo"
            fileAssociation(
                mimeType = "application/x-terevo",
                extension = "terevo",
                description = "Terevo genealogy project",
            )
            windows {
                iconFile.set(rootProject.file("packaging/icons/icon.ico"))
            }
            macOS {
                iconFile.set(rootProject.file("packaging/icons/icon.icns"))
                bundleID = "me.terevo.app"
            }
            linux {
                iconFile.set(rootProject.file("packaging/icons/icon.png"))
            }
        }
    }
}

val sqliteNativeDir: File = layout.buildDirectory.dir("tmp/sqlite-native").get().asFile

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2g"
    systemProperty("org.sqlite.tmpdir", sqliteNativeDir.absolutePath)
    systemProperty("java.awt.headless", "true")
    doFirst {
        sqliteNativeDir.mkdirs()
    }
}

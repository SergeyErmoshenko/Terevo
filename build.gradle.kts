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
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict")
        allWarningsAsErrors.set(providers.environmentVariable("CI").map { true }.orElse(false))
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

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
            verifyMigrations.set(true)
        }
    }
}

compose.desktop {
    application {
        mainClass = "me.terevo.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "Terevo"
            packageVersion = "1.0.0"
            description = "Genealogy tree editor"
            vendor = "Terevo"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

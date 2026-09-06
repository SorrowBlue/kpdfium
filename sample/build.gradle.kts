import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatformLibrary)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("kioarch.detekt")
    id("kpdfium.lint")
}

kotlin {
    android {
        namespace = "com.sorrowblue.kpdfium.sample"
    }

    jvm()

    val xcframeworkName = "ComposeApp"
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = xcframeworkName
            isStatic = false
            export(projects.kpdfium)
        }
    }


    jvmToolchain {
        vendor.set(JvmVendorSpec.ADOPTIUM)
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.kpdfium)

                implementation(libs.compose.runtime)
                implementation(libs.compose.material3)
                implementation(libs.compose.uiToolingPreview)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.io.okio)
                implementation(libs.kotlinx.serializationJson)

                implementation(libs.coil3.compose)
                implementation(libs.filekit.dialogsCompose)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.activity.compose)
                implementation("androidx.documentfile:documentfile:1.1.0")
            }
        }

        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.sorrowblue.kpdfium.sample.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "kpdfium-sample"
            packageVersion = "1.0.0"
        }
    }
}


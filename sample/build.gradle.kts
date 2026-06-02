import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatformLibrary)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("kioarch.detekt")
}

kotlin {
    android {
        namespace = "com.sorrowblue.kpdfium.sample"
    }

    jvm()

    val jniLibsDir = project(":kpdfium").layout.projectDirectory.dir("src/cpp/pdfium")

    val xcframeworkName = "ComposeApp"
    val xcf = XCFramework(xcframeworkName)
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = xcframeworkName
            isStatic = false
            export(projects.kpdfium)

            val platformClassifier = when (iosTarget.name) {
                "iosArm64" -> "ios-device-arm64"
                "iosSimulatorArm64" -> "ios-simulator-arm64"
                else -> null
            }
            if (platformClassifier != null) {
                val libDir = jniLibsDir.dir(platformClassifier)
                linkerOpts("-L${libDir.asFile.absolutePath}", "-lpdfium")
            }
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

tasks.register("embedAndSignPdfium") {
    val builtProductsDir = System.getenv("BUILT_PRODUCTS_DIR")
    val contentsFolderPath = System.getenv("CONTENTS_FOLDER_PATH")
    val codesignIdentity = System.getenv("EXPANDED_CODE_SIGN_IDENTITY") ?: "-"

    if (builtProductsDir != null) {
        val platformClassifier = when (System.getenv("PLATFORM_NAME")) {
            "iphoneos" -> "ios-device-arm64"
            "iphonesimulator" -> "ios-simulator-arm64"
            else -> null
        }
        if (platformClassifier != null) {
            val jniLibsDir = project(":kpdfium").layout.projectDirectory.dir("src/cpp/pdfium")
            val dylibFile = jniLibsDir.dir(platformClassifier).file("libpdfium.dylib").asFile
            if (dylibFile.exists()) {
                val targetDir = file(builtProductsDir)
                val appDir = targetDir.resolve(contentsFolderPath ?: "")

                inputs.file(dylibFile)
                outputs.file(targetDir.resolve("libpdfium.dylib"))
                if (contentsFolderPath != null) {
                    outputs.file(appDir.resolve("libpdfium.dylib"))
                }

                doLast {
                    // $BUILT_PRODUCTS_DIR へのコピー
                    val targetFile1 = targetDir.resolve("libpdfium.dylib")
                    dylibFile.copyTo(targetFile1, overwrite = true)
                    ProcessBuilder(
                        "codesign",
                        "--force",
                        "--sign",
                        codesignIdentity,
                        "--timestamp=none",
                        targetFile1.absolutePath
                    )
                        .inheritIO()
                        .start()
                        .waitFor()

                    // アプリの .app バンドル内へのコピー
                    if (contentsFolderPath != null) {
                        val targetFile2 = appDir.resolve("libpdfium.dylib")
                        dylibFile.copyTo(targetFile2, overwrite = true)
                        ProcessBuilder(
                            "codesign",
                            "--force",
                            "--sign",
                            codesignIdentity,
                            "--timestamp=none",
                            targetFile2.absolutePath
                        )
                            .inheritIO()
                            .start()
                            .waitFor()
                    }
                }
            }
        }
    }
}

tasks.named("embedAndSignAppleFrameworkForXcode") {
    finalizedBy("embedAndSignPdfium")
}

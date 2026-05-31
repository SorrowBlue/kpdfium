import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatformLibrary)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
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
        val commonMain by getting {
            dependencies {
                api(projects.kpdfium)
                implementation("org.jetbrains.compose.runtime:runtime:1.12.0-alpha01")
                implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha01")
                implementation("io.github.vinceglb:filekit-dialogs-compose:0.14.1")
                implementation("io.coil-kt.coil3:coil-compose:3.4.0")
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.ioOkio)
                implementation(libs.kotlinx.serializationJson)
            }
        }
        
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.activity.compose)
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
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
                    ProcessBuilder("codesign", "--force", "--sign", codesignIdentity, "--timestamp=none", targetFile1.absolutePath)
                        .inheritIO()
                        .start()
                        .waitFor()

                    // アプリの .app バンドル内へのコピー
                    if (contentsFolderPath != null) {
                        val targetFile2 = appDir.resolve("libpdfium.dylib")
                        dylibFile.copyTo(targetFile2, overwrite = true)
                        ProcessBuilder("codesign", "--force", "--sign", codesignIdentity, "--timestamp=none", targetFile2.absolutePath)
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

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatformLibrary)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.sorrowblue.kpdfium.sample.compose"
    }
    
    jvm()
    
    jvmToolchain {
        vendor.set(JvmVendorSpec.ADOPTIUM)
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.kpdfium)
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


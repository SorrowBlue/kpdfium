import java.net.URL
import java.net.URI
import java.io.InputStream
import java.io.File
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlin.dokka)

    id("kioarch.versioning")
    id("kioarch.detekt")
}

kotlin {
    explicitApi()
    jvmToolchain {
        vendor.set(JvmVendorSpec.ADOPTIUM)
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}

android {
    namespace = "com.sorrowblue.kpdfium.android"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            // Compile for all major Android architectures
            abiFilters.addAll(setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    ndkVersion = "29.0.14206865"

    externalNativeBuild {
        cmake {
            path("../src/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
}

mavenPublishing {
    publishToMavenCentral()

    coordinates(
        groupId = "com.sorrowblue.kpdfium",
        artifactId = "kpdfium-android-native",
        version = project.version.toString()
    )

    pom {
        name.set("kpdfium Android")
        description.set("Android library component for kpdfium C++ native code")
        inceptionYear.set("2026")
        url.set("https://github.com/SorrowBlue/kpdfium")
        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("sorrowblue")
                name.set("Sorrow Blue")
                url.set("https://github.com/SorrowBlue")
                email.set("sorrowblue.dev@gmail.com")
            }
        }
        scm {
            url.set("https://github.com/SorrowBlue/kpdfium")
            connection.set("scm:git:git://github.com/SorrowBlue/kpdfium.git")
            developerConnection.set("scm:git:ssh://github.com/SorrowBlue/kpdfium.git")
        }
    }
}



// Custom task to automatically download and install precompiled PDFium binaries and headers from bblanchon
val downloadAndExtractPdfium by tasks.registering(DownloadPdfiumTask::class) {
    group = "setup"
    description = "Downloads and extracts precompiled PDFium binaries and headers from bblanchon/pdfium-binaries"

    architectures.set(mapOf(
        "arm" to "armeabi-v7a",
        "arm64" to "arm64-v8a",
        "x86" to "x86",
        "x64" to "x86_64"
    ))
    jniLibsDir.set(layout.projectDirectory.dir("src/main/jniLibs"))
    headersDir.set(layout.projectDirectory.dir("../src/cpp/include"))
    buildTmpDir.set(layout.buildDirectory.dir("tmp"))
}

// Ensure the downloadAndExtractPdfium task runs before any CMake native compilation or JNI lib merging
tasks.configureEach {
    if (name.startsWith("configureCMake") || 
        name.startsWith("buildCMake") || 
        (name.startsWith("merge") && name.endsWith("JniLibFolders"))
    ) {
        dependsOn(downloadAndExtractPdfium)
    }
}

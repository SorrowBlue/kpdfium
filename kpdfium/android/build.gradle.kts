plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlin.dokka)

    id("kioarch.versioning")
    id("kioarch.detekt")
    id("kpdfium")
}

downloadPdfium {
    pdfiumVersion.set(libs.versions.pdfium.get())
    architectures.set(listOf("arm", "arm64", "x86", "x64"))
    jniLibsDir.set(layout.projectDirectory.dir("src/main/jniLibs"))
    headersDir.set(layout.projectDirectory.dir("../src/cpp/include"))
    buildTmpDir.set(layout.buildDirectory.dir("tmp"))
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

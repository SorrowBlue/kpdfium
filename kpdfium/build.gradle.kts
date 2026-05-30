plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatformLibrary)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlin.dokka)

    id("kioarch.versioning")
    id("kioarch.detekt")
    id("kpdfium")
}

val os = System.getProperty("os.name").lowercase()
val arch = System.getProperty("os.arch").lowercase()
val platformClassifier = when {
    os.contains("win") -> "win-x64"
    os.contains("mac") || os.contains("darwin") -> {
        if (arch.contains("aarch64") || arch.contains("arm64")) "mac-arm64" else "mac-x64"
    }
    os.contains("nux") -> {
        if (arch.contains("aarch64") || arch.contains("arm64")) "linux-arm64" else "linux-x64"
    }
    else -> throw UnsupportedOperationException("Unsupported OS: $os")
}

downloadPdfium {
    pdfiumVersion.set(libs.versions.pdfium.get())
    enableLocalCompile.set(true)
    architectures.set(listOf(
        platformClassifier,
        "ios-device-arm64",
        "ios-simulator-arm64",
        "ios-simulator-x64"
    ))
    jniLibsDir.set(layout.projectDirectory.dir("src/cpp/pdfium"))
    headersDir.set(layout.projectDirectory.dir("src/cpp/include"))
    buildTmpDir.set(layout.buildDirectory.dir("tmp"))
}

kotlin {
    explicitApi()

    jvmToolchain {
        vendor.set(JvmVendorSpec.ADOPTIUM)
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }

    android {
        namespace = "com.sorrowblue.kpdfium"
    }

    jvm()

    // iOS Targets
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io.core)
        }
        jvmMain.dependencies {
            // JVM JNI dynamic library loading (no JNA dependency required!)
        }
        androidMain.dependencies {
            implementation(project(":kpdfium:android"))
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

mavenPublishing {
    publishToMavenCentral()

    coordinates(
        groupId = "com.sorrowblue.kpdfium",
        artifactId = "kpdfium",
        version = project.version.toString()
    )

    pom {
        name.set("kpdfium")
        description.set("Kotlin Multiplatform Library for Archive Files")
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





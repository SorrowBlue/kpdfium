plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatformLibrary)
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

    android {
        namespace = "com.sorrowblue.kpdfium"
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            // JVM JNI dynamic library loading (no JNA dependency required!)
        }
        androidMain.dependencies {
            implementation(project(":kpdfium:android"))
        }
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



val downloadDesktopPdfium by tasks.registering(DownloadDesktopPdfiumTask::class) {
    group = "setup"
    description = "Downloads precompiled PDFium Desktop binaries depending on host OS"

    outputDir.set(layout.projectDirectory.dir("src/cpp/pdfium"))
    buildTmpDir.set(layout.buildDirectory.dir("tmp"))
}



val compileDesktopJni by tasks.registering(CompileDesktopJniTask::class) {
    group = "build"
    description = "Compiles pdfium-jni C++ shared library locally using CMake on Windows"
    dependsOn(downloadDesktopPdfium)

    sourceDir.set(layout.projectDirectory.dir("src/cpp"))
    outputDir.set(layout.projectDirectory.dir("src/jvmMain/resources/win32-x86-64"))
}

tasks.named("jvmProcessResources") {
    dependsOn(compileDesktopJni)
}

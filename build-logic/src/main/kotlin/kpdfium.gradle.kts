import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

// 1. Define configuration Extension interface
interface DownloadPdfiumExtension {
    val pdfiumVersion: Property<String>
    val enableLocalCompile: Property<Boolean>
    val architectures: ListProperty<String>
    val jniLibsDir: DirectoryProperty
    val headersDir: DirectoryProperty
    val buildTmpDir: DirectoryProperty
}

// 2. Register the 'downloadPdfium' extension block with default values
// Note: architectures, jniLibsDir, headersDir, and buildTmpDir must be explicitly specified by the user.
val extension = extensions.create<DownloadPdfiumExtension>("downloadPdfium").apply {
    pdfiumVersion.convention("latest")
    enableLocalCompile.convention(true)
}

// 3. Automate native build graph construction based on target project plugins
plugins.withId("com.android.library") {
    configureAndroidNative(extension)
}
plugins.withId("com.android.application") {
    configureAndroidNative(extension)
}
plugins.withId("org.jetbrains.kotlin.multiplatform") {
    configureKmpJvm(extension)
}

fun Project.configureAndroidNative(extension: DownloadPdfiumExtension) {
    println("kpdfium plugin: Auto-configuring Android Native PDFium Build Graph...")
    
    val downloadAndExtractPdfium = tasks.register<DownloadPdfiumTask>("downloadAndExtractPdfium") {
        group = "setup"
        description = "Downloads and extracts precompiled PDFium binaries and headers from bblanchon/pdfium-binaries"

        pdfiumVersion.set(extension.pdfiumVersion)
        buildTmpDir.set(extension.buildTmpDir)
        headersDir.set(extension.headersDir)
        extractHeaders.set(true)

        // Map architectures: "arm" -> "android-arm"
        val resolvedClassifiers = extension.architectures.map { archList ->
            archList.map { arch ->
                when (arch) {
                    "arm" -> "android-arm"
                    "arm64" -> "android-arm64"
                    "x86" -> "android-x86"
                    "x64" -> "android-x64"
                    else -> arch
                }
            }
        }
        classifiers.set(resolvedClassifiers)

        // Map outputDirs per architecture
        val resolvedOutputDirs = extension.architectures.zip(extension.jniLibsDir) { archList, jniDir ->
            archList.associate { arch ->
                val classifier = when (arch) {
                    "arm" -> "android-arm"
                    "arm64" -> "android-arm64"
                    "x86" -> "android-x86"
                    "x64" -> "android-x64"
                    else -> arch
                }
                val abiDirName = when (arch) {
                    "arm", "android-arm" -> "armeabi-v7a"
                    "arm64", "android-arm64" -> "arm64-v8a"
                    "x86", "android-x86" -> "x86"
                    "x64", "android-x64" -> "x86_64"
                    else -> arch
                }
                classifier to jniDir.dir(abiDirName).asFile
            }
        }
        outputDirs.set(resolvedOutputDirs)

        // Map includeFilters per architecture
        val resolvedIncludeFilters = extension.architectures.map { archList ->
            archList.associate { arch ->
                val classifier = when (arch) {
                    "arm" -> "android-arm"
                    "arm64" -> "android-arm64"
                    "x86" -> "android-x86"
                    "x64" -> "android-x64"
                    else -> arch
                }
                classifier to listOf("lib/libpdfium.so")
            }
        }
        includeFilters.set(resolvedIncludeFilters)
    }

    // Auto-link NDK CMake build to our download task
    tasks.configureEach {
        if (name.startsWith("configureCMake") || 
            name.startsWith("buildCMake") || 
            (name.startsWith("merge") && name.endsWith("JniLibFolders"))
        ) {
            dependsOn(downloadAndExtractPdfium)
        }
    }
}

fun Project.configureKmpJvm(extension: DownloadPdfiumExtension) {
    println("kpdfium plugin: Auto-configuring JVM Desktop Native PDFium Build Graph...")

    val downloadDesktopPdfium = tasks.register<DownloadPdfiumTask>("downloadDesktopPdfium") {
        group = "setup"
        description = "Downloads precompiled PDFium Desktop binaries depending on host OS"

        pdfiumVersion.set(extension.pdfiumVersion)
        buildTmpDir.set(extension.buildTmpDir)
        headersDir.set(extension.headersDir)
        extractHeaders.set(true)

        classifiers.set(extension.architectures)

        // Map outputDirs per platform classifier
        val resolvedOutputDirs = extension.architectures.zip(extension.jniLibsDir) { archList, jniDir ->
            archList.associateWith { arch ->
                jniDir.dir(arch).asFile
            }
        }
        outputDirs.set(resolvedOutputDirs)

        // Map includeFilters per platform classifier
        val resolvedIncludeFilters = extension.architectures.map { archList ->
            archList.associateWith {
                listOf("bin/*", "lib/*")
            }
        }
        includeFilters.set(resolvedIncludeFilters)
    }

    val compileDesktopJni = tasks.register<CompileDesktopJniTask>("compileDesktopJni") {
        group = "build"
        description = "Compiles pdfium-jni C++ shared library locally using CMake on Windows"
        dependsOn(downloadDesktopPdfium)

        sourceDir.set(layout.projectDirectory.dir("src/cpp"))
        outputDir.set(layout.projectDirectory.dir("src/jvmMain/resources/win32-x86-64"))

        onlyIf { extension.enableLocalCompile.get() }
    }

    // Auto-link JVM resources task to our local CMake compiler task
    tasks.configureEach {
        if (name == "jvmProcessResources") {
            dependsOn(compileDesktopJni)
        }
    }
}

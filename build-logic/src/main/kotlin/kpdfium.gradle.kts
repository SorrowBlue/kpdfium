import com.sorrowblue.kpdfium.plugin.CompileDesktopJniTask
import com.sorrowblue.kpdfium.plugin.DownloadPdfiumTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// 1. Define configuration Extension interface
interface DownloadPdfiumExtension {
    val pdfiumVersion: Property<String>
    val pdfiumIosVersion: Property<String>
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
    configureKmpIos(extension)
}

fun Project.configureAndroidNative(extension: DownloadPdfiumExtension) {
    println("kpdfium plugin: Auto-configuring Android Native PDFium Build Graph...")

    val downloadAndExtractPdfium = tasks.register<DownloadPdfiumTask>("downloadAndExtractPdfium") {
        group = "setup"
        description =
            "Downloads and extracts precompiled PDFium binaries and headers from bblanchon/pdfium-binaries"

        pdfiumVersion.set(extension.pdfiumVersion)
        buildTmpDir.set(extension.buildTmpDir)
        headersDir.set(extension.headersDir)
        extractHeaders.set(true)

        // Map architectures: "arm" -> "android-arm"
        classifiers.set(extension.architectures.map { resolveClassifiers(it) })

        // Map outputDirs per architecture
        outputDirs.set(
            extension.architectures.zip(extension.jniLibsDir) { archList, jniDir ->
                resolveOutputDirs(archList, jniDir)
            }
        )

        // Map includeFilters per architecture
        includeFilters.set(extension.architectures.map { resolveIncludeFilters(it) })
    }

    // Auto-link NDK CMake build to our download task
    tasks.configureEach {
        val isCMakeTask = name.startsWith("configureCMake") || name.startsWith("buildCMake")
        val isMergeJniTask = name.startsWith("merge") && name.endsWith("JniLibFolders")
        if (isCMakeTask || isMergeJniTask) {
            dependsOn(downloadAndExtractPdfium)
        }
    }
}

private fun resolveClassifiers(architectures: List<String>): List<String> =
    architectures.map { arch ->
        when (arch) {
            "arm" -> "android-arm"
            "arm64" -> "android-arm64"
            "x86" -> "android-x86"
            "x64" -> "android-x64"
            else -> arch
        }
    }

private fun resolveOutputDirs(
    architectures: List<String>,
    jniDir: org.gradle.api.file.Directory
): Map<String, File> = architectures.associate { arch ->
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

private fun resolveIncludeFilters(architectures: List<String>): Map<String, List<String>> =
    architectures.associate { arch ->
        val classifier = when (arch) {
            "arm" -> "android-arm"
            "arm64" -> "android-arm64"
            "x86" -> "android-x86"
            "x64" -> "android-x64"
            else -> arch
        }
        classifier to listOf("lib/libpdfium.so")
    }

fun Project.configureKmpJvm(extension: DownloadPdfiumExtension) {
    println("kpdfium plugin: Auto-configuring JVM Desktop Native PDFium Build Graph...")

    val downloadDesktopPdfium = tasks.register<DownloadPdfiumTask>("downloadDesktopPdfium") {
        group = "setup"
        description = "Downloads precompiled PDFium Desktop binaries depending on host OS"

        pdfiumVersion.set(extension.pdfiumVersion)
        pdfiumIosVersion.set(extension.pdfiumIosVersion)
        buildTmpDir.set(extension.buildTmpDir)
        headersDir.set(extension.headersDir)
        extractHeaders.set(true)

        classifiers.set(extension.architectures)

        // Map outputDirs per platform classifier
        val resolvedOutputDirs = extension.architectures.zip(
            extension.jniLibsDir
        ) { archList, jniDir ->
            archList.associateWith { arch ->
                jniDir.dir(arch).asFile
            }
        }
        outputDirs.set(resolvedOutputDirs)

        // Map includeFilters per platform classifier
        val resolvedIncludeFilters = extension.architectures.map { archList ->
            archList.associateWith { arch ->
                when {
                    arch.startsWith("ios") -> listOf("lib/libpdfium.dylib")
                    else -> listOf("bin/*", "lib/*")
                }
            }
        }
        includeFilters.set(resolvedIncludeFilters)
    }

    val compileDesktopJni = tasks.register<CompileDesktopJniTask>("compileDesktopJni") {
        group = "build"
        description = "Compiles pdfium-jni C++ shared library locally using CMake"
        dependsOn(downloadDesktopPdfium)

        sourceDir.set(layout.projectDirectory.dir("src/cpp"))

        // Dynamically resolve target resources directory based on host OS and arch
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        val osDir = getOsDir(os)
        val archDir = getArchDir(arch)

        val resourceSubdir = "$osDir-$archDir" // e.g. win32-x86-64, darwin-aarch64, linux-x86-64
        outputDir.set(layout.projectDirectory.dir("src/jvmMain/resources/$resourceSubdir"))

        onlyIf { extension.enableLocalCompile.get() }
    }

    // Auto-link JVM resources task to our local CMake compiler task
    tasks.configureEach {
        if (name == "jvmProcessResources") {
            dependsOn(compileDesktopJni)
        }
    }
}

private fun getOsDir(os: String): String = when {
    os.contains("win") -> "win32"
    os.contains("mac") || os.contains("darwin") -> "darwin"
    os.contains("nux") -> "linux"
    else -> throw UnsupportedOperationException("Unsupported OS: $os")
}

private fun getArchDir(arch: String): String = when {
    arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x64") -> "x86-64"
    arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
    else -> throw UnsupportedOperationException("Unsupported architecture: $arch")
}

fun Project.configureKmpIos(extension: DownloadPdfiumExtension) {
    val kotlin =
        extensions.findByType<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension>()
            ?: return

    afterEvaluate {
        // iOS targets check
        val hasIos = kotlin.targets.any { it.name.startsWith("ios") }
        if (!hasIos) return@afterEvaluate

        println("kpdfium plugin: Auto-configuring iOS Native PDFium Build Graph...")
        // Configure C-Interop and static library embedding for iOS targets
        kotlin.targets.withType<KotlinNativeTarget>().forEach { target ->
            if (target.name.startsWith("ios")) {
                val platformClassifier = when (target.name) {
                    "iosX64" -> "ios-simulator-x64"
                    "iosArm64" -> "ios-device-arm64"
                    "iosSimulatorArm64" -> "ios-simulator-arm64"
                    else -> null
                }

                // Configure C-Interop for headers and static library embedding into klib
                target.compilations.getByName("main") {
                    cinterops.create("pdfium") {
                        defFile(project.file("src/nativeInterop/cinterop/pdfium.def"))
                        includeDirs(extension.headersDir)
                        if (platformClassifier != null) {
                            val libDir = extension.jniLibsDir.get().dir(platformClassifier).asFile
                            extraOpts("-libraryPath", libDir.absolutePath)
                        }
                    }
                }
            }
        }
    }

    // Auto-link iOS cinterop compile task to pre-download setup task
    tasks.configureEach {
        if (name.startsWith("cinteropPdfiumIos", ignoreCase = true)) {
            dependsOn("downloadDesktopPdfium")
        }
    }
}

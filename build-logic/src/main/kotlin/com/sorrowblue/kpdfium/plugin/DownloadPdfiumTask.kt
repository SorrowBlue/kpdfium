package com.sorrowblue.kpdfium.plugin

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

abstract class DownloadPdfiumTask : DefaultTask() {
    @get:Inject
    abstract val archiveOperations: ArchiveOperations

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    // Dynamic list of packages to download (e.g. ["android-arm64", "win-x64"])
    @get:Input
    abstract val classifiers: ListProperty<String>

    // Destination directories mapped per package name (classifier -> directory)
    @get:Input
    abstract val outputDirs: MapProperty<String, File>

    // Included files filters mapped per package name (classifier -> lists of includes)
    @get:Input
    abstract val includeFilters: MapProperty<String, List<String>>

    // Flag to extract official C headers (include/*.h)
    @get:Input
    abstract val extractHeaders: Property<Boolean>

    // PDFium Version (either "latest" or a release tag like "chromium/6543")
    @get:Input
    abstract val pdfiumVersion: Property<String>

    // Optional PDFium iOS Version for paulocoutinhox/pdfium-lib (e.g. "7902")
    @get:Input
    @get:Optional
    abstract val pdfiumIosVersion: Property<String>

    // Shared headers output directory
    @get:Internal
    abstract val headersDir: DirectoryProperty

    @get:Internal
    abstract val buildTmpDir: DirectoryProperty

    @TaskAction
    fun downloadAndExtract() {
        val buildTmp = buildTmpDir.get().asFile
        val headersDest = headersDir.orNull?.asFile

        val classifiersList = classifiers.get()
        val version = pdfiumVersion.get()

        if (version == "latest") {
            throw GradleException(
                "kpdfium: 'latest' version is NOT allowed. Please specify a fixed PDFium version (e.g. 'chromium/6543') in downloadPdfium configuration block."
            )
        }

        classifiersList.forEachIndexed { index, classifier ->
            downloadAndExtractClassifier(index, classifier, version, buildTmp, headersDest)
        }
        println("PDFium download and extraction completed successfully!")
    }

    private fun downloadAndExtractClassifier(
        index: Int,
        classifier: String,
        version: String,
        buildTmp: File,
        headersDest: File?
    ) {
        val destDir = outputDirs.get()[classifier]
            ?: throw GradleException(
                "Output directory not specified for classifier: $classifier"
            )

        val targetVersion = if (classifier.startsWith("ios")) {
            pdfiumIosVersion.orNull ?: version
        } else {
            version
        }

        if (isPdfiumUpToDate(classifier, destDir, targetVersion)) {
            println(
                "PDFium binaries for $classifier ($targetVersion) already exist and are up-to-date. Skipping download."
            )
            return
        }

        val isIos = classifier.startsWith("ios")
        val archiveFileName = if (isIos) "pdfium-$classifier-$targetVersion.klib" else "pdfium-$classifier.tgz"
        val archiveFile = File(buildTmp, archiveFileName)
        archiveFile.parentFile.mkdirs()

        try {
            if (!archiveFile.exists() || archiveFile.length() == 0L) {
                downloadArchiveFile(classifier, targetVersion, archiveFile)
            }
            extractAndSaveMetadata(index, classifier, targetVersion, archiveFile, headersDest)
        } finally {
            archiveFile.delete()
            cleanupTempDirs(destDir, headersDest)
        }
    }

    private fun isPdfiumUpToDate(classifier: String, destDir: File, version: String): Boolean {
        val checkFileName = when {
            classifier.contains("android") -> "libpdfium.so"
            classifier.contains("ios") -> "libpdfium.a"
            classifier.contains("win") -> "pdfium.dll"
            classifier.contains("mac") -> "libpdfium.dylib"
            else -> "libpdfium.so"
        }
        val checkFile = File(destDir, checkFileName)
        val versionFile = File(destDir, ".pdfium-version")
        return checkFile.exists() &&
            versionFile.exists() &&
            versionFile.readText().trim() == version
    }

    private fun downloadArchiveFile(classifier: String, version: String, destFile: File) {
        val urlString = if (classifier.startsWith("ios")) {
            when (classifier) {
                "ios-device-arm64" ->
                    "https://repo1.maven.org/maven2/io/github/limuyang2/pdf-core-ios-arm64/$version/pdf-core-ios-arm64-$version-cinterop-pdfviewerCore.klib"

                "ios-simulator-arm64", "ios-simulator-x64" ->
                    "https://repo1.maven.org/maven2/io/github/limuyang2/pdf-core-ios-simulator-arm64/$version/pdf-core-ios-simulator-arm64-$version-cinterop-pdfviewerCore.klib"

                else -> throw GradleException("Unsupported iOS classifier: $classifier")
            }
        } else {
            val encodedVersion = version.replace("/", "%2F")
            "https://github.com/bblanchon/pdfium-binaries/releases/download/$encodedVersion/pdfium-$classifier.tgz"
        }

        println("Downloading PDFium for $classifier from $urlString...")
        val connection = URI(urlString).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "kpdfium-gradle-plugin")
        connection.connect()

        connection.inputStream.use { input: InputStream ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun extractAndSaveMetadata(
        index: Int,
        classifier: String,
        version: String,
        archiveFile: File,
        headersDest: File?
    ) {
        val destDir = outputDirs.get()[classifier]
            ?: throw GradleException(
                "Output directory not specified for classifier: $classifier"
            )

        val isIos = classifier.startsWith("ios")

        println("Extracting $classifier binaries into ${destDir.absolutePath}...")
        if (isIos) {
            fileSystemOperations.copy {
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                from(archiveOperations.zipTree(archiveFile)) {
                    include("**/included/libpdfium.a")
                    eachFile {
                        path = "libpdfium.a"
                    }
                }
                into(destDir)
            }
        } else {
            val includes = includeFilters.get()[classifier].orEmpty()
            fileSystemOperations.copy {
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                from(archiveOperations.tarTree(archiveOperations.gzip(archiveFile))) {
                    includes.forEach { include(it) }
                    eachFile {
                        path = name
                    }
                }
                into(destDir)
            }
        }

        // Extract shared headers if enabled, it's the first non-iOS target, and destination exists
        val shouldExtractHeaders = !isIos && index == 0 && extractHeaders.get()
        if (shouldExtractHeaders && headersDest != null) {
            println(
                "Extracting official PDFium C headers into ${headersDest.absolutePath}..."
            )
            fileSystemOperations.copy {
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                from(archiveOperations.tarTree(archiveOperations.gzip(archiveFile))) {
                    include("include/*.h")
                    eachFile {
                        path = name
                    }
                }
                into(headersDest)
            }
        }

        // Save version metadata after successful extraction
        val versionFile = File(destDir, ".pdfium-version")
        versionFile.parentFile.mkdirs()
        versionFile.writeText(version)
        println("Successfully saved version metadata for $classifier: $version")
    }

    private fun cleanupTempDirs(destDir: File, headersDest: File?) {
        // Safe cleanup of temporary empty directories created by Gradle copy task
        File(destDir, "bin").deleteRecursively()
        File(destDir, "lib").deleteRecursively()
        File(destDir, "release").deleteRecursively()
        File(destDir, "default").deleteRecursively()
        if (headersDest != null) {
            File(headersDest, "include").deleteRecursively()
            File(headersDest, "release").deleteRecursively()
        }
    }
}

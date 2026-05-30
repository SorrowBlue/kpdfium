import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.InputStream
import java.net.URI
import javax.inject.Inject

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
            throw GradleException("kpdfium: 'latest' version is NOT allowed. Please specify a fixed PDFium version (e.g. 'chromium/6543') in downloadPdfium configuration block.")
        }

        classifiersList.forEachIndexed { index, classifier ->
            val destDir = outputDirs.get()[classifier]
                ?: throw GradleException("Output directory not specified for classifier: $classifier")
            val includes = includeFilters.get()[classifier] ?: emptyList()
            
            // Resolve check file name dynamically based on OS platform classifier
            val checkFileName = when {
                classifier.contains("android") -> "libpdfium.so"
                classifier.contains("ios") -> "lib/libpdfium.a"
                classifier.contains("win") -> "pdfium.dll"
                classifier.contains("mac") -> "libpdfium.dylib"
                else -> "libpdfium.so"
            }
            val checkFile = File(destDir, checkFileName)
            val versionFile = File(destDir, ".pdfium-version")

            // Perform deterministic local caching check based on requested version
            val isUpToDate = checkFile.exists() && 
                             versionFile.exists() && 
                             versionFile.readText().trim() == version

            if (isUpToDate) {
                println("PDFium binaries for $classifier ($version) already exist and are up-to-date. Skipping download.")
                return@forEachIndexed
            }

            val encodedVersion = version.replace("/", "%2F")
            val urlString = "https://github.com/bblanchon/pdfium-binaries/releases/download/$encodedVersion/pdfium-$classifier.tgz"
            val tarFile = File(buildTmp, "pdfium-$classifier.tgz")
            tarFile.parentFile.mkdirs()

            try {
                URI(urlString).toURL().openStream().use { input: InputStream ->
                    tarFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                println("Extracting $classifier binaries into ${destDir.absolutePath}...")
                fileSystemOperations.copy {
                    from(archiveOperations.tarTree(archiveOperations.gzip(tarFile))) {
                        includes.forEach { include(it) }
                        eachFile {
                            path = name
                        }
                    }
                    into(destDir)
                }

                // Extract shared headers if enabled, it's the first target (to prevent duplicated extraction), and destination exists
                if (extractHeaders.get() && headersDest != null && index == 0) {
                    println("Extracting official PDFium C headers into ${headersDest.absolutePath}...")
                    fileSystemOperations.copy {
                        from(archiveOperations.tarTree(archiveOperations.gzip(tarFile))) {
                            include("include/*.h")
                            eachFile {
                                path = name
                            }
                        }
                        into(headersDest)
                    }
                }

                // Save version metadata after successful extraction
                versionFile.parentFile.mkdirs()
                versionFile.writeText(version)
                println("Successfully saved version metadata for $classifier: $version")
            } finally {
                tarFile.delete()
                
                // Safe cleanup of temporary empty directories created by Gradle copy task
                File(destDir, "bin").delete()
                File(destDir, "lib").delete()
                if (headersDest != null) {
                    File(headersDest, "include").delete()
                }
            }
        }
        println("PDFium download and extraction completed successfully!")
    }
}

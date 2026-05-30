import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.InputStream
import java.net.URI
import javax.inject.Inject

abstract class DownloadDesktopPdfiumTask : DefaultTask() {
    @get:Inject
    abstract val archiveOperations: ArchiveOperations

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Internal
    abstract val buildTmpDir: DirectoryProperty

    @TaskAction
    fun downloadAndExtract() {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        // Dynamically resolve target bblanchon package name based on host environment
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

        val destDir = File(outputDir.get().asFile, platformClassifier)
        val checkFileName = if (os.contains("win")) "pdfium.dll" else if (os.contains("mac") || os.contains("darwin")) "libpdfium.dylib" else "libpdfium.so"
        val checkFile = File(destDir, checkFileName)
        
        if (checkFile.exists()) {
            println("Desktop PDFium binaries for $platformClassifier already exist, skipping download.")
            return
        }

        val buildTmp = buildTmpDir.get().asFile
        val urlString = "https://github.com/bblanchon/pdfium-binaries/releases/latest/download/pdfium-$platformClassifier.tgz"
        val tarFile = File(buildTmp, "pdfium-$platformClassifier.tgz")
        tarFile.parentFile.mkdirs()

        println("Downloading Desktop PDFium for $platformClassifier...")
        try {
            URI(urlString).toURL().openStream().use { input: InputStream ->
                tarFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            println("Extracting $platformClassifier binaries into src/cpp/pdfium/$platformClassifier...")
            fileSystemOperations.copy {
                from(archiveOperations.tarTree(archiveOperations.gzip(tarFile))) {
                    include("bin/*")
                    include("lib/*")
                    eachFile {
                        path = name
                    }
                }
                into(destDir)
            }

            // Extract shared headers into src/cpp/include
            println("Extracting official PDFium C headers into src/cpp/include...")
            val headersDir = File(outputDir.get().asFile.parentFile, "include")
            fileSystemOperations.copy {
                from(archiveOperations.tarTree(archiveOperations.gzip(tarFile))) {
                    include("include/*.h")
                    eachFile {
                        path = name
                    }
                }
                into(headersDir)
            }
        } finally {
            tarFile.delete()
            
            // Safe cleanup of temporary empty directories created by Gradle copy task
            File(destDir, "bin").delete()
            File(destDir, "lib").delete()
            File(outputDir.get().asFile, "include/include").delete()
        }
        println("Desktop PDFium binary successfully downloaded and configured!")
    }
}

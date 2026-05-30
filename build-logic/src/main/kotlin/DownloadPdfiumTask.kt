import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.MapProperty
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

    @get:Input
    abstract val architectures: MapProperty<String, String>

    @get:OutputDirectory
    abstract val jniLibsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val headersDir: DirectoryProperty

    @get:Internal
    abstract val buildTmpDir: DirectoryProperty

    @TaskAction
    fun downloadAndExtract() {
        val jniLibs = jniLibsDir.get().asFile
        val headers = headersDir.get().asFile
        val buildTmp = buildTmpDir.get().asFile

        architectures.get().forEach { (pdfiumArch, androidAbi) ->
            val abiDir = File(jniLibs, androidAbi)
            val checkFile = File(abiDir, "libpdfium.so")
            if (checkFile.exists()) {
                println("PDFium binary for $androidAbi already exists in jniLibs, skipping download.")
                return@forEach
            }

            println("Downloading PDFium tarball for $androidAbi from bblanchon...")
            val urlString = "https://github.com/bblanchon/pdfium-binaries/releases/latest/download/pdfium-android-$pdfiumArch.tgz"
            val tarFile = File(buildTmp, "pdfium-$pdfiumArch.tgz")
            tarFile.parentFile.mkdirs()

            try {
                URI(urlString).toURL().openStream().use { input: InputStream ->
                    tarFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                println("Extracting $androidAbi binary into jniLibs...")
                fileSystemOperations.copy {
                    from(archiveOperations.tarTree(archiveOperations.gzip(tarFile))) {
                        include("lib/libpdfium.so")
                        eachFile {
                            path = name
                        }
                    }
                    into(abiDir)
                }

                // Keep cinterop headers in sync by extracting include/*.h from arm64 package
                if (pdfiumArch == "arm64") {
                    println("Extracting official PDFium C headers into src/cpp/include...")
                    fileSystemOperations.copy {
                        from(archiveOperations.tarTree(archiveOperations.gzip(tarFile))) {
                            include("include/*.h")
                            eachFile {
                                path = name
                            }
                        }
                        into(headers)
                    }
                }
            } finally {
                tarFile.delete()
            }
        }
        println("PDFium binaries and headers successfully downloaded and configured!")
    }
}

package com.sorrowblue.kpdfium

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object JniLoader {
    private var isLoaded = false

    @Synchronized
    fun load() {
        if (isLoaded) return

        try {
            val os = System.getProperty("os.name").lowercase()
            val arch = System.getProperty("os.arch").lowercase()

            val (osDir, extension) = when {
                os.contains("win") -> "win32" to ".dll"
                os.contains("mac") || os.contains("darwin") -> "darwin" to ".dylib"
                os.contains("nux") -> "linux" to ".so"
                else -> throw UnsupportedOperationException("Unsupported OS: $os")
            }

            val archDir = when {
                arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x64") -> "x86-64"
                arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
                else -> throw UnsupportedOperationException("Unsupported architecture: $arch")
            }

            val resourceSubdir = "$osDir-$archDir"
            
            // Temporary directory to extract both libraries to ensure relative DLL dependency loading works on Windows
            val tempDir = Files.createTempDirectory("kpdfium-jni").toFile().apply {
                deleteOnExit()
            }

            val pdfiumFileName = when {
                os.contains("win") -> "pdfium$extension"
                else -> "libpdfium$extension"
            }
            val jniFileName = when {
                os.contains("win") -> "pdfium-jni$extension"
                else -> "libpdfium-jni$extension"
            }

            val pdfiumTempFile = extractLibrary(tempDir, resourceSubdir, pdfiumFileName)
            val jniTempFile = extractLibrary(tempDir, resourceSubdir, jniFileName)
                ?: throw java.io.IOException("Failed to find and extract JNI bridge library: $jniFileName")

            // On Windows, the JNI bridge DLL depends on pdfium.dll. Load pdfium first.
            if (pdfiumTempFile != null) {
                System.load(pdfiumTempFile.absolutePath)
            }
            System.load(jniTempFile.absolutePath)

            isLoaded = true
        } catch (e: Throwable) {
            throw UnsatisfiedLinkError("Failed to load native PDFium libraries: ${e.message}").initCause(e)
        }
    }

    private fun extractLibrary(tempDir: File, resourceSubdir: String, fileName: String): File? {
        val resourcePath = "/$resourceSubdir/$fileName"
        val input: InputStream = JniLoader::class.java.getResourceAsStream(resourcePath)
            ?: JniLoader::class.java.getResourceAsStream("/$fileName") // Fallback to root resource
            ?: return null

        val outputFile = File(tempDir, fileName).apply {
            deleteOnExit()
        }

        input.use { inputStream ->
            Files.copy(inputStream, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        return outputFile
    }
}

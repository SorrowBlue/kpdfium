package com.sorrowblue.kpdfium

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object JniLoader {
    private var isLoaded = false

    @Synchronized
    fun load() {
        if (isLoaded) return

        try {
            try {
                System.loadLibrary("pdfium")
            } catch (_: UnsatisfiedLinkError) {
                // Ignore and proceed to load the JNI bridge
            }
            System.loadLibrary("pdfium-jni")
            isLoaded = true
            return
        } catch (_: UnsatisfiedLinkError) {
            // Fall back to extracting the library from JAR resources
        }

        runCatching {
            val os = System.getProperty("os.name").lowercase()
            val arch = System.getProperty("os.arch").lowercase()

            val (osDir, extension) = getPlatformAndExtension(os)
            val archDir = getArchDir(arch)
            val resourceSubdir = "$osDir-$archDir"

            // 1. Calculate a unique identifier based on JAR or CodeSource modification timestamp
            val uniqueId = getUniqueId()

            // 2. Resolve a stable temp directory to cache extracted libraries
            val tempDir = File(System.getProperty("java.io.tmpdir"), "kpdfium-jni-$uniqueId")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }

            val (pdfiumFileName, jniFileName) = getLibNames(os, extension)

            val pdfiumTempFile = extractLibrary(tempDir, resourceSubdir, pdfiumFileName)
            val jniTempFile = extractLibrary(tempDir, resourceSubdir, jniFileName)
                ?: throw IOException("Failed to find and extract JNI bridge library: $jniFileName")

            loadNativeLib(pdfiumTempFile, jniTempFile)

            isLoaded = true
        }.onFailure { e ->
            throw UnsatisfiedLinkError("Failed to load native PDFium libraries: ${e.message}")
                .initCause(e)
        }
    }

    @Suppress("UnsafeDynamicallyLoadedCode")
    private fun loadNativeLib(pdfiumTempFile: File?, jniTempFile: File) {
        // On Windows, the JNI bridge DLL depends on pdfium.dll. Load pdfium first.
        if (pdfiumTempFile != null) {
            System.load(pdfiumTempFile.absolutePath)
        }
        System.load(jniTempFile.absolutePath)
    }

    private fun getPlatformAndExtension(os: String): Pair<String, String> = when {
        os.contains("win") -> "win32" to ".dll"
        os.contains("mac") || os.contains("darwin") -> "darwin" to ".dylib"
        os.contains("nux") -> "linux" to ".so"
        else -> throw UnsupportedOperationException("Unsupported OS: $os")
    }

    private fun getArchDir(arch: String): String = when {
        arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x64") -> "x86-64"
        arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
        else -> throw UnsupportedOperationException("Unsupported architecture: $arch")
    }

    private fun getUniqueId(): String = try {
        val codeSource = JniLoader::class.java.protectionDomain.codeSource
        val location = codeSource?.location
        if (location != null) {
            val file = File(location.toURI())
            if (file.exists()) {
                "${file.name}-${file.lastModified()}"
            } else {
                "dev-default"
            }
        } else {
            "fallback"
        }
    } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception) {
        "error-${System.currentTimeMillis()}"
    }

    private fun getLibNames(os: String, extension: String): Pair<String, String> {
        val pdfiumFileName = when {
            os.contains("win") -> "pdfium$extension"
            else -> "libpdfium$extension"
        }
        val jniFileName = when {
            os.contains("win") -> "pdfium-jni$extension"
            else -> "libpdfium-jni$extension"
        }
        return pdfiumFileName to jniFileName
    }

    private fun extractLibrary(tempDir: File, resourceSubdir: String, fileName: String): File? {
        val outputFile = File(tempDir, fileName)

        // 3. Skip copy if file already exists in the cache folder and is not empty
        if (outputFile.exists() && outputFile.length() > 0) {
            // Highly optimized! Instant load without disk write overhead.
            return outputFile
        }

        val resourcePath = "/$resourceSubdir/$fileName"
        val input: InputStream = JniLoader::class.java.getResourceAsStream(resourcePath)
            ?: JniLoader::class.java.getResourceAsStream("/$fileName") // Fallback to root resource
            ?: return null

        try {
            outputFile.parentFile.mkdirs()
            input.use { inputStream ->
                Files.copy(inputStream, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            // Clean up potentially corrupt partial copy
            if (outputFile.exists()) {
                outputFile.delete()
            }
            throw e
        } catch (e: SecurityException) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            throw e
        }

        return outputFile
    }
}

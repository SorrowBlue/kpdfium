package com.sorrowblue.kpdfium.plugin

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class CompileDesktopJniTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun compile() {
        val src = sourceDir.get().asFile
        val out = outputDir.get().asFile
        out.mkdirs()

        val buildDir = File(src, "build")
        buildDir.mkdirs()

        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        // 1. Run CMake configuration
        println("Running CMake configuration...")
        execOperations.exec {
            workingDir = buildDir
            val cmakeArgs = mutableListOf("cmake", "-S", src.absolutePath, "-B", ".")
            if (!os.contains("win")) {
                cmakeArgs.add("-DCMAKE_BUILD_TYPE=Release")
            }
            commandLine(cmakeArgs)
        }

        // 2. Build pdfium-jni shared library
        println("Building pdfium-jni shared library...")
        execOperations.exec {
            workingDir = buildDir
            val buildArgs = mutableListOf("cmake", "--build", ".")
            if (os.contains("win")) {
                buildArgs.addAll(listOf("--config", "Release"))
            }
            commandLine(buildArgs)
        }

        // 3. Resolve library filenames based on OS
        val (jniLibName, pdfiumLibName) = getJniAndPdfiumLibNames(os)

        // 4. Resolve generated JNI library file location
        val generatedJniFile = when {
            os.contains("win") -> File(buildDir, "Release/$jniLibName")
            else -> File(buildDir, jniLibName)
        }

        if (generatedJniFile.exists()) {
            val destJni = File(out, jniLibName)
            generatedJniFile.copyTo(destJni, overwrite = true)

            // Resolve correct precompiled platform classifier folder
            val platformClassifier = getPlatformClassifier(os, arch)

            val originalPdfium = File(src, "pdfium/$platformClassifier/$pdfiumLibName")
            if (originalPdfium.exists()) {
                originalPdfium.copyTo(File(out, pdfiumLibName), overwrite = true)
            }
            println("$jniLibName and $pdfiumLibName successfully compiled and copied to resources!")
        } else {
            throw GradleException(
                "Failed to find generated JNI library at: ${generatedJniFile.absolutePath}"
            )
        }
    }

    private fun getJniAndPdfiumLibNames(os: String): Pair<String, String> = when {
        os.contains("win") -> "pdfium-jni.dll" to "pdfium.dll"
        os.contains("mac") || os.contains("darwin") -> "libpdfium-jni.dylib" to "libpdfium.dylib"
        os.contains("nux") -> "libpdfium-jni.so" to "libpdfium.so"
        else -> throw UnsupportedOperationException("Unsupported OS: $os")
    }

    private fun getPlatformClassifier(os: String, arch: String): String = when {
        os.contains("win") -> "win-x64"

        os.contains("mac") || os.contains("darwin") -> {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                "mac-arm64"
            } else {
                "mac-x64"
            }
        }

        os.contains("nux") -> {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                "linux-arm64"
            } else {
                "linux-x64"
            }
        }

        else -> throw UnsupportedOperationException("Unsupported OS: $os")
    }
}

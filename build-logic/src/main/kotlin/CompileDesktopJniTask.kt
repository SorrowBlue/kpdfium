import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

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

        if (System.getProperty("os.name").lowercase().contains("win")) {
            println("Running CMake configuration...")
            execOperations.exec {
                workingDir = buildDir
                commandLine("cmake", "-S", src.absolutePath, "-B", ".")
            }
            println("Building pdfium-jni shared library...")
            execOperations.exec {
                workingDir = buildDir
                commandLine("cmake", "--build", ".", "--config", "Release")
            }

            val generatedDll = File(buildDir, "Release/pdfium-jni.dll")
            if (generatedDll.exists()) {
                val dest = File(out, "pdfium-jni.dll")
                generatedDll.copyTo(dest, overwrite = true)
                
                val originalPdfium = File(src, "pdfium/win-x64/pdfium.dll")
                if (originalPdfium.exists()) {
                    originalPdfium.copyTo(File(out, "pdfium.dll"), overwrite = true)
                }
                println("pdfium-jni.dll and pdfium.dll successfully copied to resources!")
            } else {
                throw GradleException("Failed to find generated pdfium-jni.dll in CMake build directory.")
            }
        } else {
            println("Non-Windows platform detected, skipping local CMake build.")
        }
    }
}

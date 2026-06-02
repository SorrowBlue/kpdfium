import com.sorrowblue.kpdfium.plugin.DownloadGoogleDriveTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty

interface DownloadLargeTestPdfExtension {
    val outputDir: DirectoryProperty
    val downloads: MapProperty<String, String> // filename -> fileId
}

val extension = extensions.create<DownloadLargeTestPdfExtension>("downloadLargeTestPdf")

val downloadLargeTestPdf = tasks.register<DownloadGoogleDriveTask>("downloadLargeTestPdf") {
    group = "verification"
    description = "Downloads large PDF files from Google Drive for testing"

    outputDir.set(extension.outputDir)
    downloads.set(extension.downloads)
}

plugins.withId("org.jetbrains.kotlin.multiplatform") {
    tasks.configureEach {
        if (name == "jvmTestProcessResources") {
            dependsOn(downloadLargeTestPdf)
        }
    }
}

package com.sorrowblue.kpdfium.plugin

import java.io.File
import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.impldep.org.apache.http.HttpStatus

abstract class DownloadGoogleDriveTask : DefaultTask() {

    @get:Input
    abstract val downloads: MapProperty<String, String> // filename -> fileId

    @get:Internal
    abstract val outputDir: DirectoryProperty

    @get:OutputFiles
    val outputFiles: Provider<Map<String, File>> = downloads.zip(outputDir) { dl, dir ->
        dl.mapKeys { (filename, _) -> filename }.mapValues { (filename, _) ->
            dir.file(filename).asFile
        }
    }

    @TaskAction
    fun download() {
        val client = HttpClient.newBuilder()
            .cookieHandler(CookieManager())
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build()

        val dir = outputDir.get().asFile
        dir.mkdirs()

        downloads.get().forEach { (filename, id) ->
            val destFile = File(dir, filename)
            if (destFile.exists()) {
                println("File $filename (ID $id) already exists. Skipping download.")
                return@forEach
            }

            runCatching {
                val initialUrl = "https://docs.google.com/uc?export=download&id=$id"
                val request1 = HttpRequest.newBuilder().uri(URI.create(initialUrl)).build()
                val response1 = client.send(request1, HttpResponse.BodyHandlers.ofString())

                val html = response1.body()
                val confirmRegex = """name="confirm"\s+value="([^"]+)"""".toRegex()
                val uuidRegex = """name="uuid"\s+value="([^"]+)"""".toRegex()

                val confirmMatch = confirmRegex.find(html)
                val uuidMatch = uuidRegex.find(html)

                val downloadUrl = if (confirmMatch != null && uuidMatch != null) {
                    val confirm = confirmMatch.groupValues[1]
                    val uuid = uuidMatch.groupValues[1]
                    "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=$confirm&uuid=$uuid"
                } else {
                    initialUrl
                }

                val request2 = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).build()
                val response2 = client.send(
                    request2,
                    HttpResponse.BodyHandlers.ofFile(destFile.toPath())
                )

                if (response2.statusCode() != HttpStatus.SC_OK) {
                    throw GradleException(
                        "Failed to download $filename. HTTP status: ${response2.statusCode()}"
                    )
                }
            }.onFailure { e ->
                if (destFile.exists()) {
                    destFile.delete()
                }
                throw GradleException("Failed to download $filename: ${e.message}", e)
            }
        }
    }
}

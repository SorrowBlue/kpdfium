import dev.detekt.gradle.report.ReportMergeTask

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.multiplatformLibrary) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.kotlin.dokka) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

val reportMerge = tasks.register("reportMerge", ReportMergeTask::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    output.set(rootProject.layout.buildDirectory.file("reports/detekt/merge.sarif"))
}

tasks.register<Delete>("clean") {
    group = "build"
    description = "Deletes the build directory and all generated/downloaded native build files."
    
    delete(rootProject.layout.projectDirectory.dir("kpdfium/android/.cxx"))
    delete(rootProject.layout.projectDirectory.dir("kpdfium/src/cpp/build"))
    delete(rootProject.layout.projectDirectory.dir("kpdfium/src/cpp/include"))
    delete(rootProject.layout.projectDirectory.dir("kpdfium/src/cpp/pdfium"))
}

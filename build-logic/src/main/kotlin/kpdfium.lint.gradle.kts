import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

private fun PluginManager.hasPlugin(provider: Provider<PluginDependency>): Boolean =
    hasPlugin(provider.get().pluginId)

when {
    pluginManager.hasPlugin(libs.plugins.android.application) -> {
        configure<ApplicationExtension> {
            lint {
                configure()
            }
        }
    }

    pluginManager.hasPlugin(libs.plugins.android.library) -> {
        configure<LibraryExtension> {
            lint {
                configure()
            }
        }
    }

    pluginManager.hasPlugin(libs.plugins.android.multiplatformLibrary) -> {
        configure<KotlinMultiplatformExtension> {
            configure<KotlinMultiplatformAndroidLibraryExtension> {
                lint {
                    configure()
                }
            }
        }
    }
}

private fun Lint.configure() {
    checkAllWarnings = true
    checkDependencies = true
    disable += listOf(
        "NewerVersionAvailable",
        "GradleDependency",
        "AppLinksAutoVerify",
    )
    baseline = rootProject.file("config/lint-baseline.xml")
}

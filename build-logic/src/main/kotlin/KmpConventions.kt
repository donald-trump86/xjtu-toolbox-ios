import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.BasePluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

internal val Project.libs: VersionCatalog
    get() = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

internal fun Project.configureKmpLibrary() {
    pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
    pluginManager.apply("base")
    extensions.configure(BasePluginExtension::class.java) {
        archivesName.set("toolbox-" + path.removePrefix(":").replace(':', '-'))
    }

    extensions.configure(KotlinMultiplatformExtension::class.java) {
        iosArm64()
        iosSimulatorArm64()
        applyDefaultHierarchyTemplate()
        sourceSets.getByName("commonMain").dependencies {
            implementation(libs.lib("kotlinx-coroutines-core"))
            implementation(libs.lib("kotlinx-serialization-json"))
            api(libs.lib("kotlinx-datetime"))
        }
        sourceSets.getByName("commonTest").dependencies {
            implementation("org.jetbrains.kotlin:kotlin-test")
            implementation(libs.lib("kotlinx-coroutines-test"))
        }
    }

    val simulatorDevice = providers.gradleProperty("iosSimulatorDevice")
    tasks.withType(KotlinNativeSimulatorTest::class.java).configureEach {
        simulatorDevice.orNull?.let { device.set(it) }
    }
}

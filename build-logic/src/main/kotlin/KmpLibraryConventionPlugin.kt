import org.gradle.api.Plugin
import org.gradle.api.Project

/** Native libraries shared by the device and simulator iOS architectures. */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = target.configureKmpLibrary()
}

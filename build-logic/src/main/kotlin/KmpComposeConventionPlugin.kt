import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Compose and MIUIX dependencies used by the iOS UI modules. */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.compose")
            apply("org.jetbrains.kotlin.plugin.compose")
        }
        val compose = extensions.getByType(ComposeExtension::class.java).dependencies
        val catalog = libs
        val kotlin = extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.sourceSets.getByName("commonMain").dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            api(compose.animation)
            // MIUIX Compose UI and navigation.
            api(catalog.lib("miuix-ui-kmp"))
            api(catalog.lib("miuix-icons-kmp"))
            api(catalog.lib("miuix-nav-kmp"))
            api(catalog.lib("miuix-preference-kmp"))
            api(catalog.lib("miuix-squircle-kmp"))
            // JetBrains lifecycle supports the UIKit Compose host.
            api(catalog.lib("jb-lifecycle-viewmodel"))
            api(catalog.lib("jb-lifecycle-viewmodel-compose"))
            api(catalog.lib("jb-lifecycle-runtime-compose"))
            api(catalog.lib("kyant-backdrop"))
            api(catalog.lib("compose-material-icons-core"))
            // 跨端图标：CMP 多平台版停在 1.7.3。
            api(catalog.lib("compose-material-icons-extended"))
            api(catalog.lib("compose-ui-backhandler"))
        }
    }
}

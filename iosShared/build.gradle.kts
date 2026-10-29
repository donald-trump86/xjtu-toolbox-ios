import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    applyDefaultHierarchyTemplate()
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "ToolboxShared"
            isStatic = true
            binaryOption("bundleId", "me.luvch1nabest.xjtutoolbox.shared")
        }
    }
    sourceSets {
        iosMain.dependencies {
            implementation(project(":shared:model"))
            implementation(project(":shared:navigation"))
            implementation(project(":shared:designsystem"))
            implementation(project(":shared:campus"))
            implementation(project(":feature:account"))
            implementation(project(":feature:home"))
            implementation(project(":feature:schedule"))
            implementation(project(":feature:score"))
            implementation(project(":feature:settings"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(libs.miuix.ui.kmp)
            implementation(libs.miuix.icons.kmp)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

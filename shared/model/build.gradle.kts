plugins { id("toolbox.kmp.library") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation("org.jetbrains.compose.runtime:runtime:${libs.versions.composeMultiplatform.get()}")
    }
}

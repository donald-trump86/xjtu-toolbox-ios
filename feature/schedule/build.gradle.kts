plugins {
    id("toolbox.kmp.library")
    id("toolbox.kmp.compose")
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":shared:model"))
        api(project(":shared:navigation"))
        api(project(":shared:designsystem"))
    }
}

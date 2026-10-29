plugins { id("toolbox.kmp.library") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:model"))
            api(libs.ktor.client.core)
            implementation(libs.ksoup)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

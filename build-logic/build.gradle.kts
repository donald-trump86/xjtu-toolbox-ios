plugins {
    `kotlin-dsl`
}

// Use the Kotlin and Compose plugin versions pinned by the root build.
dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    compileOnly("org.jetbrains.compose:compose-gradle-plugin:${libs.versions.composeMultiplatform.get()}")
}

kotlin {
    compilerOptions {
        // KGP 的元数据版本可能比 Gradle 内嵌的 Kotlin 新。
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "toolbox.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "toolbox.kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
    }
}

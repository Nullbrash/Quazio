import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    android {
        namespace = "io.github.nullbrash.quazio.feature.reminders"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.feature.finance)
            api(projects.feature.calendar)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

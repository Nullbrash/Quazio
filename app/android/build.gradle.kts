import io.github.nullbrash.quazio.core.model.version.AppVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.plugin.compose)
    id("quazio.app-version")
}

val appVersion = the<AppVersion>()

android {
    namespace = "io.github.nullbrash.quazio"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Постоянный адрес приложения: смена = другое приложение, без обновлений поверх.
        applicationId = "io.github.nullbrash.quazio"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersion.versionCode
        versionName = appVersion.name
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(projects.core.ui)
    implementation(libs.androidx.activity.compose)
}

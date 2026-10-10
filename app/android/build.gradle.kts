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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        // Для замеров памяти: как релиз (без отладки), но подписан отладочным ключом —
        // ставится поверх debug без потери данных. Отладочная сборка тяжелее на ~30 МБ.
        create("bench") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
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
    implementation(projects.core.accounts)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.kotlin.test)
}

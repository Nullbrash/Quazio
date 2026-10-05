import io.github.nullbrash.quazio.core.model.version.AppVersion
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.plugin.compose)
    id("quazio.app-version")
}

val appVersion = the<AppVersion>()

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(projects.core.ui)
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "io.github.nullbrash.quazio.desktop.MainKt"
        jvmArgs += listOf(
            "-Dquazio.version=${appVersion.name}",
            // Отрисовка без видеокарты экономит ~100 МБ — больше всех настроек Java.
            "-Dskiko.renderApi=SOFTWARE",
            // Временный потолок кучи: точное значение — по замеру настоящего приложения.
            "-Xmx256m",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Quazio"
            packageVersion = appVersion.numericName
        }
    }
}

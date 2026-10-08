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
    implementation(projects.core.accounts)
    implementation(projects.net.http)
    implementation(libs.compose.material.icons.core)
    implementation(libs.jna.platform)
    implementation(compose.desktop.currentOs)
}

// Запуск из среды разработки — со своей папкой данных, не с данными установленной программы.
tasks.withType<JavaExec>().configureEach {
    systemProperty("quazio.dataDir", layout.buildDirectory.dir("dev-data").get().asFile.absolutePath)
}

compose.desktop {
    application {
        mainClass = "io.github.nullbrash.quazio.desktop.MainKt"
        jvmArgs += listOf(
            "-Dquazio.version=${appVersion.name}",
            // Отрисовка без видеокарты экономит ~100 МБ — больше всех настроек Java.
            "-Dskiko.renderApi=SOFTWARE",
            // Потолок кучи — запас под будущие экраны (аналитика на ПК); 128m экономит лишь 10–25 МБ.
            // Пересмотреть по замеру на этапе 8. Подробности — docs/CONTEXT_measurements.md.
            "-Xmx256m",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Quazio"
            packageVersion = appVersion.numericName
            // Урезанная Java установленной программы: без java.sql база не открывается
            // (запуск из Gradle этого не ловит — там полный JDK). Список — suggestRuntimeModules.
            // java.net.http и jdk.crypto.ec — ссылки iCal по https (в Java 21 без jdk.crypto.ec
            // не проходит TLS с сертификатами на эллиптических кривых, как у Google).
            modules("java.instrument", "java.sql", "jdk.unsupported", "java.net.http", "jdk.crypto.ec")
        }
    }
}

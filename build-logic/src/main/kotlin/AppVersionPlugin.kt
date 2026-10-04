import io.github.nullbrash.quazio.core.model.version.AppVersion
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Разбирает `quazio.version` из корневого gradle.properties и кладёт в расширение `appVersion`. */
class AppVersionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val raw = target.providers.gradleProperty("quazio.version").get()
        target.extensions.add(AppVersion::class.java, "appVersion", AppVersion.parse(raw))
    }
}

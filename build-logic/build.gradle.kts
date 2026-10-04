plugins {
    `kotlin-dsl`
}

// Формула версий — один файл на сборку и на приложение: versionCode, который
// ставит сборка, и сравнение версий в автообновлении не могут разойтись.
sourceSets.main {
    kotlin.srcDir("../core/model/src/commonMain/kotlin/io/github/nullbrash/quazio/core/model/version")
}

gradlePlugin {
    plugins {
        register("appVersion") {
            id = "quazio.app-version"
            implementationClass = "AppVersionPlugin"
        }
    }
}

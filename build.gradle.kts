plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.plugin.compose) apply false
    alias(libs.plugins.sqldelight) apply false
}

// Драйвер SQLDelight тянет обычный sqlite-jdbc — во всём проекте он заменяется сборкой
// с тем же пакетом org.sqlite, но со встроенным шифрованием. Иначе база на ПК открылась
// бы без шифрования, а сборка этого бы не заметила.
val sqliteJdbcCrypt = libs.sqlite.jdbc.crypt.get().toString()
subprojects {
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.xerial:sqlite-jdbc")).using(module(sqliteJdbcCrypt))
                .because("база на ПК должна быть зашифрована")
        }
    }
}

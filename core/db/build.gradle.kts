import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    android {
        namespace = "io.github.nullbrash.quazio.core.db"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.sqlcipher.android)
            implementation(libs.androidx.sqlite)
        }
        getByName("desktopMain").dependencies {
            // sqlite-jdbc внутри драйвера подменяется шифрованным — см. корневой build.gradle.kts.
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.sqlite.jdbc.crypt)
            implementation(libs.jna.platform)
        }
    }
}

sqldelight {
    databases {
        create("QuazioDatabase") {
            packageName.set("io.github.nullbrash.quazio.core.db")
            // Единственный источник схемы — миграции: свежая база и обновлённая не расходятся.
            deriveSchemaFromMigrations.set(true)
        }
    }
}

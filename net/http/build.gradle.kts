import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(libs.kotlin.test)
}

// Сеть — только в ПК-версии (решение пользователя: клавиатура и Android к сети не обращаются).
// Модуль может подключать только :app:desktop; проверяются объявленные зависимости всех модулей.
val networkUsers = provider {
    rootProject.subprojects
        .filter { it.path != ":app:desktop" && it.path != path }
        .filter { p -> p.configurations.any { c -> c.dependencies.withType<ProjectDependency>().any { it.path == path } } }
        .map { it.path }
}
val checkNetworkBoundary by tasks.registering {
    description = "Сетевой модуль подключён только к ПК-версии"
    val users = networkUsers
    doLast {
        check(users.get().isEmpty()) { ":net:http подключён вне ПК-версии: ${users.get()}" }
    }
}
tasks.named("check") { dependsOn(checkNetworkBoundary) }

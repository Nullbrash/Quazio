package io.github.nullbrash.quazio.core.model.version

// Файл компилируется и в build-logic (встроенный в Gradle Kotlin) — только
// простой Kotlin без зависимостей и свежих возможностей языка.

/**
 * Версия Quazio: `Major.Minor.Patch`, бета — `-beta.N`, проверка механизма
 * обновлений — `-updtest.N`. Порядок: бета < проверка обновлений < релиз.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val stage: Stage,
    val stageNumber: Int,
) : Comparable<AppVersion> {

    enum class Stage { BETA, UPDTEST, RELEASE }

    init {
        // major ≤ 255 — предел версии установщика MSI; minor/patch ≤ 99 и
        // ступени ≤ 99 — чтобы разряды versionCode не наезжали друг на друга.
        require(major in 0..255) { "major вне 0..255: $major" }
        require(minor in 0..99) { "minor вне 0..99: $minor" }
        require(patch in 0..99) { "patch вне 0..99: $patch" }
        when (stage) {
            Stage.BETA -> require(stageNumber in 1..89) { "номер беты вне 1..89: $stageNumber" }
            Stage.UPDTEST -> require(stageNumber in 1..9) { "номер updtest вне 1..9: $stageNumber" }
            Stage.RELEASE -> require(stageNumber == 0) { "у релиза нет номера ступени" }
        }
    }

    val isPrerelease: Boolean get() = stage != Stage.RELEASE

    /** Строка версии как в теге релиза (без `v`). */
    val name: String
        get() = when (stage) {
            Stage.BETA -> "$numericName-beta.$stageNumber"
            Stage.UPDTEST -> "$numericName-updtest.$stageNumber"
            Stage.RELEASE -> numericName
        }

    /** Только числа: MSI не принимает суффиксы, бета на ПК различается описанием обновления. */
    val numericName: String get() = "$major.$minor.$patch"

    /**
     * Android `versionCode`: `major·1 000 000 + minor·10 000 + patch·100 + ступень`,
     * ступень — номер беты (1–89), проверка обновлений — 90–98, релиз — 99.
     * Растёт строго в порядке версий: Android не ставит «обновление» с меньшим кодом.
     */
    val versionCode: Int
        get() = major * 1_000_000 + minor * 10_000 + patch * 100 + stageCode

    private val stageCode: Int
        get() = when (stage) {
            Stage.BETA -> stageNumber
            Stage.UPDTEST -> 89 + stageNumber
            Stage.RELEASE -> 99
        }

    override fun compareTo(other: AppVersion): Int = versionCode.compareTo(other.versionCode)

    override fun toString(): String = name

    companion object {
        // Без ведущих нулей: иначе "01.2.3" и "1.2.3" — разные теги одной версии.
        private val pattern =
            Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-(beta|updtest)\.([1-9]\d*))?""")

        fun parse(text: String): AppVersion {
            val match = pattern.matchEntire(text.trim())
                ?: throw IllegalArgumentException("Неверный формат версии: \"$text\"")
            val (major, minor, patch, suffix, number) = match.destructured
            val stage = when (suffix) {
                "beta" -> Stage.BETA
                "updtest" -> Stage.UPDTEST
                else -> Stage.RELEASE
            }
            return AppVersion(
                major = major.toInt(),
                minor = minor.toInt(),
                patch = patch.toInt(),
                stage = stage,
                stageNumber = if (stage == Stage.RELEASE) 0 else number.toInt(),
            )
        }
    }
}

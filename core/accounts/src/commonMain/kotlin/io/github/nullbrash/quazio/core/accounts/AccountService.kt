package io.github.nullbrash.quazio.core.accounts

import io.github.nullbrash.quazio.core.db.ChangeLog
import io.github.nullbrash.quazio.core.db.QuazioDatabase
import io.github.nullbrash.quazio.core.model.Uuid7

enum class AccessLevel(val dbValue: String) {
    /** «Пользоваться»: смотреть и менять. */
    FULL("full"),
    /** «Следить»: только смотреть; правки принимаются только от устройств владельца (этап 3). */
    WATCH("watch");

    companion object {
        fun fromDb(value: String): AccessLevel = entries.first { it.dbValue == value }
    }
}

data class Account(val id: String, val name: String, val createdAt: Long, val access: AccessLevel)

/**
 * Локальные аккаунты устройства. Аккаунт — без логина и сервера: создаётся
 * здесь, его изменения идут в журнал синхронизации. Методы блокирующие —
 * вызывать не из главного потока.
 */
class AccountService(
    private val db: QuazioDatabase,
    private val clock: DeviceClock,
) {
    private val changeLog = ChangeLog(db)

    val deviceId: String get() = clock.deviceId

    /** Первый запуск: запись об устройстве и первый аккаунт. Повторный вызов ничего не меняет. */
    fun initialize(deviceName: String, platform: String, firstAccountName: String): Account = db.transactionWithResult {
        if (db.deviceQueries.byId(deviceId).executeAsOneOrNull() == null) {
            db.deviceQueries.insert(deviceId, deviceName, platform)
        }
        currentOrNull() ?: createInTransaction(firstAccountName).also { switchTo(it.id) }
    }

    fun accounts(): List<Account> = db.accountQueries.active().executeAsList().map { row ->
        Account(row.id, row.name, row.created_at, accessOf(row.id))
    }

    fun current(): Account = checkNotNull(currentOrNull()) { "Аккаунты не созданы — сначала initialize()" }

    fun create(name: String): Account = db.transactionWithResult { createInTransaction(name) }

    fun rename(accountId: String, name: String) {
        val clean = validName(name)
        db.transaction {
            requireNotNull(db.accountQueries.byId(accountId).executeAsOneOrNull()) { "Нет аккаунта $accountId" }
            val hlc = clock.now()
            db.accountQueries.setName(name = clean, hlc = hlc.toString(), id = accountId)
            changeLog.record(accountId, TABLE, accountId, hlc, mapOf("name" to clean))
        }
    }

    /** Переключение — состояние только этого устройства, в синхронизацию не идёт. */
    fun switchTo(accountId: String) {
        val row = requireNotNull(db.accountQueries.byId(accountId).executeAsOneOrNull()) { "Нет аккаунта $accountId" }
        require(row.deleted == 0L) { "Аккаунт удалён: $accountId" }
        db.appStateQueries.put(KEY_CURRENT_ACCOUNT, accountId)
    }

    private fun createInTransaction(name: String): Account {
        val clean = validName(name)
        val now = clock.wallMillis()
        val id = Uuid7.generate(now)
        val hlc = clock.now()
        db.accountQueries.insert(id, clean, now, hlc.toString())
        db.accountQueries.setAccess(id, AccessLevel.FULL.dbValue)
        db.accountQueries.addWriter(id, deviceId)
        changeLog.record(id, TABLE, id, hlc, mapOf("name" to clean, "created_at" to now.toString()))
        return Account(id, clean, now, AccessLevel.FULL)
    }

    private fun currentOrNull(): Account? {
        val id = db.appStateQueries.get(KEY_CURRENT_ACCOUNT).executeAsOneOrNull() ?: return null
        val row = db.accountQueries.byId(id).executeAsOneOrNull()?.takeIf { it.deleted == 0L } ?: return null
        return Account(row.id, row.name, row.created_at, accessOf(row.id))
    }

    private fun accessOf(accountId: String): AccessLevel =
        db.accountQueries.accessOf(accountId).executeAsOneOrNull()?.let(AccessLevel::fromDb) ?: AccessLevel.FULL

    private fun validName(name: String): String {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Пустое имя аккаунта" }
        require(clean.length <= MAX_NAME) { "Имя длиннее $MAX_NAME символов" }
        return clean
    }

    private companion object {
        const val TABLE = "account"
        const val KEY_CURRENT_ACCOUNT = "current_account_id"
        const val MAX_NAME = 50
    }
}

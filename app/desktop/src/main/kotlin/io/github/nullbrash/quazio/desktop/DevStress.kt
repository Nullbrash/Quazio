package io.github.nullbrash.quazio.desktop

import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.feature.finance.CategoryKind
import io.github.nullbrash.quazio.feature.finance.FinAccountType
import io.github.nullbrash.quazio.feature.finance.TransactionDraft
import io.github.nullbrash.quazio.feature.finance.TxnKind
import java.awt.Window
import java.awt.event.MouseWheelEvent
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.swing.SwingUtilities
import kotlin.random.Random

/**
 * Проверка нагрузки на длинном списке (только для разработки, интерфейса не имеет).
 * `-Dquazio.dev.seedOps=N` — N операций в текущем месяце; работает только вместе
 * с `-Dquazio.dataDir`, чтобы никогда не попасть в настоящую базу.
 * `-Dquazio.dev.scroll=true` — бесконечная прокрутка колесом без движения настоящей мыши.
 */
internal object DevStress {

    fun seedIfRequested(services: AppServices) {
        val count = System.getProperty("quazio.dev.seedOps")?.toIntOrNull() ?: return
        if (System.getProperty("quazio.dataDir") == null) return
        val accountId = services.accounts.initialize(services.deviceName, services.platform, "Я").id
        val finance = services.finance
        finance.ensureDefaults(accountId)
        if (finance.transactions(accountId, Long.MIN_VALUE, Long.MAX_VALUE).isNotEmpty()) return
        val card = finance.createAccount(accountId, "Карта", FinAccountType.CARD, 50_000_00)
        // Счёт «Наличные» уже создан набором по умолчанию.
        val cash = finance.accounts(accountId).firstOrNull { it.type == FinAccountType.CASH }?.id
            ?: finance.createAccount(accountId, "Наличные", FinAccountType.CASH, 5_000_00)
        val categories = finance.categories(accountId, CategoryKind.EXPENSE).map { it.id }
        val merchants = listOf("Пятёрочка", "Магнит", "SPAR", "Аптека", "Кофейня", "Такси", "Ozon", "Wildberries")
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val start = now.withDayOfMonth(1).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val span = now.toInstant().toEpochMilli() - start
        val random = Random(42)
        repeat(count) {
            finance.saveTransaction(accountId, TransactionDraft(
                kind = TxnKind.EXPENSE,
                amountMinor = random.nextLong(50_00, 5_000_00),
                finAccountId = if (random.nextInt(4) == 0) cash else card,
                categoryId = categories.random(random),
                merchantName = merchants.random(random),
                occurredAt = start + random.nextLong(span),
                timeZone = zone.id,
                description = if (random.nextBoolean()) "покупка №$it" else "",
            ))
        }
    }

    fun scrollIfRequested(window: Window) {
        if (System.getProperty("quazio.dev.scroll") != "true") return
        Thread({
            Thread.sleep(5_000) // окно и список успевают загрузиться
            var down = true
            var step = 0
            while (window.isDisplayable) {
                SwingUtilities.invokeLater {
                    val x = window.width / 2
                    val y = window.height / 2
                    val target = SwingUtilities.getDeepestComponentAt(window, x, y) ?: return@invokeLater
                    val point = SwingUtilities.convertPoint(window, x, y, target)
                    target.dispatchEvent(MouseWheelEvent(
                        target, MouseWheelEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
                        point.x, point.y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, if (down) 1 else -1,
                    ))
                }
                if (++step % 300 == 0) down = !down
                Thread.sleep(16)
            }
        }, "dev-scroll").apply { isDaemon = true }.start()
    }
}

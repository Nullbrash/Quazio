package io.github.nullbrash.quazio.core.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Разделы приложения в навигации. Новые модули добавляются сюда. */
enum class Destination { FINANCE, CALENDAR, SETTINGS }

enum class NavLayout { BOTTOM_BAR, SIDE_RAIL }

/** Порог Material: уже 600 dp — телефон, нижняя панель; шире — боковая. */
fun navLayoutFor(width: Dp): NavLayout =
    if (width < 600.dp) NavLayout.BOTTOM_BAR else NavLayout.SIDE_RAIL

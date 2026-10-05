package io.github.nullbrash.quazio.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler

/**
 * Системная «назад» (Android): закрыть внутренний экран, а не всё приложение.
 * Срабатывает самый глубокий включённый обработчик — окно поверх списка перехватывает первым.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SystemBack(enabled: Boolean = true, onBack: () -> Unit) = BackHandler(enabled, onBack)

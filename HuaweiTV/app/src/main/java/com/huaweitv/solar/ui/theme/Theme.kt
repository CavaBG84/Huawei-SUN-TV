package com.huaweitv.solar.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalTvTypography = staticCompositionLocalOf { TvTypography }

@Composable
fun HuaweiTvTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTvTypography provides TvTypography,
        content = content
    )
}

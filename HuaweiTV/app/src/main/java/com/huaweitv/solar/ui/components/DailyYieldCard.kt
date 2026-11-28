package com.huaweitv.solar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huaweitv.solar.R
import com.huaweitv.solar.ui.theme.DarkCardBorder
import com.huaweitv.solar.ui.theme.LocalTvTypography
import com.huaweitv.solar.ui.theme.SolarYellow
import com.huaweitv.solar.ui.theme.SolarYellowGlow
import com.huaweitv.solar.ui.theme.TextMuted
import com.huaweitv.solar.ui.theme.TextPrimary
import com.huaweitv.solar.ui.theme.TextSecondary

/**
 * Card highlighting the daily solar production (Produzione Giornaliera).
 * Fully responsive: adapts padding and typography for smartphone and Android TV.
 */
@Composable
fun DailyYieldCard(
    modifier: Modifier = Modifier,
    dailyYieldKWh: Double,
    subtitle: String = stringResource(id = R.string.daily_yield_subtitle_compact),
    isCompact: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val typography = LocalTvTypography.current

    val hPadding = if (isCompact) 14.dp else 22.dp
    val vPadding = if (isCompact) 10.dp else 14.dp
    val badgeSize = if (isCompact) 32.dp else 46.dp
    val iconSize = if (isCompact) 18.dp else 26.dp
    val titleSize = if (isCompact) 11.sp else 14.sp
    val valueSize = if (isCompact) 22.sp else 32.sp
    val subtitleSize = if (isCompact) 10.sp else 12.sp

    FocusableTvCard(
        modifier = modifier,
        onClick = onClick,
        focusedBorderColor = SolarYellow,
        unfocusedBorderColor = DarkCardBorder
    ) { isFocused ->
        Row(
            modifier = Modifier
                .padding(horizontal = hPadding, vertical = vPadding)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(badgeSize)
                        .clip(CircleShape)
                        .background(SolarYellowGlow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_sun),
                        contentDescription = "Solar Yield",
                        tint = SolarYellow,
                        modifier = Modifier.size(iconSize)
                    )
                }

                Spacer(modifier = Modifier.width(if (isCompact) 10.dp else 16.dp))

                Column {
                    Text(
                        text = stringResource(id = R.string.daily_yield_title),
                        style = typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isFocused) TextPrimary else TextSecondary,
                            fontSize = titleSize,
                            letterSpacing = if (isCompact) 0.5.sp else 1.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = typography.bodyMedium.copy(
                            color = TextMuted,
                            fontSize = subtitleSize
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = String.format(java.util.Locale.US, "%.2f kWh", dailyYieldKWh),
                style = typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = SolarYellow,
                    fontSize = valueSize
                ),
                maxLines = 1
            )
        }
    }
}

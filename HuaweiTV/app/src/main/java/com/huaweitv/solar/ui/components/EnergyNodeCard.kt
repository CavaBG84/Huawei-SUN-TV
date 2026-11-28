package com.huaweitv.solar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huaweitv.solar.ui.theme.DarkCardBorder
import com.huaweitv.solar.ui.theme.LocalTvTypography
import com.huaweitv.solar.ui.theme.TextMuted
import com.huaweitv.solar.ui.theme.TextPrimary
import com.huaweitv.solar.ui.theme.TextSecondary

/**
 * High-contrast Card representing an Energy Node (PV, Battery, Grid, Home).
 * Fully responsive: adapts padding and typography for smartphone and Android TV.
 */
@Composable
fun EnergyNodeCard(
    modifier: Modifier = Modifier,
    title: String,
    valueText: String,
    subtitleText: String,
    icon: Painter,
    accentColor: Color,
    badgeBgColor: Color = accentColor.copy(alpha = 0.15f),
    batteryProgress: Float? = null,
    isCompact: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val typography = LocalTvTypography.current

    val hPadding = if (isCompact) 12.dp else 20.dp
    val vPadding = if (isCompact) 10.dp else 16.dp
    val badgeSize = if (isCompact) 32.dp else 44.dp
    val iconSize = if (isCompact) 18.dp else 26.dp
    val titleSize = if (isCompact) 11.sp else 15.sp
    val valueSize = if (isCompact) 20.sp else 32.sp
    val subtitleSize = if (isCompact) 10.sp else 13.sp
    val spacingTitle = if (isCompact) 6.dp else 10.dp
    val spacingValue = if (isCompact) 4.dp else 6.dp

    FocusableTvCard(
        modifier = modifier,
        onClick = onClick,
        focusedBorderColor = accentColor,
        unfocusedBorderColor = DarkCardBorder
    ) { isFocused ->
        Column(
            modifier = Modifier
                .padding(horizontal = hPadding, vertical = vPadding)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            // Header: Icon + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(badgeSize)
                        .clip(CircleShape)
                        .background(badgeBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(iconSize)
                    )
                }

                Spacer(modifier = Modifier.width(if (isCompact) 8.dp else 12.dp))

                Text(
                    text = title.uppercase(),
                    style = typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isFocused) TextPrimary else TextSecondary,
                        fontSize = titleSize,
                        letterSpacing = if (isCompact) 0.5.sp else 1.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(spacingTitle))

            // Main Large Value
            Text(
                text = valueText,
                style = typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    fontSize = valueSize
                ),
                maxLines = 1
            )

            // Optional Battery Level bar
            if (batteryProgress != null) {
                Spacer(modifier = Modifier.height(spacingValue))
                LinearProgressIndicator(
                    progress = { batteryProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isCompact) 6.dp else 8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = accentColor,
                    trackColor = Color(0xFF1E293B)
                )
            }

            Spacer(modifier = Modifier.height(spacingValue))

            // Subtitle badge / description
            Text(
                text = subtitleText,
                style = typography.bodyMedium.copy(
                    color = if (subtitleText.isNotEmpty()) accentColor else TextMuted,
                    fontWeight = FontWeight.Medium,
                    fontSize = subtitleSize
                ),
                maxLines = 1
            )
        }
    }
}

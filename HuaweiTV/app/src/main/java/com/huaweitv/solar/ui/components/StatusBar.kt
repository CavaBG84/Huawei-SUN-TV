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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huaweitv.solar.R
import com.huaweitv.solar.data.model.ConnectionState
import com.huaweitv.solar.ui.theme.DarkCardBg
import com.huaweitv.solar.ui.theme.LocalTvTypography
import com.huaweitv.solar.ui.theme.SolarYellow
import com.huaweitv.solar.ui.theme.StatusConnected
import com.huaweitv.solar.ui.theme.StatusConnecting
import com.huaweitv.solar.ui.theme.StatusError
import com.huaweitv.solar.ui.theme.StatusMock
import com.huaweitv.solar.ui.theme.TextMuted
import com.huaweitv.solar.ui.theme.TextPrimary
import com.huaweitv.solar.ui.theme.TextSecondary
import com.huaweitv.solar.ui.theme.TvFocusBorderColor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Top Status Bar designed for Android TV and responsive for Smartphones.
 */
@Composable
fun StatusBar(
    modifier: Modifier = Modifier,
    connectionState: ConnectionState,
    lastUpdatedTimestamp: Long,
    inverterSubtitle: String = "SUN2000 + LUNA2000",
    isCompact: Boolean = false,
    isPortrait: Boolean = false,
    onOpenSettings: () -> Unit
) {
    val typography = LocalTvTypography.current

    var currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        while (true) {
            currentTimeString = timeFormat.format(Date())
            delay(1000L)
        }
    }

    val updateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val lastUpdateStr = remember(lastUpdatedTimestamp) {
        if (lastUpdatedTimestamp > 0) updateFormat.format(Date(lastUpdatedTimestamp)) else "--:--:--"
    }

    val (statusColor, statusText) = when (connectionState) {
        is ConnectionState.Connected -> StatusConnected to stringResource(id = R.string.status_connected, connectionState.ip, connectionState.port)
        is ConnectionState.Connecting -> StatusConnecting to when {
            connectionState.ip.isNotEmpty() && connectionState.isFronius ->
                stringResource(id = R.string.status_connecting_fronius, connectionState.ip, connectionState.port)
            connectionState.ip.isNotEmpty() ->
                stringResource(id = R.string.status_connecting_huawei, connectionState.ip, connectionState.port)
            connectionState.message.isNotBlank() -> connectionState.message
            else -> stringResource(id = R.string.status_connecting_generic)
        }
        is ConnectionState.Error -> StatusError to stringResource(id = R.string.status_error_retry, connectionState.retryInSeconds)
        is ConnectionState.Mock -> StatusMock to stringResource(id = R.string.status_mock)
        ConnectionState.Disconnected -> Color.Gray to stringResource(id = R.string.status_disconnected)
        ConnectionState.NeedsConfig -> StatusConnecting to stringResource(id = R.string.status_needs_config)
    }

    if (isPortrait) {
        // --- Smartphone Portrait Mode (2 Rows) ---
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Logo + Clock + Settings button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_sun),
                        contentDescription = null,
                        tint = SolarYellow,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SOLARDECK",
                        style = typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentTimeString,
                        style = typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    FocusableTvCard(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(8.dp),
                        focusedBorderColor = TvFocusBorderColor
                    ) { isFocused ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_settings),
                                contentDescription = stringResource(id = R.string.btn_settings),
                                tint = if (isFocused) TvFocusBorderColor else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Row 2: Connection Badge & Last Update
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCardBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                            fontSize = 11.sp
                        ),
                        maxLines = 1
                    )
                }

                Text(
                    text = stringResource(id = R.string.label_updated_prefix, lastUpdateStr),
                    style = typography.bodyMedium.copy(
                        color = TextMuted,
                        fontSize = 10.sp
                    ),
                    maxLines = 1
                )
            }
        }
    } else {
        // --- Landscape (TV or Phone Landscape) ---
        val barHeight = if (isCompact) 44.dp else 56.dp
        val hPadding = if (isCompact) 12.dp else 24.dp

        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(barHeight)
                .padding(horizontal = hPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // App Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_sun),
                    contentDescription = null,
                    tint = SolarYellow,
                    modifier = Modifier.size(if (isCompact) 22.dp else 28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SOLARDECK",
                    style = typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = if (isCompact) 1.sp else 1.5.sp,
                        fontSize = if (isCompact) 16.sp else 20.sp
                    )
                )
                if (!isCompact) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkCardBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = inverterSubtitle,
                            style = typography.labelSmall.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // Center Connection Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkCardBg)
                    .padding(horizontal = if (isCompact) 10.dp else 14.dp, vertical = if (isCompact) 4.dp else 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isCompact) 8.dp else 10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    style = typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                        fontSize = if (isCompact) 11.sp else 13.sp
                    ),
                    maxLines = 1
                )

                if (!isCompact) {
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(id = R.string.label_updated_prefix, lastUpdateStr),
                        style = typography.bodyMedium.copy(
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            // Clock & Settings
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentTimeString,
                    style = typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = if (isCompact) 15.sp else 18.sp
                    )
                )

                Spacer(modifier = Modifier.width(if (isCompact) 10.dp else 16.dp))

                FocusableTvCard(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(8.dp),
                    focusedBorderColor = TvFocusBorderColor
                ) { isFocused ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = if (isCompact) 8.dp else 12.dp, vertical = if (isCompact) 4.dp else 6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = stringResource(id = R.string.btn_settings),
                            tint = if (isFocused) TvFocusBorderColor else TextSecondary,
                            modifier = Modifier.size(if (isCompact) 16.dp else 18.dp)
                        )
                        if (!isCompact) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(id = R.string.btn_settings),
                                style = typography.bodyMedium.copy(
                                    color = if (isFocused) TextPrimary else TextSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

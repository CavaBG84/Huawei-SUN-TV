package com.huaweitv.solar.ui.screens

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huaweitv.solar.R
import com.huaweitv.solar.ui.components.CoffeePromptDialog
import com.huaweitv.solar.ui.components.DailyYieldCard
import com.huaweitv.solar.ui.components.EnergyFlowDiagram
import com.huaweitv.solar.ui.components.FocusableTvCard
import com.huaweitv.solar.ui.components.PowerGuardBanner
import com.huaweitv.solar.ui.components.StatusBar
import com.huaweitv.solar.ui.theme.BatteryGreen
import com.huaweitv.solar.ui.theme.BatteryGreenGlow
import com.huaweitv.solar.ui.theme.DarkBg
import com.huaweitv.solar.ui.theme.DarkCardBorder
import com.huaweitv.solar.ui.theme.DarkSurface
import com.huaweitv.solar.ui.theme.LocalTvTypography
import com.huaweitv.solar.ui.theme.TextMuted
import com.huaweitv.solar.ui.theme.TextPrimary
import com.huaweitv.solar.ui.theme.TextSecondary
import com.huaweitv.solar.ui.viewmodel.SolarUiState

/**
 * Main Dashboard Screen.
 * Adapts between 16:9 Android TV (zero-scroll 10-foot experience)
 * and Smartphones (fully scrollable, responsive in both Portrait and Landscape).
 */
@Composable
fun DashboardScreen(
    uiState: SolarUiState,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onSaveSettings: (com.huaweitv.solar.data.model.AppSettings) -> Unit,
    onMuteAlarm: () -> Unit = {},
    onResetShellyShed: () -> Unit = {},
    onTestPowerAlarm: (Double) -> Unit = {},
    onTestShellyOff: suspend (String, Int, com.huaweitv.solar.data.model.ShellyGen) -> Result<Unit> = { _, _, _ -> Result.success(Unit) },
    onOpenCoffeePayPal: () -> Unit = {},
    onSnoozeCoffeePrompt: () -> Unit = {},
    onConfirmCoffeeDismissForever: () -> Unit = {},
    onCoffeeThankYouFinished: () -> Unit = {}
) {
    val typography = LocalTvTypography.current
    val solarData = uiState.solarData

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val context = LocalContext.current
    val isTv = remember(context) {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val hasTouch = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        (isTvMode || hasLeanback) && !hasTouch
    }

    // Identify if the device is a smartphone / small screen needing compact layout & scroll
    val isCompact = !isTv || screenHeightDp < 520 || screenWidthDp < 700 || isPortrait

    // Background gradient
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            DarkBg,
            DarkSurface.copy(alpha = 0.8f),
            DarkBg
        )
    )

    // Autonomy calculation
    val isSelfSufficient = solarData.gridPowerW >= 0 || (solarData.pvPowerW + kotlin.math.abs(solarData.batteryPowerW.coerceAtMost(0))) >= solarData.houseConsumptionW
    val autonomyPercentage = if (solarData.houseConsumptionW > 0) {
        val coveredByLocal = (solarData.houseConsumptionW - (-solarData.gridPowerW.coerceAtMost(0))).coerceAtLeast(0)
        ((coveredByLocal.toDouble() / solarData.houseConsumptionW) * 100).toInt().coerceIn(0, 100)
    } else 100

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        if (!isCompact) {
            // ===============================================
            // --- ANDROID TV MODE (Original 10-foot 16:9) ---
            // ===============================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Top Status Bar
                StatusBar(
                    connectionState = uiState.connectionState,
                    lastUpdatedTimestamp = solarData.timestamp,
                    inverterSubtitle = uiState.settings.inverterBrand.subtitle,
                    isCompact = false,
                    isPortrait = false,
                    onOpenSettings = onOpenSettings
                )

                if (uiState.powerGuardState.shouldShowBanner) {
                    PowerGuardBanner(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        state = uiState.powerGuardState,
                        alarmSoundEnabled = uiState.settings.meterAlarmSoundEnabled,
                        isCompact = false,
                        onMuteAlarm = onMuteAlarm,
                        onResetShellyShed = onResetShellyShed
                    )
                }

                // 2. Middle: Animated Energy Flow Diagram
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EnergyFlowDiagram(
                        modifier = Modifier.fillMaxWidth(),
                        solarData = solarData,
                        isCompact = false,
                        isPortrait = false
                    )
                }

                // 3. Bottom Row: Daily Yield & Energy Autonomy Cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    DailyYieldCard(
                        modifier = Modifier.weight(1f),
                        dailyYieldKWh = solarData.dailyYieldKWh,
                        subtitle = stringResource(id = R.string.daily_yield_subtitle),
                        isCompact = false,
                        onClick = onOpenSettings
                    )

                    FocusableTvCard(
                        modifier = Modifier.weight(1f),
                        focusedBorderColor = BatteryGreen,
                        unfocusedBorderColor = DarkCardBorder,
                        onClick = onOpenSettings
                    ) { isFocused ->
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 22.dp, vertical = 14.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 16.dp)
                                        .clip(CircleShape)
                                        .background(BatteryGreenGlow)
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_battery),
                                        contentDescription = null,
                                        tint = BatteryGreen
                                    )
                                }

                                Column {
                                    Text(
                                        text = stringResource(id = R.string.autonomy_title),
                                        style = typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFocused) TextPrimary else TextSecondary,
                                            fontSize = 14.sp,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isSelfSufficient) stringResource(id = R.string.autonomy_subtitle_100) else stringResource(id = R.string.autonomy_subtitle_partial),
                                        style = typography.bodyMedium.copy(
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }

                            Text(
                                text = "$autonomyPercentage%",
                                style = typography.displayMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (autonomyPercentage > 80) BatteryGreen else TextPrimary,
                                    fontSize = 32.sp
                                )
                            )
                        }
                    }
                }
            }
        } else {
            // =============================================================
            // --- SMARTPHONE MODE (Fully Responsive & Smooth Scrolling) ---
            // =============================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Status Bar
                StatusBar(
                    connectionState = uiState.connectionState,
                    lastUpdatedTimestamp = solarData.timestamp,
                    inverterSubtitle = uiState.settings.inverterBrand.subtitle,
                    isCompact = true,
                    isPortrait = isPortrait,
                    onOpenSettings = onOpenSettings
                )

                if (uiState.powerGuardState.shouldShowBanner) {
                    PowerGuardBanner(
                        state = uiState.powerGuardState,
                        alarmSoundEnabled = uiState.settings.meterAlarmSoundEnabled,
                        isCompact = true,
                        onMuteAlarm = onMuteAlarm,
                        onResetShellyShed = onResetShellyShed
                    )
                }

                // 2. Animated Energy Flow Diagram
                EnergyFlowDiagram(
                    modifier = Modifier.fillMaxWidth(),
                    solarData = solarData,
                    isCompact = true,
                    isPortrait = isPortrait
                )

                // 3. Bottom Cards (Stacked in Portrait, Side-by-Side in Landscape)
                if (isPortrait) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DailyYieldCard(
                            modifier = Modifier.fillMaxWidth(),
                            dailyYieldKWh = solarData.dailyYieldKWh,
                            subtitle = stringResource(id = R.string.daily_yield_subtitle_compact),
                            isCompact = true,
                            onClick = onOpenSettings
                        )

                        FocusableTvCard(
                            modifier = Modifier.fillMaxWidth(),
                            focusedBorderColor = BatteryGreen,
                            unfocusedBorderColor = DarkCardBorder,
                            onClick = onOpenSettings
                        ) { isFocused ->
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
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
                                            .padding(end = 10.dp)
                                            .clip(CircleShape)
                                            .background(BatteryGreenGlow)
                                            .padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_battery),
                                            contentDescription = null,
                                            tint = BatteryGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = stringResource(id = R.string.autonomy_title_compact),
                                            style = typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isFocused) TextPrimary else TextSecondary,
                                                fontSize = 11.sp,
                                                letterSpacing = 0.5.sp
                                            ),
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (isSelfSufficient) stringResource(id = R.string.autonomy_subtitle_100_compact) else stringResource(id = R.string.autonomy_subtitle_partial_compact),
                                            style = typography.bodyMedium.copy(
                                                color = TextMuted,
                                                fontSize = 10.sp
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = "$autonomyPercentage%",
                                    style = typography.displayMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (autonomyPercentage > 80) BatteryGreen else TextPrimary,
                                        fontSize = 22.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                } else {
                    // Landscape Phone: Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DailyYieldCard(
                            modifier = Modifier.weight(1f),
                            dailyYieldKWh = solarData.dailyYieldKWh,
                            subtitle = stringResource(id = R.string.daily_yield_subtitle_compact),
                            isCompact = true,
                            onClick = onOpenSettings
                        )

                        FocusableTvCard(
                            modifier = Modifier.weight(1f),
                            focusedBorderColor = BatteryGreen,
                            unfocusedBorderColor = DarkCardBorder,
                            onClick = onOpenSettings
                        ) { isFocused ->
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
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
                                            .padding(end = 10.dp)
                                            .clip(CircleShape)
                                            .background(BatteryGreenGlow)
                                            .padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_battery),
                                            contentDescription = null,
                                            tint = BatteryGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = stringResource(id = R.string.autonomy_title_compact),
                                            style = typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isFocused) TextPrimary else TextSecondary,
                                                fontSize = 11.sp,
                                                letterSpacing = 0.5.sp
                                            ),
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (isSelfSufficient) stringResource(id = R.string.autonomy_subtitle_100_compact) else stringResource(id = R.string.autonomy_subtitle_partial_compact),
                                            style = typography.bodyMedium.copy(
                                                color = TextMuted,
                                                fontSize = 10.sp
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = "$autonomyPercentage%",
                                    style = typography.displayMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (autonomyPercentage > 80) BatteryGreen else TextPrimary,
                                        fontSize = 22.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Settings Dialog Overlay
        if (uiState.isSettingsOpen) {
            SettingsDialog(
                currentSettings = uiState.settings,
                onSave = onSaveSettings,
                onDismiss = onCloseSettings,
                onTestPowerAlarm = onTestPowerAlarm,
                onTestShellyOff = onTestShellyOff
            )
        } else if (uiState.isCoffeePromptOpen) {
            CoffeePromptDialog(
                onOpenPayPal = onOpenCoffeePayPal,
                onSnoozeClose = onSnoozeCoffeePrompt,
                onConfirmDismissForever = onConfirmCoffeeDismissForever,
                onThankYouFinished = onCoffeeThankYouFinished
            )
        }
    }
}

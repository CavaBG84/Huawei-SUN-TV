package com.huaweitv.solar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huaweitv.solar.R
import com.huaweitv.solar.data.powerguard.PowerBand
import com.huaweitv.solar.data.powerguard.PowerGuardState
import com.huaweitv.solar.data.powerguard.ShellyStatusKind
import com.huaweitv.solar.ui.theme.BatteryGreen
import com.huaweitv.solar.ui.theme.GridRed
import com.huaweitv.solar.ui.theme.LocalTvTypography
import com.huaweitv.solar.ui.theme.SolarYellow
import com.huaweitv.solar.ui.theme.TextMuted
import com.huaweitv.solar.ui.theme.TextPrimary
import com.huaweitv.solar.ui.theme.TextSecondary
import java.util.Locale

/**
 * High-contrast Power Guard & Shelly Load-Shedding Banner.
 * Only rendered when an alarm is active (WARNING/CRITICAL) or a Shelly relay has shed a load.
 * Fully navigable via TV D-Pad and localized via Android string resources.
 */
@Composable
fun PowerGuardBanner(
    state: PowerGuardState,
    alarmSoundEnabled: Boolean,
    isCompact: Boolean,
    onMuteAlarm: () -> Unit,
    onResetShellyShed: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.shouldShowBanner) return

    val typography = LocalTvTypography.current
    val accentColor = when (state.band) {
        PowerBand.CRITICAL -> GridRed
        PowerBand.WARNING -> SolarYellow
        PowerBand.NORMAL -> BatteryGreen
    }
    val bgColor = accentColor.copy(alpha = 0.14f)

    val baseTitle = when (state.band) {
        PowerBand.CRITICAL -> stringResource(id = R.string.guard_title_critical)
        PowerBand.WARNING -> stringResource(id = R.string.guard_title_warning)
        PowerBand.NORMAL -> stringResource(id = R.string.guard_title_shelly)
    }
    val titleText = if (state.isTestSimulation) {
        "$baseTitle • ${stringResource(id = R.string.guard_test_badge)}"
    } else {
        baseTitle
    }

    val shellyText = when (state.shellyStatusKind) {
        ShellyStatusKind.SHEDDING_IN_PROGRESS ->
            stringResource(id = R.string.guard_shelly_shedding_in_progress, state.shedLoadName)
        ShellyStatusKind.SHED_SUCCESS ->
            stringResource(id = R.string.guard_shelly_shed_success, state.shedLoadName)
        ShellyStatusKind.SHED_ERROR ->
            stringResource(
                id = R.string.guard_shelly_shed_error,
                state.shedLoadName,
                state.shellyErrorDetail ?: stringResource(id = R.string.guard_shelly_unreachable)
            )
        ShellyStatusKind.NONE -> state.shellyStatusMessage
    }

    val detailsParts = buildList {
        if (state.band != PowerBand.NORMAL) {
            add(stringResource(id = R.string.guard_import_and_contract, state.importPowerKw, state.contractPowerKw))
            add(stringResource(id = R.string.guard_remaining_time, formatCountdown(state.remainingSeconds)))
        }
        if (state.shellyPendingDelaySec > 0) {
            add(stringResource(id = R.string.guard_shelly_pending, state.shellyPendingDelaySec))
        }
        if (!shellyText.isNullOrBlank()) {
            add(shellyText)
        }
    }
    val detailsText = detailsParts.joinToString("  •  ")

    val showMuteButton = state.band != PowerBand.NORMAL && (alarmSoundEnabled || state.isTestSimulation) && !state.isMuted
    val showMutedBadge = state.band != PowerBand.NORMAL && !state.isTestSimulation && alarmSoundEnabled && state.isMuted
    val muteLabel = stringResource(id = R.string.guard_btn_mute)
    val mutedBadgeText = stringResource(id = R.string.guard_muted_badge)
    val resetLabel = stringResource(id = R.string.guard_btn_reset)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, accentColor, RoundedCornerShape(12.dp))
            .padding(
                horizontal = if (isCompact) 12.dp else 18.dp,
                vertical = if (isCompact) 8.dp else 10.dp
            )
    ) {
        if (isCompact) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = titleText,
                    style = typography.titleMedium.copy(
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (detailsText.isNotEmpty()) {
                    Text(
                        text = detailsText,
                        style = typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontSize = 11.sp
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BannerActionRow(
                    showMuteButton = showMuteButton,
                    showMutedBadge = showMutedBadge,
                    isShedded = state.isShedded,
                    muteLabel = muteLabel,
                    mutedBadgeText = mutedBadgeText,
                    resetLabel = resetLabel,
                    accentColor = accentColor,
                    mutedFontSize = 10,
                    onMuteAlarm = onMuteAlarm,
                    onResetShellyShed = onResetShellyShed
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleText,
                        style = typography.titleMedium.copy(
                            color = accentColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            letterSpacing = 0.6.sp
                        )
                    )
                    if (detailsText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = detailsText,
                            style = typography.bodyMedium.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                BannerActionRow(
                    showMuteButton = showMuteButton,
                    showMutedBadge = showMutedBadge,
                    isShedded = state.isShedded,
                    muteLabel = muteLabel,
                    mutedBadgeText = mutedBadgeText,
                    resetLabel = resetLabel,
                    accentColor = accentColor,
                    mutedFontSize = 11,
                    onMuteAlarm = onMuteAlarm,
                    onResetShellyShed = onResetShellyShed
                )
            }
        }
    }
}

@Composable
private fun BannerActionRow(
    showMuteButton: Boolean,
    showMutedBadge: Boolean,
    isShedded: Boolean,
    muteLabel: String,
    mutedBadgeText: String,
    resetLabel: String,
    accentColor: Color,
    mutedFontSize: Int,
    onMuteAlarm: () -> Unit,
    onResetShellyShed: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showMuteButton) {
            BannerActionButton(
                label = muteLabel,
                accentColor = accentColor,
                onClick = onMuteAlarm
            )
        } else if (showMutedBadge) {
            Text(
                text = mutedBadgeText,
                style = LocalTvTypography.current.labelSmall.copy(
                    color = TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = mutedFontSize.sp
                )
            )
        }
        if (isShedded) {
            BannerActionButton(
                label = resetLabel,
                accentColor = BatteryGreen,
                onClick = onResetShellyShed
            )
        }
    }
}

@Composable
private fun BannerActionButton(
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    FocusableTvCard(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        focusedBorderColor = accentColor,
        unfocusedBorderColor = accentColor.copy(alpha = 0.7f)
    ) { isFocused ->
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = LocalTvTypography.current.bodyMedium.copy(
                color = if (isFocused) TextPrimary else TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatCountdown(totalSeconds: Long): String {
    val clamped = totalSeconds.coerceAtLeast(0L)
    val mins = clamped / 60L
    val secs = clamped % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

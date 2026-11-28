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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huaweitv.solar.R
import com.huaweitv.solar.data.model.SolarTelemetry
import com.huaweitv.solar.ui.theme.BatteryCyan
import com.huaweitv.solar.ui.theme.BatteryGreen
import com.huaweitv.solar.ui.theme.DarkCardBg
import com.huaweitv.solar.ui.theme.DarkCardBorder
import com.huaweitv.solar.ui.theme.GridPurple
import com.huaweitv.solar.ui.theme.GridRed
import com.huaweitv.solar.ui.theme.HouseBlue
import com.huaweitv.solar.ui.theme.LocalTvTypography
import com.huaweitv.solar.ui.theme.SolarYellow
import com.huaweitv.solar.ui.theme.TextMuted
import com.huaweitv.solar.ui.theme.TextPrimary

/**
 * Animated Energy Flow Diagram.
 * Adapts seamlessly between 16:9 Android TV, Smartphone Landscape, and Smartphone Portrait.
 */
@Composable
fun EnergyFlowDiagram(
    modifier: Modifier = Modifier,
    solarData: SolarTelemetry,
    isCompact: Boolean = false,
    isPortrait: Boolean = false
) {
    val typography = LocalTvTypography.current

    var diagramOrigin by remember { mutableStateOf(Offset.Zero) }
    var hubCenter by remember { mutableStateOf(Offset.Zero) }
    var pvCenter by remember { mutableStateOf(Offset.Zero) }
    var batteryCenter by remember { mutableStateOf(Offset.Zero) }
    var gridCenter by remember { mutableStateOf(Offset.Zero) }
    var homeCenter by remember { mutableStateOf(Offset.Zero) }

    val hubWidth = if (isCompact) 130.dp else 180.dp
    val rowSpacing = if (isCompact) 10.dp else 20.dp
    val colSpacing = if (isCompact) 10.dp else 32.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                diagramOrigin = coords.positionInRoot()
            }
    ) {
        // Draw animated connecting flow lines using local offsets
        if (hubCenter != Offset.Zero && diagramOrigin != Offset.Zero) {
            val localHub = hubCenter - diagramOrigin

            // 1. PV -> Inverter Hub
            if (pvCenter != Offset.Zero) {
                val localPv = pvCenter - diagramOrigin
                AnimatedFlowLine(
                    startOffset = localPv,
                    endOffset = localHub,
                    color = SolarYellow,
                    powerW = solarData.pvPowerW,
                    direction = if (solarData.pvPowerW > 10) FlowDirection.FORWARD else FlowDirection.IDLE
                )
            }

            // 2. Hub <-> Battery
            if (batteryCenter != Offset.Zero) {
                val localBattery = batteryCenter - diagramOrigin
                val batDirection = when {
                    solarData.batteryPowerW > 10 -> FlowDirection.FORWARD
                    solarData.batteryPowerW < -10 -> FlowDirection.BACKWARD
                    else -> FlowDirection.IDLE
                }
                val batColor = if (solarData.isBatteryCharging) BatteryGreen else BatteryCyan
                AnimatedFlowLine(
                    startOffset = localHub,
                    endOffset = localBattery,
                    color = batColor,
                    powerW = solarData.batteryPowerW,
                    direction = batDirection
                )
            }

            // 3. Hub <-> Grid
            if (gridCenter != Offset.Zero) {
                val localGrid = gridCenter - diagramOrigin
                val gridDirection = when {
                    solarData.gridPowerW > 10 -> FlowDirection.FORWARD
                    solarData.gridPowerW < -10 -> FlowDirection.BACKWARD
                    else -> FlowDirection.IDLE
                }
                val gridColor = if (solarData.gridPowerW >= 0) GridPurple else GridRed
                AnimatedFlowLine(
                    startOffset = localHub,
                    endOffset = localGrid,
                    color = gridColor,
                    powerW = solarData.gridPowerW,
                    direction = gridDirection
                )
            }

            // 4. Hub -> Home
            if (homeCenter != Offset.Zero) {
                val localHome = homeCenter - diagramOrigin
                AnimatedFlowLine(
                    startOffset = localHub,
                    endOffset = localHome,
                    color = HouseBlue,
                    powerW = solarData.houseConsumptionW,
                    direction = if (solarData.houseConsumptionW > 10) FlowDirection.FORWARD else FlowDirection.IDLE
                )
            }
        }

        if (isPortrait) {
            // ==========================================
            // --- Smartphone Portrait Mode (3 Tiers) ---
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tier 1: PV (Left) & Casa (Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EnergyNodeCard(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coords ->
                                pvCenter = Offset(
                                    coords.positionInRoot().x + coords.size.width / 2f,
                                    coords.positionInRoot().y + coords.size.height / 2f
                                )
                            },
                        title = stringResource(id = R.string.node_pv_title_compact),
                        valueText = solarData.formattedPvPower,
                        subtitleText = if (solarData.pvPowerW > 10) stringResource(id = R.string.node_pv_active_compact) else stringResource(id = R.string.node_pv_inactive_compact),
                        icon = painterResource(id = R.drawable.ic_sun),
                        accentColor = SolarYellow,
                        isCompact = true
                    )

                    EnergyNodeCard(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coords ->
                                homeCenter = Offset(
                                    coords.positionInRoot().x + coords.size.width / 2f,
                                    coords.positionInRoot().y + coords.size.height / 2f
                                )
                            },
                        title = stringResource(id = R.string.node_house_title_compact),
                        valueText = solarData.formattedHousePower,
                        subtitleText = stringResource(id = R.string.node_house_subtitle_compact),
                        icon = painterResource(id = R.drawable.ic_home),
                        accentColor = HouseBlue,
                        isCompact = true
                    )
                }

                // Tier 2: Central Inverter Hub Badge
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            hubCenter = Offset(
                                coords.positionInRoot().x + coords.size.width / 2f,
                                coords.positionInRoot().y + coords.size.height / 2f
                            )
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCardBg)
                        .border(1.5.dp, DarkCardBorder, RoundedCornerShape(16.dp))
                        .padding(vertical = 8.dp, horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${stringResource(id = R.string.hub_inverter_ac)}: ",
                            style = typography.labelSmall.copy(
                                color = TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = solarData.formatWattsOrKw(solarData.activePowerW),
                            style = typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                        )
                    }
                }

                // Tier 3: Batteria (Left) & Rete (Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val batStateText = when {
                        !solarData.hasBattery -> stringResource(id = R.string.node_battery_not_present)
                        solarData.batteryPowerW > 10 -> stringResource(id = R.string.node_battery_charging, solarData.formattedBatteryPower)
                        solarData.batteryPowerW < -10 -> stringResource(id = R.string.node_battery_discharging, solarData.formattedBatteryPower)
                        else -> stringResource(id = R.string.node_battery_standby)
                    }
                    val batAccent = if (solarData.isBatteryCharging) BatteryGreen else BatteryCyan

                    EnergyNodeCard(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coords ->
                                batteryCenter = Offset(
                                    coords.positionInRoot().x + coords.size.width / 2f,
                                    coords.positionInRoot().y + coords.size.height / 2f
                                )
                            },
                        title = stringResource(id = R.string.node_battery_title),
                        valueText = solarData.formattedBatterySoc,
                        subtitleText = batStateText,
                        icon = painterResource(id = R.drawable.ic_battery),
                        accentColor = batAccent,
                        batteryProgress = (solarData.batterySoc / 100.0).toFloat(),
                        isCompact = true
                    )

                    val gridStateText = when {
                        solarData.gridPowerW > 10 -> stringResource(id = R.string.node_grid_export_compact)
                        solarData.gridPowerW < -10 -> stringResource(id = R.string.node_grid_import_compact)
                        else -> stringResource(id = R.string.node_grid_balanced_compact)
                    }
                    val gridAccent = if (solarData.gridPowerW >= 0) GridPurple else GridRed

                    EnergyNodeCard(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coords ->
                                gridCenter = Offset(
                                    coords.positionInRoot().x + coords.size.width / 2f,
                                    coords.positionInRoot().y + coords.size.height / 2f
                                )
                            },
                        title = stringResource(id = R.string.node_grid_title_compact),
                        valueText = solarData.formattedGridPower,
                        subtitleText = gridStateText,
                        icon = painterResource(id = R.drawable.ic_grid),
                        accentColor = gridAccent,
                        isCompact = true
                    )
                }
            }
        } else {
            // ===============================================
            // --- Landscape Mode (Android TV or Phone Horiz) ---
            // ===============================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isCompact) 10.dp else 24.dp, vertical = if (isCompact) 4.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(rowSpacing)
            ) {
                // TOP ROW: PV (Left) and CASA (Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(colSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EnergyNodeCard(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coords ->
                                pvCenter = Offset(
                                    coords.positionInRoot().x + coords.size.width / 2f,
                                    coords.positionInRoot().y + coords.size.height / 2f
                                )
                            },
                        title = if (isCompact) stringResource(id = R.string.node_pv_title_compact) else stringResource(id = R.string.node_pv_title),
                        valueText = solarData.formattedPvPower,
                        subtitleText = if (solarData.pvPowerW > 10) stringResource(id = R.string.node_pv_active) else stringResource(id = R.string.node_pv_inactive),
                        icon = painterResource(id = R.drawable.ic_sun),
                        accentColor = SolarYellow,
                        isCompact = isCompact
                    )

                    // Central Inverter Hub Badge
                    Box(
                        modifier = Modifier
                            .width(hubWidth)
                            .onGloballyPositioned { coords ->
                                hubCenter = Offset(
                                    coords.positionInRoot().x + coords.size.width / 2f,
                                    coords.positionInRoot().y + coords.size.height / 2f
                                )
                            }
                            .clip(CircleShape)
                            .background(DarkCardBg)
                            .border(if (isCompact) 1.5.dp else 2.dp, DarkCardBorder, CircleShape)
                            .padding(vertical = if (isCompact) 6.dp else 12.dp, horizontal = if (isCompact) 10.dp else 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(id = R.string.hub_inverter_ac),
                                style = typography.labelSmall.copy(
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    fontSize = if (isCompact) 10.sp else 12.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = solarData.formatWattsOrKw(solarData.activePowerW),
                                style = typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary,
                                    fontSize = if (isCompact) 14.sp else 17.sp
                                )
                            )
                        }
                    }

                    // Casa Node Card
                    EnergyNodeCard(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coords ->
                                homeCenter = Offset(
                                    coords.positionInRoot().x + coords.size.width / 2f,
                                    coords.positionInRoot().y + coords.size.height / 2f
                                )
                            },
                        title = if (isCompact) stringResource(id = R.string.node_house_title_compact) else stringResource(id = R.string.node_house_title),
                        valueText = solarData.formattedHousePower,
                        subtitleText = stringResource(id = R.string.node_house_subtitle),
                        icon = painterResource(id = R.drawable.ic_home),
                        accentColor = HouseBlue,
                        isCompact = isCompact
                    )
                }

                // BOTTOM ROW: BATTERY (Left) and RETE (Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(colSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val batStateText = when {
                        !solarData.hasBattery -> stringResource(id = R.string.node_battery_not_present)
                        solarData.batteryPowerW > 10 -> stringResource(id = R.string.node_battery_charging, solarData.formattedBatteryPower)
                        solarData.batteryPowerW < -10 -> stringResource(id = R.string.node_battery_discharging, solarData.formattedBatteryPower)
                        else -> stringResource(id = R.string.node_battery_standby)
                    }
                    val batAccent = if (solarData.isBatteryCharging) BatteryGreen else BatteryCyan

                    EnergyNodeCard(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coords ->
                                batteryCenter = Offset(
                                    coords.positionInRoot().x + coords.size.width / 2f,
                                    coords.positionInRoot().y + coords.size.height / 2f
                                )
                            },
                        title = stringResource(id = R.string.node_battery_title),
                        valueText = solarData.formattedBatterySoc,
                        subtitleText = batStateText,
                        icon = painterResource(id = R.drawable.ic_battery),
                        accentColor = batAccent,
                        batteryProgress = (solarData.batterySoc / 100.0).toFloat(),
                        isCompact = isCompact
                    )

                    // Spacer for Hub alignment
                    Spacer(modifier = Modifier.width(hubWidth))

                    val gridStateText = when {
                        solarData.gridPowerW > 10 -> stringResource(id = R.string.node_grid_export)
                        solarData.gridPowerW < -10 -> stringResource(id = R.string.node_grid_import)
                        else -> stringResource(id = R.string.node_grid_balanced)
                    }
                    val gridAccent = if (solarData.gridPowerW >= 0) GridPurple else GridRed

                    EnergyNodeCard(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coords ->
                                gridCenter = Offset(
                                    coords.positionInRoot().x + coords.size.width / 2f,
                                    coords.positionInRoot().y + coords.size.height / 2f
                                )
                            },
                        title = stringResource(id = R.string.node_grid_title),
                        valueText = solarData.formattedGridPower,
                        subtitleText = gridStateText,
                        icon = painterResource(id = R.drawable.ic_grid),
                        accentColor = gridAccent,
                        isCompact = isCompact
                    )
                }
            }
        }
    }
}

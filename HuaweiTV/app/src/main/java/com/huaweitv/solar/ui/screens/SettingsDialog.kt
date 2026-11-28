package com.huaweitv.solar.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.huaweitv.solar.R
import com.huaweitv.solar.data.model.AppLanguage
import com.huaweitv.solar.data.model.AppSettings
import com.huaweitv.solar.data.model.InverterBrand
import com.huaweitv.solar.data.model.ShellyGen
import com.huaweitv.solar.ui.components.FocusableTvCard
import com.huaweitv.solar.ui.theme.BatteryGreen
import com.huaweitv.solar.ui.theme.DarkCardBg
import com.huaweitv.solar.ui.theme.DarkCardBorder
import com.huaweitv.solar.ui.theme.DarkSurface
import com.huaweitv.solar.ui.theme.GridRed
import com.huaweitv.solar.ui.theme.HouseBlue
import com.huaweitv.solar.ui.theme.LocalTvTypography
import com.huaweitv.solar.ui.theme.SolarYellow
import com.huaweitv.solar.ui.theme.StatusError
import com.huaweitv.solar.ui.theme.StatusMock
import com.huaweitv.solar.ui.theme.TextMuted
import com.huaweitv.solar.ui.theme.TextPrimary
import com.huaweitv.solar.ui.theme.TextSecondary
import com.huaweitv.solar.ui.theme.TvFocusBorderColor
import com.huaweitv.solar.ui.theme.TvFocusCardBg
import kotlinx.coroutines.launch
import java.util.Locale

private enum class ShellyTestPhase {
    IDLE,
    SENDING,
    SUCCESS,
    ERROR
}

/**
 * TV & Smartphone Settings Dialog.
 * Fully localized via Android string resources (IT / EN / DE / FR).
 */
@Composable
fun SettingsDialog(
    currentSettings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
    onTestPowerAlarm: (Double) -> Unit = {},
    onTestShellyOff: suspend (String, Int, ShellyGen) -> Result<Unit> = { _, _, _ -> Result.success(Unit) }
) {
    val typography = LocalTvTypography.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val screenWidthDp = configuration.screenWidthDp

    // 0. Language setting
    var selectedLanguage by remember { mutableStateOf(currentSettings.appLanguage) }

    // 1. Inverter settings
    var inverterBrand by remember { mutableStateOf(currentSettings.inverterBrand) }
    var ipText by remember { mutableStateOf(currentSettings.ipAddress) }
    var portText by remember {
        mutableStateOf((if (currentSettings.port in 1..65535) currentSettings.port else 502).toString())
    }
    var unitIdText by remember {
        mutableStateOf((if (currentSettings.unitId >= 0) currentSettings.unitId else 1).toString())
    }
    var froniusDeviceIdText by remember {
        mutableStateOf((if (currentSettings.froniusDeviceId >= 0) currentSettings.froniusDeviceId else 1).toString())
    }
    var pollInterval by remember {
        mutableIntStateOf(if (currentSettings.pollingIntervalSeconds > 0) currentSettings.pollingIntervalSeconds else 10)
    }
    var mockMode by remember { mutableStateOf(currentSettings.mockMode) }

    // 2. Meter / Contract power alarm
    var meterAlarmEnabled by remember { mutableStateOf(currentSettings.meterAlarmEnabled) }
    var contractPowerText by remember {
        val kw = if (currentSettings.contractPowerKw > 0.0) currentSettings.contractPowerKw else 3.0
        mutableStateOf(String.format(Locale.US, "%.1f", kw))
    }
    var meterAlarmSoundEnabled by remember { mutableStateOf(currentSettings.meterAlarmSoundEnabled) }

    // 3. Shelly load shedding
    var shellySheddingEnabled by remember { mutableStateOf(currentSettings.shellySheddingEnabled) }
    var shelly1Name by remember { mutableStateOf(currentSettings.shelly1Name) }
    var shelly1Ip by remember { mutableStateOf(currentSettings.shelly1Ip) }
    var shelly1ChannelText by remember { mutableStateOf(currentSettings.shelly1Channel.coerceAtLeast(0).toString()) }
    var shelly1Gen by remember { mutableStateOf(currentSettings.shelly1Gen) }
    var shellyDelayText by remember {
        mutableStateOf((if (currentSettings.shellyTriggerDelaySec > 0) currentSettings.shellyTriggerDelaySec else 15).toString())
    }

    var validationError by remember { mutableStateOf<String?>(null) }
    var shellyTestPhase by remember { mutableStateOf(ShellyTestPhase.IDLE) }
    var shellyTestMessage by remember { mutableStateOf<String?>(null) }

    val huaweiPortPresets = listOf("502", "6607")
    val unitPresets = listOf("0", "1", "2")
    val froniusIdPresets = listOf("1", "2")
    val pollingPresets = listOf(5, 10, 15, 30)
    val contractPowerPresets = listOf("3.0", "4.5", "6.0")
    val shellyChannelPresets = listOf("0", "1")
    val shellyDelayPresets = listOf("10", "15", "30")

    fun isValidIpv4(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            if (part.isEmpty() || part.length > 3) return false
            if (part.length > 1 && part.startsWith("0")) return false
            val v = part.toIntOrNull()
            v != null && v in 0..255
        }
    }

    fun validateAndSave() {
        val trimmedIp = ipText.trim()
        if (trimmedIp.isNotEmpty() && !isValidIpv4(trimmedIp)) {
            validationError = context.getString(R.string.err_invalid_inverter_ip)
            return
        }

        val portInt: Int
        val unitInt: Int
        val froniusDevIdInt: Int

        if (inverterBrand == InverterBrand.HUAWEI) {
            val parsedPort = portText.trim().toIntOrNull()
            if (parsedPort == null || parsedPort !in 1..65535) {
                validationError = context.getString(R.string.err_invalid_port)
                return
            }
            val parsedUnit = unitIdText.trim().toIntOrNull()
            if (parsedUnit == null || parsedUnit !in 0..247) {
                validationError = context.getString(R.string.err_invalid_unit_id)
                return
            }
            portInt = parsedPort
            unitInt = parsedUnit
            froniusDevIdInt = froniusDeviceIdText.trim().toIntOrNull()?.coerceAtLeast(0) ?: 1
        } else {
            val parsedDevId = froniusDeviceIdText.trim().toIntOrNull()
            if (parsedDevId == null || parsedDevId !in 0..99) {
                validationError = context.getString(R.string.err_invalid_fronius_id)
                return
            }
            portInt = portText.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: 502
            unitInt = unitIdText.trim().toIntOrNull()?.takeIf { it in 0..247 } ?: 1
            froniusDevIdInt = parsedDevId
        }

        val contractKw = contractPowerText.trim().replace(',', '.').toDoubleOrNull()
        if (contractKw == null || contractKw <= 0.0 || contractKw > 100.0) {
            validationError = context.getString(R.string.err_invalid_contract_kw)
            return
        }

        val trimmedShellyIp = shelly1Ip.trim()
        if (trimmedShellyIp.isNotEmpty() && !isValidIpv4(trimmedShellyIp)) {
            validationError = context.getString(R.string.err_invalid_shelly_ip)
            return
        }
        if (shellySheddingEnabled && trimmedShellyIp.isEmpty()) {
            validationError = context.getString(R.string.err_missing_shelly_ip)
            return
        }

        val shellyChannelInt = shelly1ChannelText.trim().toIntOrNull()
        if (shellyChannelInt == null || shellyChannelInt !in 0..3) {
            validationError = context.getString(R.string.err_invalid_shelly_channel)
            return
        }

        val shellyDelayInt = shellyDelayText.trim().toIntOrNull()
        if (shellyDelayInt == null || shellyDelayInt !in 1..300) {
            validationError = context.getString(R.string.err_invalid_shelly_delay)
            return
        }

        validationError = null
        onSave(
            AppSettings(
                inverterBrand = inverterBrand,
                ipAddress = trimmedIp,
                port = portInt,
                unitId = unitInt,
                froniusDeviceId = froniusDevIdInt,
                pollingIntervalSeconds = pollInterval,
                mockMode = mockMode,
                languageCode = selectedLanguage.code,
                meterAlarmEnabled = meterAlarmEnabled,
                contractPowerKw = contractKw,
                meterAlarmSoundEnabled = meterAlarmSoundEnabled,
                shellySheddingEnabled = shellySheddingEnabled,
                shelly1Name = shelly1Name.trim(),
                shelly1Ip = trimmedShellyIp,
                shelly1Channel = shellyChannelInt,
                shelly1Gen = shelly1Gen,
                shellyTriggerDelaySec = shellyDelayInt
            )
        )
        onDismiss()
    }

    fun triggerPowerAlarmTest() {
        val contractKw = contractPowerText.trim().replace(',', '.').toDoubleOrNull()
        if (contractKw == null || contractKw <= 0.0 || contractKw > 100.0) {
            validationError = context.getString(R.string.err_invalid_contract_kw)
            return
        }
        validationError = null
        onTestPowerAlarm(contractKw)
        onDismiss()
    }

    fun triggerShellyOffTest() {
        if (shellyTestPhase == ShellyTestPhase.SENDING) return
        val trimmedShellyIp = shelly1Ip.trim()
        if (trimmedShellyIp.isEmpty()) {
            val msg = context.getString(R.string.err_missing_shelly_ip_test)
            shellyTestPhase = ShellyTestPhase.ERROR
            shellyTestMessage = msg
            return
        }
        if (!isValidIpv4(trimmedShellyIp)) {
            val msg = context.getString(R.string.err_invalid_shelly_ip)
            shellyTestPhase = ShellyTestPhase.ERROR
            shellyTestMessage = msg
            return
        }
        val channelInt = shelly1ChannelText.trim().toIntOrNull()
        if (channelInt == null || channelInt !in 0..3) {
            val msg = context.getString(R.string.err_invalid_shelly_channel)
            shellyTestPhase = ShellyTestPhase.ERROR
            shellyTestMessage = msg
            return
        }

        validationError = null
        shellyTestPhase = ShellyTestPhase.SENDING
        shellyTestMessage = context.getString(R.string.settings_test_shelly_sending, trimmedShellyIp)

        val genToTest = shelly1Gen
        coroutineScope.launch {
            val result = onTestShellyOff(trimmedShellyIp, channelInt, genToTest)
            if (result.isSuccess) {
                shellyTestPhase = ShellyTestPhase.SUCCESS
                shellyTestMessage = context.getString(R.string.settings_test_shelly_success, trimmedShellyIp)
            } else {
                val reason = result.exceptionOrNull()?.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.guard_shelly_unreachable)
                shellyTestPhase = ShellyTestPhase.ERROR
                shellyTestMessage = context.getString(R.string.settings_test_shelly_error, reason)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth(if (screenWidthDp < 700) 0.98f else 0.88f)
                .clip(RoundedCornerShape(18.dp))
                .background(DarkSurface)
                .border(2.dp, TvFocusBorderColor.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .padding(if (isPortrait) 16.dp else 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Title
                Text(
                    text = stringResource(id = R.string.settings_title),
                    style = typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.8.sp,
                        fontSize = if (isPortrait) 16.sp else 18.sp
                    )
                )

                // Validation Error Alert Banner
                if (validationError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(StatusError.copy(alpha = 0.2f))
                            .border(1.dp, StatusError, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.err_prefix, validationError!!),
                            style = typography.bodyMedium.copy(
                                color = StatusError,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // =====================================================
                // 0. SEZIONE LINGUA APPLICAZIONE
                // =====================================================
                SectionHeader(title = stringResource(id = R.string.settings_section_language), accentColor = TvFocusBorderColor)

                Column {
                    Text(
                        text = stringResource(id = R.string.settings_label_language),
                        style = typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            LanguageOptionCard(
                                modifier = Modifier.weight(1f),
                                language = lang,
                                isSelected = selectedLanguage == lang,
                                onSelect = { selectedLanguage = lang }
                            )
                        }
                    }
                }

                // =====================================================
                // 1. SEZIONE INVERTER DINAMICA
                // =====================================================
                SectionHeader(title = stringResource(id = R.string.settings_section_inverter), accentColor = SolarYellow)

                Column {
                    Text(
                        text = stringResource(id = R.string.settings_label_inverter_brand),
                        style = typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isPortrait) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            InverterBrand.entries.forEach { brand ->
                                InverterBrandOptionCard(
                                    brand = brand,
                                    isSelected = inverterBrand == brand,
                                    onSelect = {
                                        inverterBrand = brand
                                        validationError = null
                                    }
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InverterBrand.entries.forEach { brand ->
                                InverterBrandOptionCard(
                                    modifier = Modifier.weight(1f),
                                    brand = brand,
                                    isSelected = inverterBrand == brand,
                                    onSelect = {
                                        inverterBrand = brand
                                        validationError = null
                                    }
                                )
                            }
                        }
                    }
                }

                // IP Address (Always visible)
                TvInputField(
                    label = stringResource(id = R.string.settings_label_ip),
                    value = ipText,
                    placeholder = stringResource(id = R.string.settings_hint_ip),
                    onValueChange = {
                        ipText = it
                        validationError = null
                    },
                    keyboardType = KeyboardType.Text,
                    focusedBorderColor = SolarYellow
                )

                // Dynamic Fields based on selected InverterBrand
                if (inverterBrand == InverterBrand.HUAWEI) {
                    if (isPortrait) {
                        NumericConfigFieldWithPresets(
                            label = stringResource(id = R.string.settings_label_port),
                            value = portText,
                            maxLength = 5,
                            presets = huaweiPortPresets,
                            presetPrefix = "",
                            onValueChange = { portText = it; validationError = null },
                            onSelectPreset = { portText = it; validationError = null }
                        )
                        NumericConfigFieldWithPresets(
                            label = stringResource(id = R.string.settings_label_unit_id),
                            value = unitIdText,
                            maxLength = 3,
                            presets = unitPresets,
                            presetPrefix = "ID ",
                            onValueChange = { unitIdText = it; validationError = null },
                            onSelectPreset = { unitIdText = it; validationError = null }
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            NumericConfigFieldWithPresets(
                                modifier = Modifier.weight(1f),
                                label = stringResource(id = R.string.settings_label_port),
                                value = portText,
                                maxLength = 5,
                                presets = huaweiPortPresets,
                                presetPrefix = "",
                                onValueChange = { portText = it; validationError = null },
                                onSelectPreset = { portText = it; validationError = null }
                            )
                            NumericConfigFieldWithPresets(
                                modifier = Modifier.weight(1f),
                                label = stringResource(id = R.string.settings_label_unit_id),
                                value = unitIdText,
                                maxLength = 3,
                                presets = unitPresets,
                                presetPrefix = "ID ",
                                onValueChange = { unitIdText = it; validationError = null },
                                onSelectPreset = { unitIdText = it; validationError = null }
                            )
                        }
                    }
                } else {
                    NumericConfigFieldWithPresets(
                        label = stringResource(id = R.string.settings_label_fronius_id),
                        value = froniusDeviceIdText,
                        maxLength = 2,
                        presets = froniusIdPresets,
                        presetPrefix = "ID ",
                        onValueChange = { froniusDeviceIdText = it; validationError = null },
                        onSelectPreset = { froniusDeviceIdText = it; validationError = null }
                    )
                }

                // Polling Interval
                Column {
                    Text(
                        text = stringResource(id = R.string.settings_label_poll, pollInterval),
                        style = typography.titleMedium.copy(fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pollingPresets.forEach { seconds ->
                            val isSelected = pollInterval == seconds
                            FocusableTvCard(
                                modifier = Modifier.weight(1f),
                                onClick = { pollInterval = seconds },
                                shape = RoundedCornerShape(6.dp),
                                focusedBorderColor = TvFocusBorderColor,
                                unfocusedBorderColor = if (isSelected) TvFocusBorderColor else DarkCardBorder
                            ) { isFocused ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${seconds}s",
                                        style = typography.bodyMedium.copy(
                                            color = if (isFocused || isSelected) TextPrimary else TextSecondary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Mock Mode Toggle
                ToggleSettingRow(
                    title = stringResource(id = R.string.settings_mock_title),
                    subtitle = stringResource(id = R.string.settings_mock_subtitle),
                    checked = mockMode,
                    accentColor = StatusMock,
                    onToggle = { mockMode = !mockMode }
                )

                // =====================================================
                // 2. SEZIONE CONTATORE / ALLARME SUPERAMENTO SOGLIA
                // =====================================================
                SectionHeader(title = stringResource(id = R.string.settings_section_meter), accentColor = HouseBlue)

                ToggleSettingRow(
                    title = stringResource(id = R.string.settings_meter_alarm_title),
                    subtitle = stringResource(id = R.string.settings_meter_alarm_subtitle),
                    checked = meterAlarmEnabled,
                    accentColor = HouseBlue,
                    onToggle = { meterAlarmEnabled = !meterAlarmEnabled }
                )

                DecimalConfigFieldWithPresets(
                    label = stringResource(id = R.string.settings_label_contract_kw),
                    value = contractPowerText,
                    placeholder = stringResource(id = R.string.settings_hint_contract_kw),
                    presets = contractPowerPresets,
                    focusedBorderColor = HouseBlue,
                    onValueChange = { contractPowerText = it; validationError = null },
                    onSelectPreset = { contractPowerText = it; validationError = null }
                )

                ToggleSettingRow(
                    title = stringResource(id = R.string.settings_meter_sound_title),
                    subtitle = stringResource(id = R.string.settings_meter_sound_subtitle),
                    checked = meterAlarmSoundEnabled,
                    accentColor = HouseBlue,
                    onToggle = { meterAlarmSoundEnabled = !meterAlarmSoundEnabled }
                )

                FocusableTvCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { triggerPowerAlarmTest() },
                    shape = RoundedCornerShape(8.dp),
                    focusedBorderColor = GridRed,
                    unfocusedBorderColor = GridRed.copy(alpha = 0.65f)
                ) { isFocused ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.settings_btn_test_alarm),
                                style = typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isFocused) TextPrimary else GridRed
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(id = R.string.settings_test_alarm_hint),
                                style = typography.bodyMedium.copy(fontSize = 10.sp, color = TextMuted),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // =====================================================
                // 3. SEZIONE LOAD SHEDDING LOCALE VIA SHELLY
                // =====================================================
                SectionHeader(title = stringResource(id = R.string.settings_section_shelly), accentColor = BatteryGreen)

                ToggleSettingRow(
                    title = stringResource(id = R.string.settings_shelly_master_title),
                    subtitle = stringResource(id = R.string.settings_shelly_master_subtitle),
                    checked = shellySheddingEnabled,
                    accentColor = BatteryGreen,
                    onToggle = { shellySheddingEnabled = !shellySheddingEnabled }
                )

                if (isPortrait) {
                    TvInputField(
                        label = stringResource(id = R.string.settings_label_shelly_name),
                        value = shelly1Name,
                        placeholder = stringResource(id = R.string.settings_hint_shelly_name),
                        onValueChange = { shelly1Name = it; validationError = null },
                        focusedBorderColor = BatteryGreen
                    )
                    TvInputField(
                        label = stringResource(id = R.string.settings_label_shelly_ip),
                        value = shelly1Ip,
                        placeholder = stringResource(id = R.string.settings_hint_shelly_ip),
                        onValueChange = { shelly1Ip = it; validationError = null },
                        focusedBorderColor = BatteryGreen
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        TvInputField(
                            modifier = Modifier.weight(1f),
                            label = stringResource(id = R.string.settings_label_shelly_name),
                            value = shelly1Name,
                            placeholder = stringResource(id = R.string.settings_hint_shelly_name),
                            onValueChange = { shelly1Name = it; validationError = null },
                            focusedBorderColor = BatteryGreen
                        )
                        TvInputField(
                            modifier = Modifier.weight(1f),
                            label = stringResource(id = R.string.settings_label_shelly_ip),
                            value = shelly1Ip,
                            placeholder = stringResource(id = R.string.settings_hint_shelly_ip),
                            onValueChange = { shelly1Ip = it; validationError = null },
                            focusedBorderColor = BatteryGreen
                        )
                    }
                }

                // Shelly Generation Selector (GEN1 vs GEN2)
                Column {
                    Text(
                        text = stringResource(id = R.string.settings_label_shelly_gen),
                        style = typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShellyGen.entries.forEach { gen ->
                            val isSelected = shelly1Gen == gen
                            FocusableTvCard(
                                modifier = Modifier.weight(1f),
                                onClick = { shelly1Gen = gen },
                                shape = RoundedCornerShape(8.dp),
                                focusedBorderColor = BatteryGreen,
                                unfocusedBorderColor = if (isSelected) BatteryGreen else DarkCardBorder
                            ) { isFocused ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = gen.displayName,
                                        style = typography.bodyMedium.copy(
                                            color = if (isFocused || isSelected) TextPrimary else TextSecondary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                if (isPortrait) {
                    NumericConfigFieldWithPresets(
                        label = stringResource(id = R.string.settings_label_shelly_channel),
                        value = shelly1ChannelText,
                        maxLength = 1,
                        presets = shellyChannelPresets,
                        presetPrefix = "CH ",
                        focusedBorderColor = BatteryGreen,
                        onValueChange = { shelly1ChannelText = it; validationError = null },
                        onSelectPreset = { shelly1ChannelText = it; validationError = null }
                    )
                    NumericConfigFieldWithPresets(
                        label = stringResource(id = R.string.settings_label_shelly_delay),
                        value = shellyDelayText,
                        maxLength = 3,
                        presets = shellyDelayPresets,
                        presetPrefix = "",
                        presetSuffix = "s",
                        focusedBorderColor = BatteryGreen,
                        onValueChange = { shellyDelayText = it; validationError = null },
                        onSelectPreset = { shellyDelayText = it; validationError = null }
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        NumericConfigFieldWithPresets(
                            modifier = Modifier.weight(1f),
                            label = stringResource(id = R.string.settings_label_shelly_channel),
                            value = shelly1ChannelText,
                            maxLength = 1,
                            presets = shellyChannelPresets,
                            presetPrefix = "CH ",
                            focusedBorderColor = BatteryGreen,
                            onValueChange = { shelly1ChannelText = it; validationError = null },
                            onSelectPreset = { shelly1ChannelText = it; validationError = null }
                        )
                        NumericConfigFieldWithPresets(
                            modifier = Modifier.weight(1f),
                            label = stringResource(id = R.string.settings_label_shelly_delay),
                            value = shellyDelayText,
                            maxLength = 3,
                            presets = shellyDelayPresets,
                            presetPrefix = "",
                            presetSuffix = "s",
                            focusedBorderColor = BatteryGreen,
                            onValueChange = { shellyDelayText = it; validationError = null },
                            onSelectPreset = { shellyDelayText = it; validationError = null }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Shelly manual OFF test button + inline status
                FocusableTvCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { triggerShellyOffTest() },
                    shape = RoundedCornerShape(8.dp),
                    focusedBorderColor = BatteryGreen,
                    unfocusedBorderColor = BatteryGreen.copy(alpha = 0.65f)
                ) { isFocused ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.settings_btn_test_shelly),
                            style = typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isFocused) TextPrimary else BatteryGreen,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (shellyTestMessage != null) {
                    val statusColor = when (shellyTestPhase) {
                        ShellyTestPhase.SUCCESS -> BatteryGreen
                        ShellyTestPhase.ERROR -> StatusError
                        ShellyTestPhase.SENDING -> SolarYellow
                        ShellyTestPhase.IDLE -> TextSecondary
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusColor.copy(alpha = 0.14f))
                            .border(1.dp, statusColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = shellyTestMessage!!,
                            style = typography.bodyMedium.copy(
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 5. Action Buttons (Cancel / Save)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FocusableTvCard(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        focusedBorderColor = TextSecondary
                    ) { isFocused ->
                        Text(
                            text = stringResource(id = R.string.settings_btn_cancel),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isFocused) TextPrimary else TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    FocusableTvCard(
                        onClick = { validateAndSave() },
                        shape = RoundedCornerShape(8.dp),
                        focusedBorderColor = BatteryGreen
                    ) { isFocused ->
                        Text(
                            text = stringResource(id = R.string.settings_btn_save),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            style = typography.bodyMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isFocused) TextPrimary else BatteryGreen,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, accentColor: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text = title,
            style = LocalTvTypography.current.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                fontSize = 12.sp,
                letterSpacing = 0.8.sp
            )
        )
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DarkCardBorder)
        )
    }
}

@Composable
private fun LanguageOptionCard(
    language: AppLanguage,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val flagDrawableId = when (language) {
        AppLanguage.ITALIAN -> R.drawable.ic_flag_it
        AppLanguage.ENGLISH -> R.drawable.ic_flag_en
        AppLanguage.GERMAN -> R.drawable.ic_flag_de
        AppLanguage.FRENCH -> R.drawable.ic_flag_fr
    }

    FocusableTvCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onSelect,
        shape = RoundedCornerShape(8.dp),
        focusedBorderColor = TvFocusBorderColor,
        unfocusedBorderColor = if (isSelected) TvFocusBorderColor else DarkCardBorder
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSelected && !isFocused) TvFocusBorderColor.copy(alpha = 0.14f) else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = flagDrawableId),
                contentDescription = language.nativeLabel,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.size(width = 24.dp, height = 16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = language.shortCode,
                style = LocalTvTypography.current.bodyMedium.copy(
                    color = if (isFocused || isSelected) TextPrimary else TextSecondary,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InverterBrandOptionCard(
    brand: InverterBrand,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (brand == InverterBrand.FRONIUS) {
        stringResource(id = R.string.settings_brand_fronius)
    } else {
        stringResource(id = R.string.settings_brand_huawei)
    }

    FocusableTvCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onSelect,
        shape = RoundedCornerShape(8.dp),
        focusedBorderColor = SolarYellow,
        unfocusedBorderColor = if (isSelected) SolarYellow else DarkCardBorder
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = LocalTvTypography.current.bodyMedium.copy(
                    color = if (isFocused || isSelected) TextPrimary else TextSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color,
    onToggle: () -> Unit
) {
    val typography = LocalTvTypography.current
    FocusableTvCard(
        onClick = onToggle,
        shape = RoundedCornerShape(8.dp),
        focusedBorderColor = accentColor,
        unfocusedBorderColor = if (checked) accentColor else DarkCardBorder
    ) { _ ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (checked) accentColor else TextPrimary
                    )
                )
                Text(
                    text = subtitle,
                    style = typography.bodyMedium.copy(fontSize = 10.sp, color = TextMuted)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (checked) accentColor else Color(0xFF334155))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (checked) stringResource(id = R.string.settings_state_on) else stringResource(id = R.string.settings_state_off),
                    style = typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun NumericConfigFieldWithPresets(
    label: String,
    value: String,
    maxLength: Int,
    presets: List<String>,
    presetPrefix: String,
    onValueChange: (String) -> Unit,
    onSelectPreset: (String) -> Unit,
    modifier: Modifier = Modifier,
    presetSuffix: String = "",
    focusedBorderColor: Color = SolarYellow
) {
    val typography = LocalTvTypography.current
    Column(modifier = modifier) {
        TvInputField(
            label = label,
            value = value,
            onValueChange = { input ->
                if (input.length <= maxLength && input.all { it.isDigit() }) {
                    onValueChange(input)
                }
            },
            keyboardType = KeyboardType.Number,
            focusedBorderColor = focusedBorderColor
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.settings_label_preset),
                style = typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
            )
            presets.forEach { preset ->
                FocusableTvCard(
                    onClick = { onSelectPreset(preset) },
                    shape = RoundedCornerShape(6.dp),
                    focusedBorderColor = focusedBorderColor,
                    unfocusedBorderColor = if (value == preset) focusedBorderColor else DarkCardBorder
                ) { isFocused ->
                    Text(
                        text = "$presetPrefix$preset$presetSuffix",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = typography.bodyMedium.copy(
                            color = if (isFocused || value == preset) TextPrimary else TextSecondary,
                            fontWeight = if (value == preset) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DecimalConfigFieldWithPresets(
    label: String,
    value: String,
    placeholder: String,
    presets: List<String>,
    focusedBorderColor: Color,
    onValueChange: (String) -> Unit,
    onSelectPreset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = LocalTvTypography.current
    Column(modifier = modifier) {
        TvInputField(
            label = label,
            value = value,
            placeholder = placeholder,
            onValueChange = { input ->
                if (input.length <= 6 && input.all { it.isDigit() || it == '.' || it == ',' }) {
                    onValueChange(input)
                }
            },
            keyboardType = KeyboardType.Decimal,
            focusedBorderColor = focusedBorderColor
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.settings_label_preset),
                style = typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
            )
            presets.forEach { preset ->
                val isSelected = value.trim().replace(',', '.') == preset
                FocusableTvCard(
                    onClick = { onSelectPreset(preset) },
                    shape = RoundedCornerShape(6.dp),
                    focusedBorderColor = focusedBorderColor,
                    unfocusedBorderColor = if (isSelected) focusedBorderColor else DarkCardBorder
                ) { isFocused ->
                    Text(
                        text = "$preset kW",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = typography.bodyMedium.copy(
                            color = if (isFocused || isSelected) TextPrimary else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Focusable, styled text input field tailored for TV D-Pad & Mobile Touch interaction.
 */
@Composable
private fun TvInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    focusedBorderColor: Color = SolarYellow,
    unfocusedBorderColor: Color = DarkCardBorder
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = LocalTvTypography.current.labelSmall.copy(
                color = if (isFocused) focusedBorderColor else TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        )
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) TvFocusCardBg else DarkCardBg)
                .border(
                    BorderStroke(if (isFocused) 2.dp else 1.dp, if (isFocused) focusedBorderColor else unfocusedBorderColor),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = LocalTvTypography.current.bodyLarge.copy(
                        color = TextMuted,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp
                    )
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                cursorBrush = SolidColor(focusedBorderColor),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Next
                ),
                textStyle = LocalTvTypography.current.bodyLarge.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
            )
        }
    }
}

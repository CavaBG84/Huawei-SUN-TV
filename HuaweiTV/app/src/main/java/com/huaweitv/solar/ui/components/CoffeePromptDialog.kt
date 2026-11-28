package com.huaweitv.solar.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.huaweitv.solar.R
import com.huaweitv.solar.data.coffee.CoffeePromptManager
import com.huaweitv.solar.data.coffee.QrCodeEncoder
import com.huaweitv.solar.ui.theme.BatteryGreen
import com.huaweitv.solar.ui.theme.DarkCardBorder
import com.huaweitv.solar.ui.theme.DarkSurface
import com.huaweitv.solar.ui.theme.HouseBlue
import com.huaweitv.solar.ui.theme.LocalTvTypography
import com.huaweitv.solar.ui.theme.SolarYellow
import com.huaweitv.solar.ui.theme.TextMuted
import com.huaweitv.solar.ui.theme.TextPrimary
import com.huaweitv.solar.ui.theme.TextSecondary
import com.huaweitv.solar.ui.theme.TvFocusBorderColor
import kotlinx.coroutines.delay

private enum class CoffeeDialogStep {
    PROMPT,
    CONFIRM,
    THANK_YOU
}

/**
 * TV-friendly & touch-friendly voluntary "Offrimi un caffè" (PayPal) prompt dialog.
 * Supports D-Pad navigation, local QR code rendering, clickable PayPal link,
 * confirmation step, and localized final thank-you message.
 */
@Composable
fun CoffeePromptDialog(
    onOpenPayPal: () -> Unit,
    onSnoozeClose: () -> Unit,
    onConfirmDismissForever: () -> Unit,
    onThankYouFinished: () -> Unit
) {
    val typography = LocalTvTypography.current
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val screenWidthDp = configuration.screenWidthDp

    var step by remember { mutableStateOf(CoffeeDialogStep.PROMPT) }
    val qrMatrix = remember { QrCodeEncoder.encode(CoffeePromptManager.PAYPAL_URL) }

    LaunchedEffect(step) {
        if (step == CoffeeDialogStep.THANK_YOU) {
            delay(3000L)
            onThankYouFinished()
        }
    }

    Dialog(
        onDismissRequest = {
            when (step) {
                CoffeeDialogStep.PROMPT -> onSnoozeClose()
                CoffeeDialogStep.CONFIRM -> step = CoffeeDialogStep.PROMPT
                CoffeeDialogStep.THANK_YOU -> onThankYouFinished()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(if (screenWidthDp < 700) 0.94f else 0.78f)
                .clip(RoundedCornerShape(18.dp))
                .background(DarkSurface)
                .border(2.dp, SolarYellow.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                .padding(if (isPortrait) 16.dp else 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (step) {
                    CoffeeDialogStep.PROMPT -> {
                        // Header: Coffee Icon + Title + Close X button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_coffee),
                                    contentDescription = null,
                                    tint = SolarYellow,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(id = R.string.coffee_title),
                                    style = typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SolarYellow,
                                        fontSize = if (isPortrait) 14.sp else 17.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            FocusableTvCard(
                                onClick = onSnoozeClose,
                                shape = RoundedCornerShape(8.dp),
                                focusedBorderColor = TvFocusBorderColor,
                                unfocusedBorderColor = DarkCardBorder
                            ) { isFocused ->
                                Text(
                                    text = "✕",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = typography.titleMedium.copy(
                                        color = if (isFocused) TextPrimary else TextSecondary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }

                        if (isPortrait) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CoffeePromptTextAndActions(
                                    modifier = Modifier.fillMaxWidth(),
                                    onOpenPayPal = onOpenPayPal,
                                    onAlreadyBought = { step = CoffeeDialogStep.CONFIRM }
                                )
                                CoffeeQrCodeCard(matrix = qrMatrix)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CoffeePromptTextAndActions(
                                    modifier = Modifier.weight(1f),
                                    onOpenPayPal = onOpenPayPal,
                                    onAlreadyBought = { step = CoffeeDialogStep.CONFIRM }
                                )
                                CoffeeQrCodeCard(matrix = qrMatrix)
                            }
                        }
                    }

                    CoffeeDialogStep.CONFIRM -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_coffee),
                                contentDescription = null,
                                tint = SolarYellow,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(id = R.string.coffee_confirm_title),
                                style = typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SolarYellow,
                                    fontSize = 16.sp
                                )
                            )
                        }

                        Text(
                            text = stringResource(id = R.string.coffee_confirm_message),
                            style = typography.bodyLarge.copy(
                                color = TextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FocusableTvCard(
                                onClick = { step = CoffeeDialogStep.PROMPT },
                                shape = RoundedCornerShape(8.dp),
                                focusedBorderColor = TextSecondary,
                                unfocusedBorderColor = DarkCardBorder
                            ) { isFocused ->
                                Text(
                                    text = stringResource(id = R.string.coffee_confirm_cancel),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = typography.bodyMedium.copy(
                                        color = if (isFocused) TextPrimary else TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            FocusableTvCard(
                                onClick = {
                                    onConfirmDismissForever()
                                    step = CoffeeDialogStep.THANK_YOU
                                },
                                shape = RoundedCornerShape(8.dp),
                                focusedBorderColor = BatteryGreen,
                                unfocusedBorderColor = BatteryGreen.copy(alpha = 0.7f)
                            ) { isFocused ->
                                Text(
                                    text = stringResource(id = R.string.coffee_confirm_yes),
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                    style = typography.bodyMedium.copy(
                                        color = if (isFocused) TextPrimary else BatteryGreen,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }

                    CoffeeDialogStep.THANK_YOU -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_coffee),
                                contentDescription = null,
                                tint = BatteryGreen,
                                modifier = Modifier.size(34.dp)
                            )

                            Text(
                                text = stringResource(id = R.string.coffee_thank_you_message),
                                textAlign = TextAlign.Center,
                                style = typography.titleMedium.copy(
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                )
                            )

                            FocusableTvCard(
                                onClick = onThankYouFinished,
                                shape = RoundedCornerShape(8.dp),
                                focusedBorderColor = BatteryGreen,
                                unfocusedBorderColor = BatteryGreen.copy(alpha = 0.7f)
                            ) { isFocused ->
                                Text(
                                    text = stringResource(id = R.string.coffee_btn_ok),
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                    style = typography.bodyMedium.copy(
                                        color = if (isFocused) TextPrimary else BatteryGreen,
                                        fontWeight = FontWeight.ExtraBold,
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
}

@Composable
private fun CoffeePromptTextAndActions(
    onOpenPayPal: () -> Unit,
    onAlreadyBought: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = LocalTvTypography.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(id = R.string.coffee_message),
            style = typography.bodyMedium.copy(
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        )

        FocusableTvCard(
            onClick = onOpenPayPal,
            shape = RoundedCornerShape(8.dp),
            focusedBorderColor = HouseBlue,
            unfocusedBorderColor = HouseBlue.copy(alpha = 0.45f)
        ) { isFocused ->
            Text(
                text = CoffeePromptManager.PAYPAL_URL,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = typography.bodyMedium.copy(
                    color = if (isFocused) TextPrimary else HouseBlue,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusableTvCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenPayPal,
                shape = RoundedCornerShape(8.dp),
                focusedBorderColor = SolarYellow,
                unfocusedBorderColor = SolarYellow.copy(alpha = 0.75f)
            ) { isFocused ->
                Text(
                    text = stringResource(id = R.string.coffee_btn_paypal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    textAlign = TextAlign.Center,
                    style = typography.bodyMedium.copy(
                        color = if (isFocused) TextPrimary else SolarYellow,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FocusableTvCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAlreadyBought,
                shape = RoundedCornerShape(8.dp),
                focusedBorderColor = TvFocusBorderColor,
                unfocusedBorderColor = DarkCardBorder
            ) { isFocused ->
                Text(
                    text = stringResource(id = R.string.coffee_btn_already),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    textAlign = TextAlign.Center,
                    style = typography.bodyMedium.copy(
                        color = if (isFocused) TextPrimary else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CoffeeQrCodeCard(
    matrix: Array<BooleanArray>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .padding(10.dp)
        ) {
            Canvas(modifier = Modifier.size(132.dp)) {
                val count = matrix.size
                if (count <= 0) return@Canvas
                val moduleSize = minOf(size.width, size.height) / count
                for (r in 0 until count) {
                    for (c in 0 until count) {
                        if (matrix[r][c]) {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(c * moduleSize, r * moduleSize),
                                size = Size(moduleSize + 0.5f, moduleSize + 0.5f)
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = stringResource(id = R.string.coffee_qr_caption),
            style = LocalTvTypography.current.labelSmall.copy(
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            textAlign = TextAlign.Center
        )
    }
}

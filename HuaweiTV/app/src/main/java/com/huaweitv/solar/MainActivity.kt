package com.huaweitv.solar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.huaweitv.solar.data.coffee.CoffeePromptManager
import com.huaweitv.solar.data.repository.SettingsRepository
import com.huaweitv.solar.data.repository.SolarRepository
import com.huaweitv.solar.ui.screens.DashboardScreen
import com.huaweitv.solar.ui.theme.HuaweiTvTheme
import com.huaweitv.solar.ui.viewmodel.SolarViewModel
import com.huaweitv.solar.ui.viewmodel.SolarViewModelFactory
import com.huaweitv.solar.util.LocaleHelper

/**
 * Native Android TV Activity for Huawei Solar & Luna2000 monitoring.
 */
class MainActivity : ComponentActivity() {

    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val solarRepository by lazy { SolarRepository(settingsRepository, lifecycleScope) }
    private val coffeePromptManager by lazy { CoffeePromptManager(applicationContext) }

    private val viewModel: SolarViewModel by viewModels {
        SolarViewModelFactory(solarRepository, settingsRepository, coffeePromptManager)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on continuously for dashboard monitoring on TV
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val initialSettings = settingsRepository.loadSettings()
        LocaleHelper.applyLanguage(applicationContext, initialSettings.languageCode)

        // If IP is not configured and not in mock mode, open settings directly for initial setup
        if (initialSettings.ipAddress.isBlank() && !initialSettings.mockMode) {
            viewModel.openSettings()
        }

        setContent {
            HuaweiTvTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                DashboardScreen(
                    uiState = uiState,
                    onOpenSettings = { viewModel.openSettings() },
                    onCloseSettings = { viewModel.closeSettings() },
                    onSaveSettings = { newSettings ->
                        val languageChanged = viewModel.uiState.value.settings.languageCode != newSettings.languageCode
                        viewModel.saveSettings(newSettings)
                        if (languageChanged) {
                            LocaleHelper.applyLanguage(applicationContext, newSettings.languageCode)
                            recreate()
                        }
                    },
                    onMuteAlarm = { viewModel.mutePowerGuardAlarm() },
                    onResetShellyShed = { viewModel.resetShellyShed() },
                    onTestPowerAlarm = { contractKw -> viewModel.startPowerAlarmTest(contractKw) },
                    onTestShellyOff = { ip, channel, gen -> viewModel.testShellyTurnOff(ip, channel, gen) },
                    onOpenCoffeePayPal = { openPayPalUrl() },
                    onSnoozeCoffeePrompt = { viewModel.snoozeCoffeePrompt() },
                    onConfirmCoffeeDismissForever = { viewModel.dismissCoffeePromptForever() },
                    onCoffeeThankYouFinished = { viewModel.closeCoffeePromptAfterThankYou() }
                )
            }
        }
    }

    private fun openPayPalUrl() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(CoffeePromptManager.PAYPAL_URL))
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Fire TV / Android TV devices without a web browser still display the scannable QR code.
        } catch (_: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        solarRepository.resume()
        viewModel.checkCoffeePromptIfNeeded()
    }

    override fun onStop() {
        super.onStop()
        // Release socket cleanly so Huawei single-client limit is never locked in background
        solarRepository.disconnect()
    }

    /**
     * Handle TV remote control hardware keys (Menu / Settings / Back).
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS -> {
                viewModel.openSettings()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (viewModel.uiState.value.isSettingsOpen) {
                    viewModel.closeSettings()
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}

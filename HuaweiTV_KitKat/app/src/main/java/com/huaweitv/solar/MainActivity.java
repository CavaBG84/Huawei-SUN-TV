package com.huaweitv.solar;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.huaweitv.solar.coffee.CoffeePromptManager;
import com.huaweitv.solar.model.AppLanguage;
import com.huaweitv.solar.model.AppSettings;
import com.huaweitv.solar.model.SolarTelemetry;
import com.huaweitv.solar.powerguard.AlarmSoundController;
import com.huaweitv.solar.powerguard.PowerBand;
import com.huaweitv.solar.powerguard.PowerGuardEvaluation;
import com.huaweitv.solar.powerguard.PowerGuardManager;
import com.huaweitv.solar.powerguard.PowerGuardState;
import com.huaweitv.solar.powerguard.ShellyStatusKind;
import com.huaweitv.solar.repository.SettingsRepository;
import com.huaweitv.solar.repository.SolarRepository;
import com.huaweitv.solar.shelly.ShellyController;
import com.huaweitv.solar.ui.CoffeePromptDialog;
import com.huaweitv.solar.ui.EnergyFlowView;
import com.huaweitv.solar.ui.SettingsDialog;
import com.huaweitv.solar.util.LocaleHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Main Activity for SolarDeck on Android 4.4.4 KitKat (API 19) through Android 15.
 * Pure Java, ZERO external dependencies.
 */
public class MainActivity extends Activity implements SolarRepository.SolarListener {

    private SettingsRepository settingsRepository;
    private SolarRepository solarRepository;
    private AppSettings currentSettings;

    private final PowerGuardManager powerGuardManager = new PowerGuardManager();
    private final AlarmSoundController alarmSoundController = new AlarmSoundController();
    private final ShellyController shellyController = new ShellyController();
    private CoffeePromptManager coffeePromptManager;
    private CoffeePromptDialog activeCoffeeDialog;
    private SettingsDialog activeSettingsDialog;

    private EnergyFlowView energyFlowView;
    private TextView tvConnectionStatus;
    private TextView tvModelSubtitle;
    private TextView tvLastUpdate;
    private TextView tvClock;
    private TextView tvDailyYield;
    private TextView tvAutonomyPercentage;
    private TextView tvAutonomySubtitle;

    private LinearLayout layoutPowerGuardBanner;
    private TextView tvGuardTitle;
    private TextView tvGuardDetails;
    private Button btnGuardMute;
    private TextView tvGuardMutedBadge;
    private Button btnGuardResetShelly;

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private final SimpleDateFormat updateFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            tvClock.setText(clockFormat.format(new Date()));
            if (currentSettings != null) {
                PowerGuardState guardState = powerGuardManager.getCurrentState();
                boolean needsTick = guardState.band != PowerBand.NORMAL
                    || guardState.isTestSimulation
                    || guardState.shellyPendingDelaySec > 0
                    || (currentSettings.shellySheddingEnabled && !guardState.isShedded);
                if (needsTick) {
                    handlePowerGuardEvaluation(powerGuardManager.tick(currentSettings));
                }
            }
            clockHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on continuously for solar TV dashboard
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        // Bind Views
        energyFlowView = (EnergyFlowView) findViewById(R.id.energy_flow_view);
        tvConnectionStatus = (TextView) findViewById(R.id.tv_connection_status);
        tvModelSubtitle = (TextView) findViewById(R.id.tv_model_subtitle);
        tvLastUpdate = (TextView) findViewById(R.id.tv_last_update);
        tvClock = (TextView) findViewById(R.id.tv_clock);
        tvDailyYield = (TextView) findViewById(R.id.tv_daily_yield);
        tvAutonomyPercentage = (TextView) findViewById(R.id.tv_autonomy_percentage);
        tvAutonomySubtitle = (TextView) findViewById(R.id.tv_autonomy_subtitle);

        layoutPowerGuardBanner = (LinearLayout) findViewById(R.id.layout_power_guard_banner);
        tvGuardTitle = (TextView) findViewById(R.id.tv_guard_title);
        tvGuardDetails = (TextView) findViewById(R.id.tv_guard_details);
        btnGuardMute = (Button) findViewById(R.id.btn_guard_mute);
        tvGuardMutedBadge = (TextView) findViewById(R.id.tv_guard_muted_badge);
        btnGuardResetShelly = (Button) findViewById(R.id.btn_guard_reset_shelly);

        findViewById(R.id.btn_settings).setOnClickListener(v -> openSettings());
        findViewById(R.id.card_daily_yield).setOnClickListener(v -> openSettings());
        findViewById(R.id.card_autonomy).setOnClickListener(v -> openSettings());

        if (btnGuardMute != null) {
            btnGuardMute.setOnClickListener(v -> {
                powerGuardManager.muteAlarmForSession();
                renderPowerGuardBanner(powerGuardManager.getCurrentState());
            });
        }
        if (btnGuardResetShelly != null) {
            btnGuardResetShelly.setOnClickListener(v -> {
                powerGuardManager.resetShellyShed();
                renderPowerGuardBanner(powerGuardManager.getCurrentState());
            });
        }

        // Initialize Repositories
        settingsRepository = new SettingsRepository(getApplicationContext());
        currentSettings = settingsRepository.loadSettings();
        LocaleHelper.applyLanguage(getApplicationContext(), currentSettings.languageCode);
        solarRepository = new SolarRepository(settingsRepository);
        solarRepository.setListener(this);
        coffeePromptManager = new CoffeePromptManager(getApplicationContext());

        // Start Live Clock & PowerGuard 1s ticker
        clockHandler.post(clockRunnable);

        // Start Polling Worker
        solarRepository.start();

        // If IP is not configured and mock mode is off, open settings directly
        updateModelSubtitle(currentSettings);
        if (currentSettings.ipAddress.trim().isEmpty() && !currentSettings.mockMode) {
            openSettings();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (solarRepository != null) {
            solarRepository.start();
        }
        showCoffeePromptIfNeeded();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Free inverter socket when app is in background so other devices/apps can connect
        if (solarRepository != null) {
            solarRepository.stop();
        }
    }

    @Override
    public void onSolarDataUpdated(SolarTelemetry data) {
        energyFlowView.setSolarData(data);
        tvDailyYield.setText(data.getFormattedDailyYield());

        int houseConsumption = data.getHouseConsumptionW();
        boolean isSelfSufficient = data.gridPowerW >= 0 || (data.pvPowerW + Math.abs(Math.min(0, data.batteryPowerW))) >= houseConsumption;
        int autonomyPercentage = 100;
        if (houseConsumption > 0) {
            int imported = -Math.min(0, data.gridPowerW);
            int coveredByLocal = Math.max(0, houseConsumption - imported);
            autonomyPercentage = Math.min(100, Math.max(0, (int) ((coveredByLocal * 100.0) / houseConsumption)));
        }

        tvAutonomyPercentage.setText(autonomyPercentage + "%");
        tvAutonomySubtitle.setText(isSelfSufficient ? R.string.autonomy_subtitle_100 : R.string.autonomy_subtitle_partial);

        if (data.timestamp > 0) {
            tvLastUpdate.setText(getString(R.string.label_updated_prefix, updateFormat.format(new Date(data.timestamp))));
        }

        if (currentSettings != null) {
            handlePowerGuardEvaluation(powerGuardManager.evaluate(data, currentSettings));
        }
    }

    @Override
    public void onConnectionStateChanged(String stateText, int stateColor) {
        tvConnectionStatus.setText(stateText);
        tvConnectionStatus.setTextColor(stateColor);
    }

    private void handlePowerGuardEvaluation(PowerGuardEvaluation evaluation) {
        if (evaluation == null) return;
        renderPowerGuardBanner(evaluation.state);

        if (evaluation.shouldTriggerCriticalBeep) {
            alarmSoundController.playCriticalBeep();
        } else if (evaluation.shouldTriggerWarningBeep) {
            alarmSoundController.playWarningBeep();
        }

        if (evaluation.shouldTriggerShellyShed && currentSettings != null) {
            final String loadName = currentSettings.shelly1Name;
            shellyController.turnOffAsync(currentSettings, (success, errorMessage) ->
                clockHandler.post(() -> {
                    powerGuardManager.onShellyCommandResult(success, loadName, errorMessage);
                    renderPowerGuardBanner(powerGuardManager.getCurrentState());
                })
            );
        }
    }

    private void renderPowerGuardBanner(PowerGuardState state) {
        if (layoutPowerGuardBanner == null || state == null) return;

        if (!state.shouldShowBanner()) {
            layoutPowerGuardBanner.setVisibility(View.GONE);
            return;
        }

        layoutPowerGuardBanner.setVisibility(View.VISIBLE);

        int accentColor;
        int bgColor;
        String title;
        if (state.band == PowerBand.CRITICAL) {
            accentColor = Color.parseColor("#EF4444");
            bgColor = Color.parseColor("#2A1215");
            title = getString(R.string.guard_title_critical);
        } else if (state.band == PowerBand.WARNING) {
            accentColor = Color.parseColor("#F59E0B");
            bgColor = Color.parseColor("#291E0B");
            title = getString(R.string.guard_title_warning);
        } else {
            accentColor = Color.parseColor("#10B981");
            bgColor = Color.parseColor("#0D241E");
            title = getString(R.string.guard_title_shelly);
        }

        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setShape(GradientDrawable.RECTANGLE);
        bannerBg.setCornerRadius(10f * getResources().getDisplayMetrics().density);
        bannerBg.setColor(bgColor);
        bannerBg.setStroke(Math.round(1.5f * getResources().getDisplayMetrics().density), accentColor);
        layoutPowerGuardBanner.setBackground(bannerBg);

        tvGuardTitle.setText(state.isTestSimulation ? (title + " • " + getString(R.string.guard_test_badge)) : title);
        tvGuardTitle.setTextColor(accentColor);

        StringBuilder details = new StringBuilder();
        if (state.band != PowerBand.NORMAL) {
            details.append(getString(R.string.guard_import_and_contract, state.importPowerKw, state.contractPowerKw));
            details.append("  •  ");
            details.append(getString(R.string.guard_remaining_time, formatCountdown(state.remainingSeconds)));
        }
        if (state.shellyPendingDelaySec > 0) {
            if (details.length() > 0) details.append("  •  ");
            details.append(getString(R.string.guard_shelly_pending, state.shellyPendingDelaySec));
        }

        String shellyMsg = null;
        if (state.shellyStatusKind == ShellyStatusKind.SHEDDING_IN_PROGRESS) {
            shellyMsg = getString(R.string.guard_shelly_shedding_in_progress, state.shedLoadName);
        } else if (state.shellyStatusKind == ShellyStatusKind.SHED_SUCCESS) {
            shellyMsg = getString(R.string.guard_shelly_shed_success, state.shedLoadName);
        } else if (state.shellyStatusKind == ShellyStatusKind.SHED_ERROR) {
            shellyMsg = getString(
                R.string.guard_shelly_shed_error,
                state.shedLoadName,
                state.shellyErrorDetail != null ? state.shellyErrorDetail : getString(R.string.guard_shelly_unreachable)
            );
        } else if (state.shellyStatusMessage != null) {
            shellyMsg = state.shellyStatusMessage;
        }
        if (shellyMsg != null && !shellyMsg.isEmpty()) {
            if (details.length() > 0) details.append("  •  ");
            details.append(shellyMsg);
        }
        tvGuardDetails.setText(details.toString());

        boolean soundEnabled = currentSettings != null && currentSettings.meterAlarmSoundEnabled;
        boolean showMute = state.band != PowerBand.NORMAL && (soundEnabled || state.isTestSimulation) && !state.isMuted;
        boolean showMutedBadge = state.band != PowerBand.NORMAL && !state.isTestSimulation && soundEnabled && state.isMuted;

        btnGuardMute.setVisibility(showMute ? View.VISIBLE : View.GONE);
        tvGuardMutedBadge.setVisibility(showMutedBadge ? View.VISIBLE : View.GONE);
        btnGuardResetShelly.setVisibility(state.isShedded ? View.VISIBLE : View.GONE);
    }

    private void showCoffeePromptIfNeeded() {
        if (coffeePromptManager == null || isFinishing()) return;
        if (activeSettingsDialog != null && activeSettingsDialog.isShowing()) return;
        if (activeCoffeeDialog != null && activeCoffeeDialog.isShowing()) return;
        if (!coffeePromptManager.shouldShowCoffeePrompt()) return;

        activeCoffeeDialog = new CoffeePromptDialog(this, new CoffeePromptDialog.Callback() {
            @Override
            public void onSnoozed() {
                coffeePromptManager.recordSnoozed();
            }

            @Override
            public void onConfirmedForever() {
                coffeePromptManager.recordDismissedForever();
            }
        });
        activeCoffeeDialog.setOnDismissListener(d -> activeCoffeeDialog = null);
        activeCoffeeDialog.show();
    }

    private static String formatCountdown(long totalSeconds) {
        long clamped = Math.max(0L, totalSeconds);
        long mins = clamped / 60L;
        long secs = clamped % 60L;
        return String.format(Locale.US, "%02d:%02d", mins, secs);
    }

    private void updateModelSubtitle(AppSettings settings) {
        if (tvModelSubtitle != null && settings != null && settings.inverterBrand != null) {
            tvModelSubtitle.setText(settings.inverterBrand.subtitle);
        }
    }

    private void openSettings() {
        AppSettings current = settingsRepository.loadSettings();
        SettingsDialog dialog = new SettingsDialog(
            this,
            current,
            updated -> {
                String previousLanguageCode = currentSettings != null ? currentSettings.languageCode : null;
                boolean languageChanged = (previousLanguageCode == null || !previousLanguageCode.equals(updated.languageCode));
                currentSettings = updated;
                settingsRepository.updateSettings(updated);
                LocaleHelper.applyLanguage(getApplicationContext(), updated.languageCode);
                if (languageChanged) {
                    recreate();
                    return;
                }
                updateModelSubtitle(updated);
                handlePowerGuardEvaluation(powerGuardManager.tick(updated));
                solarRepository.reload(updated);
            },
            new SettingsDialog.OnSettingsTestListener() {
                @Override
                public void onTestPowerAlarm(double contractPowerKw) {
                    handlePowerGuardEvaluation(powerGuardManager.startAlarmTest(contractPowerKw, currentSettings));
                }

                @Override
                public void onTestShellyOff(String ip, int channel, com.huaweitv.solar.model.ShellyGen gen, ShellyController.Callback callback) {
                    shellyController.turnOffAsync(ip, channel, gen, (success, errorMessage) ->
                        clockHandler.post(() -> {
                            if (callback != null) {
                                callback.onComplete(success, errorMessage);
                            }
                        })
                    );
                }
            }
        );
        activeSettingsDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (activeSettingsDialog == dialog) {
                activeSettingsDialog = null;
            }
        });
        dialog.show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            openSettings();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockHandler.removeCallbacks(clockRunnable);
        alarmSoundController.release();
        shellyController.shutdown();
        if (solarRepository != null) {
            solarRepository.stop();
        }
    }
}

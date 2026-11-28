package com.huaweitv.solar.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.huaweitv.solar.R;
import com.huaweitv.solar.model.AppLanguage;
import com.huaweitv.solar.model.AppSettings;
import com.huaweitv.solar.model.InverterBrand;
import com.huaweitv.solar.model.ShellyGen;
import com.huaweitv.solar.shelly.ShellyController;

import java.util.Locale;

/**
 * TV Settings Dialog with dynamic Inverter selector (Huawei Modbus TCP / Fronius Solar API),
 * Meter / Contract Power alarm configuration, and local Shelly Load Shedding settings.
 * Maintains a valid D-Pad focus chain with zero links to View.GONE controls.
 * Compatible with Android 4.4.4 (API 19) through Android 15.
 */
public class SettingsDialog extends Dialog {

    public interface OnSettingsSavedListener {
        void onSettingsSaved(AppSettings settings);
    }

    public interface OnSettingsTestListener {
        void onTestPowerAlarm(double contractPowerKw);
        void onTestShellyOff(String ip, int channel, ShellyGen gen, ShellyController.Callback callback);
    }

    private final AppSettings currentSettings;
    private final OnSettingsSavedListener listener;
    private final OnSettingsTestListener testListener;

    private View btnLangIt;
    private View btnLangEn;
    private View btnLangDe;
    private View btnLangFr;
    private TextView tvLangIt;
    private TextView tvLangEn;
    private TextView tvLangDe;
    private TextView tvLangFr;

    private Button btnInverterHuawei;
    private Button btnInverterFronius;
    private EditText etIp;
    private LinearLayout layoutHuaweiFields;
    private LinearLayout layoutFroniusFields;
    private EditText etPort;
    private EditText etUnitId;
    private EditText etFroniusDeviceId;
    private Button btnPortPreset502;
    private Button btnPortPreset6607;
    private Button btnUnitPreset0;
    private Button btnFroniusId1;
    private Button btnPoll5;
    private Button btnPoll10;
    private Button btnPoll15;
    private Button btnPoll30;
    private TextView tvPollLabel;
    private Button btnMock;

    // Meter / Contract power alarm
    private Button btnMeterAlarmToggle;
    private EditText etContractPowerKw;
    private Button btnMeterSoundToggle;
    private Button btnTestPowerAlarm;

    // Shelly load shedding
    private Button btnShellySheddingToggle;
    private EditText etShellyName;
    private EditText etShellyIp;
    private Button btnShellyGen1;
    private Button btnShellyGen2;
    private EditText etShellyChannel;
    private EditText etShellyDelay;
    private Button btnTestShellyOff;
    private TextView tvShellyTestStatus;
    private boolean shellyTestInProgress = false;

    private TextView tvValidationError;

    private AppLanguage selectedLanguage;
    private InverterBrand selectedInverterBrand;
    private int selectedPollInterval;
    private boolean selectedMockMode;
    private boolean selectedMeterAlarmEnabled;
    private boolean selectedMeterAlarmSoundEnabled;
    private boolean selectedShellySheddingEnabled;
    private ShellyGen selectedShellyGen;

    public SettingsDialog(Context context, AppSettings currentSettings, OnSettingsSavedListener listener) {
        this(context, currentSettings, listener, null);
    }

    public SettingsDialog(
        Context context,
        AppSettings currentSettings,
        OnSettingsSavedListener listener,
        OnSettingsTestListener testListener
    ) {
        super(context);
        this.currentSettings = currentSettings;
        this.listener = listener;
        this.testListener = testListener;
        this.selectedLanguage = currentSettings.getLanguage();
        this.selectedInverterBrand = currentSettings.inverterBrand != null ? currentSettings.inverterBrand : InverterBrand.HUAWEI;
        this.selectedPollInterval = currentSettings.pollingIntervalSeconds > 0 ? currentSettings.pollingIntervalSeconds : 10;
        this.selectedMockMode = currentSettings.mockMode;
        this.selectedMeterAlarmEnabled = currentSettings.meterAlarmEnabled;
        this.selectedMeterAlarmSoundEnabled = currentSettings.meterAlarmSoundEnabled;
        this.selectedShellySheddingEnabled = currentSettings.shellySheddingEnabled;
        this.selectedShellyGen = currentSettings.shelly1Gen != null ? currentSettings.shelly1Gen : ShellyGen.GEN2;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_settings);
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Bind Language Selector
        btnLangIt = findViewById(R.id.btn_lang_it);
        btnLangEn = findViewById(R.id.btn_lang_en);
        btnLangDe = findViewById(R.id.btn_lang_de);
        btnLangFr = findViewById(R.id.btn_lang_fr);
        tvLangIt = (TextView) findViewById(R.id.tv_lang_it);
        tvLangEn = (TextView) findViewById(R.id.tv_lang_en);
        tvLangDe = (TextView) findViewById(R.id.tv_lang_de);
        tvLangFr = (TextView) findViewById(R.id.tv_lang_fr);

        btnLangIt.setOnClickListener(v -> selectLanguage(AppLanguage.ITALIAN));
        btnLangEn.setOnClickListener(v -> selectLanguage(AppLanguage.ENGLISH));
        btnLangDe.setOnClickListener(v -> selectLanguage(AppLanguage.GERMAN));
        btnLangFr.setOnClickListener(v -> selectLanguage(AppLanguage.FRENCH));

        // Bind Inverter Section
        btnInverterHuawei = (Button) findViewById(R.id.btn_inverter_huawei);
        btnInverterFronius = (Button) findViewById(R.id.btn_inverter_fronius);
        etIp = (EditText) findViewById(R.id.et_ip);
        layoutHuaweiFields = (LinearLayout) findViewById(R.id.layout_huawei_fields);
        layoutFroniusFields = (LinearLayout) findViewById(R.id.layout_fronius_fields);
        etPort = (EditText) findViewById(R.id.et_port);
        etUnitId = (EditText) findViewById(R.id.et_unit_id);
        etFroniusDeviceId = (EditText) findViewById(R.id.et_fronius_device_id);
        btnPortPreset502 = (Button) findViewById(R.id.btn_port_preset_502);
        btnPortPreset6607 = (Button) findViewById(R.id.btn_port_preset_6607);
        btnUnitPreset0 = (Button) findViewById(R.id.btn_unit_preset_0);
        btnFroniusId1 = (Button) findViewById(R.id.btn_fronius_id_1);
        btnPoll5 = (Button) findViewById(R.id.btn_poll_5);
        btnPoll10 = (Button) findViewById(R.id.btn_poll_10);
        btnPoll15 = (Button) findViewById(R.id.btn_poll_15);
        btnPoll30 = (Button) findViewById(R.id.btn_poll_30);
        tvPollLabel = (TextView) findViewById(R.id.tv_dialog_poll_label);
        btnMock = (Button) findViewById(R.id.btn_mock_toggle);

        // Bind Meter Section
        btnMeterAlarmToggle = (Button) findViewById(R.id.btn_meter_alarm_toggle);
        etContractPowerKw = (EditText) findViewById(R.id.et_contract_power_kw);
        btnMeterSoundToggle = (Button) findViewById(R.id.btn_meter_sound_toggle);
        btnTestPowerAlarm = (Button) findViewById(R.id.btn_test_power_alarm);

        // Bind Shelly Section
        btnShellySheddingToggle = (Button) findViewById(R.id.btn_shelly_shedding_toggle);
        etShellyName = (EditText) findViewById(R.id.et_shelly_name);
        etShellyIp = (EditText) findViewById(R.id.et_shelly_ip);
        btnShellyGen1 = (Button) findViewById(R.id.btn_shelly_gen1);
        btnShellyGen2 = (Button) findViewById(R.id.btn_shelly_gen2);
        etShellyChannel = (EditText) findViewById(R.id.et_shelly_channel);
        etShellyDelay = (EditText) findViewById(R.id.et_shelly_delay);
        btnTestShellyOff = (Button) findViewById(R.id.btn_test_shelly_off);
        tvShellyTestStatus = (TextView) findViewById(R.id.tv_shelly_test_status);

        tvValidationError = (TextView) findViewById(R.id.tv_validation_error);

        // Populate initial Inverter values
        setEditTextValue(etIp, currentSettings.ipAddress != null ? currentSettings.ipAddress : "");
        int initPort = (currentSettings.port > 0 && currentSettings.port <= 65535) ? currentSettings.port : 502;
        setEditTextValue(etPort, String.valueOf(initPort));
        setEditTextValue(etUnitId, String.valueOf(Math.max(0, currentSettings.unitId)));
        setEditTextValue(etFroniusDeviceId, String.valueOf(Math.max(0, currentSettings.froniusDeviceId)));

        // Populate initial Meter values
        double initKw = currentSettings.contractPowerKw > 0.0 ? currentSettings.contractPowerKw : 3.0;
        setEditTextValue(etContractPowerKw, String.format(Locale.US, "%.1f", initKw));

        // Populate initial Shelly values
        setEditTextValue(etShellyName, currentSettings.shelly1Name != null ? currentSettings.shelly1Name : "");
        setEditTextValue(etShellyIp, currentSettings.shelly1Ip != null ? currentSettings.shelly1Ip : "");
        setEditTextValue(etShellyChannel, String.valueOf(Math.max(0, currentSettings.shelly1Channel)));
        int initDelay = currentSettings.shellyTriggerDelaySec > 0 ? currentSettings.shellyTriggerDelaySec : 15;
        setEditTextValue(etShellyDelay, String.valueOf(initDelay));

        // Apply dynamic UI visibility & focus chain
        updateLanguageUi();
        updateInverterUi();
        updatePollLabel();
        updateMockButton();
        updateMeterUi();
        updateShellyUi();

        // Inverter Brand Selector
        btnInverterHuawei.setOnClickListener(v -> selectInverterBrand(InverterBrand.HUAWEI));
        btnInverterFronius.setOnClickListener(v -> selectInverterBrand(InverterBrand.FRONIUS));

        // Huawei Port & Unit ID Presets
        btnPortPreset502.setOnClickListener(v -> setFieldText(etPort, "502"));
        btnPortPreset6607.setOnClickListener(v -> setFieldText(etPort, "6607"));
        btnUnitPreset0.setOnClickListener(v -> setFieldText(etUnitId, "0"));
        findViewById(R.id.btn_unit_preset_1).setOnClickListener(v -> setFieldText(etUnitId, "1"));
        findViewById(R.id.btn_unit_preset_2).setOnClickListener(v -> setFieldText(etUnitId, "2"));

        // Fronius Device ID Presets
        btnFroniusId1.setOnClickListener(v -> setFieldText(etFroniusDeviceId, "1"));
        findViewById(R.id.btn_fronius_id_2).setOnClickListener(v -> setFieldText(etFroniusDeviceId, "2"));

        // Polling Interval Buttons
        btnPoll5.setOnClickListener(v -> { selectedPollInterval = 5; updatePollLabel(); });
        btnPoll10.setOnClickListener(v -> { selectedPollInterval = 10; updatePollLabel(); });
        btnPoll15.setOnClickListener(v -> { selectedPollInterval = 15; updatePollLabel(); });
        btnPoll30.setOnClickListener(v -> { selectedPollInterval = 30; updatePollLabel(); });

        // Mock Toggle
        btnMock.setOnClickListener(v -> {
            selectedMockMode = !selectedMockMode;
            updateMockButton();
        });

        // Meter Section Listeners
        btnMeterAlarmToggle.setOnClickListener(v -> {
            selectedMeterAlarmEnabled = !selectedMeterAlarmEnabled;
            updateMeterUi();
        });
        findViewById(R.id.btn_contract_30).setOnClickListener(v -> setFieldText(etContractPowerKw, "3.0"));
        findViewById(R.id.btn_contract_45).setOnClickListener(v -> setFieldText(etContractPowerKw, "4.5"));
        findViewById(R.id.btn_contract_60).setOnClickListener(v -> setFieldText(etContractPowerKw, "6.0"));
        btnMeterSoundToggle.setOnClickListener(v -> {
            selectedMeterAlarmSoundEnabled = !selectedMeterAlarmSoundEnabled;
            updateMeterUi();
        });
        btnTestPowerAlarm.setOnClickListener(v -> triggerPowerAlarmTest());

        // Shelly Section Listeners
        btnShellySheddingToggle.setOnClickListener(v -> {
            selectedShellySheddingEnabled = !selectedShellySheddingEnabled;
            updateShellyUi();
        });
        btnShellyGen1.setOnClickListener(v -> {
            selectedShellyGen = ShellyGen.GEN1;
            updateShellyUi();
        });
        btnShellyGen2.setOnClickListener(v -> {
            selectedShellyGen = ShellyGen.GEN2;
            updateShellyUi();
        });
        findViewById(R.id.btn_shelly_ch_0).setOnClickListener(v -> setFieldText(etShellyChannel, "0"));
        findViewById(R.id.btn_shelly_ch_1).setOnClickListener(v -> setFieldText(etShellyChannel, "1"));
        findViewById(R.id.btn_shelly_delay_10).setOnClickListener(v -> setFieldText(etShellyDelay, "10"));
        findViewById(R.id.btn_shelly_delay_15).setOnClickListener(v -> setFieldText(etShellyDelay, "15"));
        findViewById(R.id.btn_shelly_delay_30).setOnClickListener(v -> setFieldText(etShellyDelay, "30"));
        btnTestShellyOff.setOnClickListener(v -> triggerShellyOffTest());

        // Cancel / Save
        findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
        findViewById(R.id.btn_save).setOnClickListener(v -> validateAndSave());
    }

    private void selectLanguage(AppLanguage language) {
        selectedLanguage = language;
        updateLanguageUi();
    }

    private void updateLanguageUi() {
        int activeColor = Color.parseColor("#F8FAFC");
        int inactiveColor = Color.parseColor("#94A3B8");
        btnLangIt.setSelected(selectedLanguage == AppLanguage.ITALIAN);
        btnLangEn.setSelected(selectedLanguage == AppLanguage.ENGLISH);
        btnLangDe.setSelected(selectedLanguage == AppLanguage.GERMAN);
        btnLangFr.setSelected(selectedLanguage == AppLanguage.FRENCH);
        tvLangIt.setTextColor(selectedLanguage == AppLanguage.ITALIAN ? activeColor : inactiveColor);
        tvLangEn.setTextColor(selectedLanguage == AppLanguage.ENGLISH ? activeColor : inactiveColor);
        tvLangDe.setTextColor(selectedLanguage == AppLanguage.GERMAN ? activeColor : inactiveColor);
        tvLangFr.setTextColor(selectedLanguage == AppLanguage.FRENCH ? activeColor : inactiveColor);
    }

    private void selectInverterBrand(InverterBrand newBrand) {
        if (selectedInverterBrand == newBrand) return;
        selectedInverterBrand = newBrand;
        tvValidationError.setVisibility(View.GONE);
        updateInverterUi();
    }

    /**
     * Toggles visibility of Huawei vs Fronius fields and updates D-Pad focus links
     * so nextFocusUp / nextFocusDown never point to View.GONE controls.
     */
    private void updateInverterUi() {
        boolean isFronius = (selectedInverterBrand == InverterBrand.FRONIUS);
        int activeColor = Color.parseColor("#F59E0B");
        int inactiveColor = Color.parseColor("#94A3B8");

        btnInverterHuawei.setTextColor(!isFronius ? activeColor : inactiveColor);
        btnInverterFronius.setTextColor(isFronius ? activeColor : inactiveColor);

        if (isFronius) {
            layoutHuaweiFields.setVisibility(View.GONE);
            layoutFroniusFields.setVisibility(View.VISIBLE);

            etIp.setNextFocusUpId(R.id.btn_inverter_fronius);
            etIp.setNextFocusDownId(R.id.et_fronius_device_id);
            btnPoll5.setNextFocusUpId(R.id.btn_fronius_id_1);
            btnPoll10.setNextFocusUpId(R.id.btn_fronius_id_1);
            btnPoll15.setNextFocusUpId(R.id.btn_fronius_id_2);
            btnPoll30.setNextFocusUpId(R.id.btn_fronius_id_2);
        } else {
            layoutHuaweiFields.setVisibility(View.VISIBLE);
            layoutFroniusFields.setVisibility(View.GONE);

            etIp.setNextFocusUpId(R.id.btn_inverter_huawei);
            etIp.setNextFocusDownId(R.id.et_port);
            btnPoll5.setNextFocusUpId(R.id.btn_port_preset_502);
            btnPoll10.setNextFocusUpId(R.id.btn_port_preset_6607);
            btnPoll15.setNextFocusUpId(R.id.btn_unit_preset_0);
            btnPoll30.setNextFocusUpId(R.id.btn_unit_preset_2);
        }
    }

    private void updateMeterUi() {
        int activeColor = Color.parseColor("#38BDF8");
        int inactiveColor = Color.parseColor("#94A3B8");
        Context ctx = getContext();

        String stateAlarm = ctx.getString(selectedMeterAlarmEnabled ? R.string.settings_state_on : R.string.settings_state_off);
        btnMeterAlarmToggle.setText(ctx.getString(R.string.settings_meter_alarm_title) + ": " + stateAlarm);
        btnMeterAlarmToggle.setTextColor(selectedMeterAlarmEnabled ? activeColor : inactiveColor);

        String stateSound = ctx.getString(selectedMeterAlarmSoundEnabled ? R.string.settings_state_on : R.string.settings_state_off);
        btnMeterSoundToggle.setText(ctx.getString(R.string.settings_meter_sound_title) + ": " + stateSound);
        btnMeterSoundToggle.setTextColor(selectedMeterAlarmSoundEnabled ? activeColor : inactiveColor);
    }

    private void updateShellyUi() {
        int activeColor = Color.parseColor("#10B981");
        int inactiveColor = Color.parseColor("#94A3B8");
        Context ctx = getContext();

        String stateShed = ctx.getString(selectedShellySheddingEnabled ? R.string.settings_state_on : R.string.settings_state_off);
        btnShellySheddingToggle.setText(ctx.getString(R.string.settings_shelly_master_title) + ": " + stateShed);
        btnShellySheddingToggle.setTextColor(selectedShellySheddingEnabled ? activeColor : inactiveColor);

        boolean isGen1 = (selectedShellyGen == ShellyGen.GEN1);
        btnShellyGen1.setTextColor(isGen1 ? activeColor : inactiveColor);
        btnShellyGen2.setTextColor(!isGen1 ? activeColor : inactiveColor);
    }

    private void setEditTextValue(EditText editText, String text) {
        editText.setText(text);
        editText.setSelection(text.length());
    }

    private void setFieldText(EditText editText, String text) {
        setEditTextValue(editText, text);
        editText.requestFocus();
        tvValidationError.setVisibility(View.GONE);
    }

    private void updatePollLabel() {
        tvPollLabel.setText(getContext().getString(R.string.settings_label_poll, selectedPollInterval));
    }

    private void updateMockButton() {
        Context ctx = getContext();
        String stateMock = ctx.getString(selectedMockMode ? R.string.settings_state_on : R.string.settings_state_off);
        btnMock.setText(ctx.getString(R.string.settings_mock_title) + ": " + stateMock);
    }

    private void showError(String message, View focusTarget) {
        tvValidationError.setText(message);
        tvValidationError.setVisibility(View.VISIBLE);
        if (focusTarget != null) {
            focusTarget.requestFocus();
        }
    }

    private void showShellyTestStatus(String message, int textColor) {
        tvShellyTestStatus.setText(message);
        tvShellyTestStatus.setTextColor(textColor);
        tvShellyTestStatus.setVisibility(View.VISIBLE);
    }

    private void triggerPowerAlarmTest() {
        Context ctx = getContext();
        double contractKw;
        try {
            String rawKw = etContractPowerKw.getText().toString().trim().replace(',', '.');
            contractKw = Double.parseDouble(rawKw);
            if (contractKw <= 0.0 || contractKw > 100.0) {
                showError(ctx.getString(R.string.err_invalid_contract_kw), etContractPowerKw);
                return;
            }
        } catch (NumberFormatException e) {
            showError(ctx.getString(R.string.err_invalid_contract_kw), etContractPowerKw);
            return;
        }

        tvValidationError.setVisibility(View.GONE);
        if (testListener != null) {
            testListener.onTestPowerAlarm(contractKw);
        }
        dismiss();
    }

    private void triggerShellyOffTest() {
        if (shellyTestInProgress) return;
        final Context ctx = getContext();
        final String shellyIp = etShellyIp.getText().toString().trim();
        if (shellyIp.isEmpty()) {
            showShellyTestStatus(ctx.getString(R.string.err_missing_shelly_ip_test), Color.parseColor("#EF4444"));
            etShellyIp.requestFocus();
            return;
        }
        if (!isValidIpv4(shellyIp)) {
            showShellyTestStatus(ctx.getString(R.string.err_invalid_shelly_ip), Color.parseColor("#EF4444"));
            etShellyIp.requestFocus();
            return;
        }

        int shellyChannel;
        try {
            shellyChannel = Integer.parseInt(etShellyChannel.getText().toString().trim());
            if (shellyChannel < 0 || shellyChannel > 3) {
                showShellyTestStatus(ctx.getString(R.string.err_invalid_shelly_channel), Color.parseColor("#EF4444"));
                etShellyChannel.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            showShellyTestStatus(ctx.getString(R.string.err_invalid_shelly_channel), Color.parseColor("#EF4444"));
            etShellyChannel.requestFocus();
            return;
        }

        if (testListener == null) return;

        tvValidationError.setVisibility(View.GONE);
        shellyTestInProgress = true;
        showShellyTestStatus(ctx.getString(R.string.settings_test_shelly_sending, shellyIp), Color.parseColor("#F59E0B"));

        testListener.onTestShellyOff(shellyIp, shellyChannel, selectedShellyGen, (success, errorMessage) -> {
            shellyTestInProgress = false;
            if (!isShowing()) return;
            if (success) {
                showShellyTestStatus(
                    ctx.getString(R.string.settings_test_shelly_success, shellyIp),
                    Color.parseColor("#10B981")
                );
            } else {
                String reason = (errorMessage != null && !errorMessage.trim().isEmpty())
                    ? errorMessage.trim()
                    : ctx.getString(R.string.guard_shelly_unreachable);
                showShellyTestStatus(
                    ctx.getString(R.string.settings_test_shelly_error, reason),
                    Color.parseColor("#EF4444")
                );
            }
        });
    }

    private void validateAndSave() {
        Context ctx = getContext();
        String ipStr = etIp.getText().toString().trim();
        if (!ipStr.isEmpty() && !isValidIpv4(ipStr)) {
            showError(ctx.getString(R.string.err_invalid_inverter_ip), etIp);
            return;
        }

        int port = 502;
        int unitId = 1;
        int froniusDeviceId = 1;

        if (selectedInverterBrand == InverterBrand.HUAWEI) {
            try {
                port = Integer.parseInt(etPort.getText().toString().trim());
                if (port <= 0 || port > 65535) {
                    showError(ctx.getString(R.string.err_invalid_port), etPort);
                    return;
                }
            } catch (NumberFormatException e) {
                showError(ctx.getString(R.string.err_invalid_port), etPort);
                return;
            }

            try {
                unitId = Integer.parseInt(etUnitId.getText().toString().trim());
                if (unitId < 0 || unitId > 247) {
                    showError(ctx.getString(R.string.err_invalid_unit_id), etUnitId);
                    return;
                }
            } catch (NumberFormatException e) {
                showError(ctx.getString(R.string.err_invalid_unit_id), etUnitId);
                return;
            }

            froniusDeviceId = parseIntOrDefault(etFroniusDeviceId.getText().toString().trim(), 1);
        } else {
            try {
                froniusDeviceId = Integer.parseInt(etFroniusDeviceId.getText().toString().trim());
                if (froniusDeviceId < 0 || froniusDeviceId > 99) {
                    showError(ctx.getString(R.string.err_invalid_fronius_id), etFroniusDeviceId);
                    return;
                }
            } catch (NumberFormatException e) {
                showError(ctx.getString(R.string.err_invalid_fronius_id), etFroniusDeviceId);
                return;
            }

            port = parseIntOrDefault(etPort.getText().toString().trim(), 502);
            unitId = parseIntOrDefault(etUnitId.getText().toString().trim(), 1);
        }

        // Validate Contract Power (kW)
        double contractKw;
        try {
            String rawKw = etContractPowerKw.getText().toString().trim().replace(',', '.');
            contractKw = Double.parseDouble(rawKw);
            if (contractKw <= 0.0 || contractKw > 100.0) {
                showError(ctx.getString(R.string.err_invalid_contract_kw), etContractPowerKw);
                return;
            }
        } catch (NumberFormatException e) {
            showError(ctx.getString(R.string.err_invalid_contract_kw), etContractPowerKw);
            return;
        }

        // Validate Shelly Settings
        String shellyName = etShellyName.getText().toString().trim();
        String shellyIp = etShellyIp.getText().toString().trim();
        if (!shellyIp.isEmpty() && !isValidIpv4(shellyIp)) {
            showError(ctx.getString(R.string.err_invalid_shelly_ip), etShellyIp);
            return;
        }
        if (selectedShellySheddingEnabled && shellyIp.isEmpty()) {
            showError(ctx.getString(R.string.err_missing_shelly_ip), etShellyIp);
            return;
        }

        int shellyChannel;
        try {
            shellyChannel = Integer.parseInt(etShellyChannel.getText().toString().trim());
            if (shellyChannel < 0 || shellyChannel > 3) {
                showError(ctx.getString(R.string.err_invalid_shelly_channel), etShellyChannel);
                return;
            }
        } catch (NumberFormatException e) {
            showError(ctx.getString(R.string.err_invalid_shelly_channel), etShellyChannel);
            return;
        }

        int shellyDelay;
        try {
            shellyDelay = Integer.parseInt(etShellyDelay.getText().toString().trim());
            if (shellyDelay < 1 || shellyDelay > 300) {
                showError(ctx.getString(R.string.err_invalid_shelly_delay), etShellyDelay);
                return;
            }
        } catch (NumberFormatException e) {
            showError(ctx.getString(R.string.err_invalid_shelly_delay), etShellyDelay);
            return;
        }

        tvValidationError.setVisibility(View.GONE);
        if (listener != null) {
            listener.onSettingsSaved(new AppSettings(
                selectedInverterBrand,
                ipStr,
                port,
                unitId,
                froniusDeviceId,
                selectedPollInterval,
                selectedMockMode,
                selectedLanguage.code,
                selectedMeterAlarmEnabled,
                contractKw,
                selectedMeterAlarmSoundEnabled,
                selectedShellySheddingEnabled,
                shellyName,
                shellyIp,
                shellyChannel,
                selectedShellyGen,
                shellyDelay
            ));
        }
        dismiss();
    }

    private static int parseIntOrDefault(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Strict IPv4 validation for 4 octets in range 0-255.
     */
    public static boolean isValidIpv4(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String part : parts) {
                if (part.isEmpty() || part.length() > 3) return false;
                if (part.length() > 1 && part.startsWith("0")) return false;
                int val = Integer.parseInt(part);
                if (val < 0 || val > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

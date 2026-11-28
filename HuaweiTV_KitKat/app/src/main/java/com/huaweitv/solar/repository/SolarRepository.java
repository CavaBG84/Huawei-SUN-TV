package com.huaweitv.solar.repository;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import com.huaweitv.solar.R;
import com.huaweitv.solar.datasource.InverterDataSource;
import com.huaweitv.solar.datasource.InverterDataSourceFactory;
import com.huaweitv.solar.model.AppSettings;
import com.huaweitv.solar.model.InverterBrand;
import com.huaweitv.solar.model.SolarTelemetry;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Multi-inverter polling repository orchestrating {@link InverterDataSource} and Mock simulation.
 * Pure Java using Thread / ExecutorService and Handler (API 19+ compatible).
 */
public class SolarRepository {

    public interface SolarListener {
        void onSolarDataUpdated(SolarTelemetry data);
        void onConnectionStateChanged(String stateText, int stateColor);
    }

    private final SettingsRepository settingsRepository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SolarListener listener;
    private volatile boolean isRunning = false;
    private volatile AppSettings currentSettings;
    private volatile Thread workerThread = null;
    private volatile InverterDataSource activeDataSource = null;

    public SolarRepository(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
        this.currentSettings = settingsRepository.loadSettings();
    }

    public void setListener(SolarListener listener) {
        this.listener = listener;
    }

    public synchronized void start() {
        if (isRunning && workerThread != null && workerThread.isAlive()) return;
        isRunning = true;
        executor.execute(this::workerLoop);
    }

    public synchronized void reload(AppSettings newSettings) {
        this.currentSettings = newSettings;
        disconnectActiveSource();
        if (workerThread != null) {
            workerThread.interrupt(); // Wake up sleep immediately to apply new settings
        } else {
            start();
        }
    }

    public synchronized void stop() {
        isRunning = false;
        disconnectActiveSource();
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    private void disconnectActiveSource() {
        InverterDataSource source = activeDataSource;
        activeDataSource = null;
        if (source != null) {
            source.disconnect();
        }
    }

    private String getStr(int resId, Object... args) {
        Context ctx = settingsRepository.getAppContext();
        return args.length == 0 ? ctx.getString(resId) : ctx.getString(resId, args);
    }

    private void notifyData(SolarTelemetry data) {
        if (listener != null) {
            mainHandler.post(() -> listener.onSolarDataUpdated(data));
        }
    }

    private void notifyState(String text, int color) {
        if (listener != null) {
            mainHandler.post(() -> listener.onConnectionStateChanged(text, color));
        }
    }

    private void workerLoop() {
        workerThread = Thread.currentThread();
        long backoffMs = 2000L;
        final long maxBackoffMs = 30000L;
        InverterDataSource mockSource = null;

        try {
            while (isRunning) {
                AppSettings settings = currentSettings;

                if (settings.mockMode) {
                    disconnectActiveSource();
                    if (mockSource == null || mockSource.getBrand() != settings.inverterBrand) {
                        mockSource = InverterDataSourceFactory.create(settings);
                        try {
                            mockSource.connect();
                        } catch (Exception ignored) {}
                    }
                    notifyState(getStr(R.string.status_mock), Color.parseColor("#A855F7"));
                    try {
                        notifyData(mockSource.fetchTelemetry());
                    } catch (Exception ignored) {}

                    try {
                        Thread.sleep(2000L);
                    } catch (InterruptedException e) {
                        if (!isRunning) break;
                    }
                } else {
                    mockSource = null;
                    String ip = settings.ipAddress != null ? settings.ipAddress.trim() : "";
                    if (ip.isEmpty()) {
                        disconnectActiveSource();
                        notifyState(getStr(R.string.status_needs_config), Color.parseColor("#F59E0B"));
                        try {
                            Thread.sleep(2000L);
                        } catch (InterruptedException ie) {
                            if (!isRunning) break;
                            backoffMs = 2000L;
                        }
                        continue;
                    }

                    int port = settings.getEffectivePort();
                    InverterDataSource dataSource = activeDataSource;
                    if (dataSource == null || dataSource.getBrand() != settings.inverterBrand) {
                        disconnectActiveSource();
                        dataSource = InverterDataSourceFactory.create(settings);
                        activeDataSource = dataSource;
                    }

                    try {
                        if (!dataSource.isConnected()) {
                            String connectingLabel = (settings.inverterBrand == InverterBrand.FRONIUS)
                                ? getStr(R.string.status_connecting_fronius, ip, port)
                                : getStr(R.string.status_connecting_huawei, ip, port);
                            notifyState(connectingLabel, Color.parseColor("#F59E0B"));
                            dataSource.connect();
                        }

                        SolarTelemetry liveData = dataSource.fetchTelemetry();
                        backoffMs = 2000L;
                        notifyState(getStr(R.string.status_connected, ip, port), Color.parseColor("#10B981"));
                        notifyData(liveData);

                        long pollMs = Math.max(1, settings.pollingIntervalSeconds) * 1000L;
                        try {
                            Thread.sleep(pollMs);
                        } catch (InterruptedException ie) {
                            if (!isRunning) break;
                            backoffMs = 2000L;
                        }
                    } catch (Exception e) {
                        dataSource.disconnect();
                        if (!isRunning) break;

                        int retrySec = (int) (backoffMs / 1000);
                        String errMsg = (e.getMessage() != null && !e.getMessage().isEmpty())
                            ? e.getMessage()
                            : e.getClass().getSimpleName();
                        notifyState(getStr(R.string.status_error_detail, retrySec, errMsg), Color.parseColor("#EF4444"));

                        try {
                            Thread.sleep(backoffMs);
                        } catch (InterruptedException ie) {
                            if (!isRunning) break;
                            backoffMs = 2000L;
                            continue;
                        }
                        backoffMs = Math.min(backoffMs * 2, maxBackoffMs);
                    }
                }
            }
        } finally {
            disconnectActiveSource();
            workerThread = null;
        }
    }
}

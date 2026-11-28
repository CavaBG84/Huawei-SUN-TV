package com.huaweitv.solar.shelly;

import com.huaweitv.solar.model.AppSettings;
import com.huaweitv.solar.model.ShellyGen;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local LAN HTTP controller for Shelly relays (Gen 1 and Gen 2+), zero cloud dependencies.
 * Compatible with Android 4.4.4 KitKat (API 19+).
 */
public class ShellyController {

    public interface Callback {
        void onComplete(boolean success, String errorMessage);
    }

    private static final int TIMEOUT_MS = 3000;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void turnOffAsync(AppSettings settings, Callback callback) {
        turnOffAsync(settings.shelly1Ip, settings.shelly1Channel, settings.shelly1Gen, callback);
    }

    public void turnOffAsync(String ip, int channel, ShellyGen gen, Callback callback) {
        String url = buildTurnOffUrl(ip, channel, gen);
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setUseCaches(false);
                conn.setRequestProperty("Connection", "close");

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    if (callback != null) callback.onComplete(true, null);
                } else {
                    if (callback != null) callback.onComplete(false, "HTTP " + code);
                }
            } catch (IOException e) {
                if (callback != null) callback.onComplete(false, e.getMessage());
            } catch (Exception e) {
                if (callback != null) callback.onComplete(false, e.getClass().getSimpleName());
            } finally {
                if (conn != null) {
                    try {
                        conn.disconnect();
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    public static String buildTurnOffUrl(String ip, int channel, ShellyGen gen) {
        String cleanIp = ip != null ? ip.trim() : "";
        int cleanChannel = Math.max(0, channel);
        if (gen == ShellyGen.GEN1) {
            return "http://" + cleanIp + "/relay/" + cleanChannel + "?turn=off";
        }
        return "http://" + cleanIp + "/rpc/Switch.Set?id=" + cleanChannel + "&on=false";
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}

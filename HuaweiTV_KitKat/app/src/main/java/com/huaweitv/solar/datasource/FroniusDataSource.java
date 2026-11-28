package com.huaweitv.solar.datasource;

import com.huaweitv.solar.model.InverterBrand;
import com.huaweitv.solar.model.SolarTelemetry;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;

/**
 * Local LAN data source for Fronius inverters supporting Solar API v1.
 * Uses pure {@link HttpURLConnection} and {@link JSONObject} with zero external dependencies
 * for full Android 4.4.4 KitKat (API 19) compatibility.
 */
public class FroniusDataSource implements InverterDataSource {

    private static final int TIMEOUT_MS = 3500;

    private final String ipAddress;
    private final int port;
    private final int deviceId;

    private volatile boolean connected = false;
    private volatile HttpURLConnection activeConnection = null;

    public FroniusDataSource(String ipAddress, int port) {
        this(ipAddress, port, 1);
    }

    public FroniusDataSource(String ipAddress, int port, int deviceId) {
        this.ipAddress = ipAddress != null ? ipAddress.trim() : "";
        this.port = port;
        this.deviceId = Math.max(0, deviceId);
    }

    @Override
    public InverterBrand getBrand() {
        return InverterBrand.FRONIUS;
    }

    @Override
    public void connect() throws Exception {
        if (ipAddress.isEmpty()) {
            connected = false;
            throw new IOException("Indirizzo IP Fronius mancante");
        }
        connected = true;
    }

    @Override
    public SolarTelemetry fetchTelemetry() throws Exception {
        if (ipAddress.isEmpty()) {
            connected = false;
            throw new IOException("Indirizzo IP Fronius mancante");
        }

        int effectivePort = (port > 0 && port <= 65535) ? port : InverterBrand.FRONIUS.defaultPort;
        String endpoint = (effectivePort == 80)
            ? "http://" + ipAddress + "/solar_api/v1/GetPowerFlowRealtimeData.fcgi"
            : "http://" + ipAddress + ":" + effectivePort + "/solar_api/v1/GetPowerFlowRealtimeData.fcgi";

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setUseCaches(false);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Connection", "close");
        activeConnection = conn;

        try {
            int statusCode = conn.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                connected = false;
                throw new IOException("HTTP " + statusCode + " da inverter Fronius (" + endpoint + ")");
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            SolarTelemetry telemetry = parseRealtimeJson(sb.toString(), System.currentTimeMillis(), deviceId);
            connected = true;
            return telemetry;
        } catch (Exception e) {
            connected = false;
            throw e;
        } finally {
            if (activeConnection == conn) {
                activeConnection = null;
            }
            try {
                conn.disconnect();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        HttpURLConnection conn = activeConnection;
        activeConnection = null;
        if (conn != null) {
            try {
                conn.disconnect();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    /**
     * Parses a Fronius `GetPowerFlowRealtimeData.fcgi` JSON payload using {@link JSONObject}.
     */
    public static SolarTelemetry parseRealtimeJson(String json, long timestamp) throws IOException {
        return parseRealtimeJson(json, timestamp, 1);
    }

    public static SolarTelemetry parseRealtimeJson(String json, long timestamp, int preferredDeviceId) throws IOException {
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (Exception e) {
            throw new IOException("Risposta JSON Fronius non valida: " + e.getMessage(), e);
        }

        JSONObject head = root.optJSONObject("Head");
        JSONObject status = head != null ? head.optJSONObject("Status") : null;
        if (status != null) {
            int code = status.optInt("Code", 0);
            if (code != 0) {
                String userMsg = status.optString("UserMessage", "");
                String reason = status.optString("Reason", "");
                String details;
                if (userMsg != null && !userMsg.trim().isEmpty()) {
                    details = userMsg;
                } else if (reason != null && !reason.trim().isEmpty()) {
                    details = reason;
                } else {
                    details = "Codice errore " + code;
                }
                throw new IOException("Errore Fronius API (" + code + "): " + details);
            }
        }

        JSONObject body = root.optJSONObject("Body");
        if (body == null) {
            throw new IOException("Risposta Fronius non valida: campo Body mancante");
        }
        JSONObject data = body.optJSONObject("Data");
        if (data == null) {
            throw new IOException("Risposta Fronius non valida: campo Body.Data mancante");
        }
        JSONObject site = data.optJSONObject("Site");
        if (site == null) {
            throw new IOException("Risposta Fronius non valida: campo Body.Data.Site mancante");
        }
        JSONObject inverters = data.optJSONObject("Inverters");

        Double rawPv = optNullableDouble(site, "P_PV");
        Double rawGrid = optNullableDouble(site, "P_Grid");
        Double rawLoad = optNullableDouble(site, "P_Load");
        Double rawAkku = optNullableDouble(site, "P_Akku");
        Double siteEDayWh = optNullableDouble(site, "E_Day");

        double inverterPowerSum = 0.0;
        boolean hasInverterPower = false;
        double inverterEDaySumWh = 0.0;
        boolean hasInverterEDay = false;
        double socSum = 0.0;
        int socCount = 0;

        if (inverters != null) {
            JSONObject preferredInv = inverters.optJSONObject(String.valueOf(preferredDeviceId));
            if (preferredInv != null) {
                Double invP = optNullableDouble(preferredInv, "P");
                if (invP != null) {
                    inverterPowerSum = invP;
                    hasInverterPower = true;
                }
                Double invEDay = optNullableDouble(preferredInv, "E_Day");
                if (invEDay != null) {
                    inverterEDaySumWh = invEDay;
                    hasInverterEDay = true;
                }
                Double invSoc = optNullableDouble(preferredInv, "SOC");
                if (invSoc != null) {
                    socSum = invSoc;
                    socCount = 1;
                }
            } else {
                Iterator<String> keys = inverters.keys();
                while (keys.hasNext()) {
                    String id = keys.next();
                    JSONObject inv = inverters.optJSONObject(id);
                    if (inv == null) continue;

                    Double invP = optNullableDouble(inv, "P");
                    if (invP != null) {
                        inverterPowerSum += invP;
                        hasInverterPower = true;
                    }

                    Double invEDay = optNullableDouble(inv, "E_Day");
                    if (invEDay != null) {
                        inverterEDaySumWh += invEDay;
                        hasInverterEDay = true;
                    }

                    Double invSoc = optNullableDouble(inv, "SOC");
                    if (invSoc != null) {
                        socSum += invSoc;
                        socCount++;
                    }
                }
            }
        }

        Double effectiveEDayWh = siteEDayWh != null ? siteEDayWh : (hasInverterEDay ? inverterEDaySumWh : null);
        Double effectiveSoc = socCount > 0 ? (socSum / socCount) : null;
        Double effectiveInverterPower = hasInverterPower ? inverterPowerSum : null;

        return mapValues(rawPv, rawGrid, rawLoad, rawAkku, effectiveEDayWh, effectiveSoc, effectiveInverterPower, timestamp);
    }

    /**
     * Pure telemetry mapping and sign normalization from Fronius Solar API v1 units/signs
     * to SolarDeck unified {@link SolarTelemetry}.
     */
    public static SolarTelemetry mapValues(
        Double pvW,
        Double gridW,
        Double loadW,
        Double akkuW,
        Double eDayWh,
        Double socPct,
        Double inverterActivePowerW,
        long timestamp
    ) {
        int pvPowerW = pvW != null ? Math.max(0, (int) Math.round(pvW)) : 0;
        int gridPowerW = gridW != null ? (int) Math.round(-gridW) : 0;
        int batteryPowerW = akkuW != null ? (int) Math.round(-akkuW) : 0;
        double dailyYieldKWh = eDayWh != null ? Math.max(0.0, eDayWh / 1000.0) : 0.0;
        double batterySoc = socPct != null ? Math.min(100.0, Math.max(0.0, socPct)) : 0.0;

        boolean hasBattery = (akkuW != null) || (socPct != null);
        boolean hasMeter = (gridW != null) || (loadW != null);

        int activePowerW;
        if (inverterActivePowerW != null) {
            activePowerW = (int) Math.round(inverterActivePowerW);
        } else if (loadW != null) {
            activePowerW = (int) Math.round(Math.abs(loadW)) + gridPowerW;
        } else {
            activePowerW = pvPowerW;
        }

        int normalizedLoadW;
        if (loadW != null) {
            normalizedLoadW = (int) Math.round(Math.abs(loadW));
        } else {
            normalizedLoadW = Math.max(0, activePowerW - gridPowerW);
        }

        return new SolarTelemetry(
            pvPowerW,
            activePowerW,
            normalizedLoadW,
            dailyYieldKWh,
            gridPowerW,
            batterySoc,
            batteryPowerW,
            timestamp,
            true,
            hasBattery,
            hasMeter,
            null
        );
    }

    private static Double optNullableDouble(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) return null;
        double v = obj.optDouble(key, Double.NaN);
        return (Double.isNaN(v) || Double.isInfinite(v)) ? null : v;
    }
}

package com.huaweitv.solar.data.source

import com.huaweitv.solar.data.model.InverterBrand
import com.huaweitv.solar.data.model.SolarTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Local LAN data source for Fronius inverters supporting Solar API v1.
 * Uses pure [HttpURLConnection] and [org.json.JSONObject] with zero external dependencies.
 */
class FroniusDataSource(
    private val ipAddress: String,
    private val port: Int = 80,
    private val deviceId: Int = 1
) : InverterDataSource {

    override val brand: InverterBrand = InverterBrand.FRONIUS

    @Volatile
    private var connected = false

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    override suspend fun connect() {
        val host = ipAddress.trim()
        if (host.isEmpty()) {
            connected = false
            throw IOException("Indirizzo IP Fronius mancante")
        }
        connected = true
    }

    override suspend fun fetchTelemetry(): SolarTelemetry = withContext(Dispatchers.IO) {
        val host = ipAddress.trim()
        if (host.isEmpty()) {
            connected = false
            throw IOException("Indirizzo IP Fronius mancante")
        }

        val effectivePort = if (port in 1..65535) port else InverterBrand.FRONIUS.defaultPort
        val endpoint = if (effectivePort == 80) {
            "http://$host/solar_api/v1/GetPowerFlowRealtimeData.fcgi"
        } else {
            "http://$host:$effectivePort/solar_api/v1/GetPowerFlowRealtimeData.fcgi"
        }

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Connection", "close")
        }
        activeConnection = conn

        try {
            val statusCode = conn.responseCode
            if (statusCode !in 200..299) {
                connected = false
                throw IOException("HTTP $statusCode da inverter Fronius ($endpoint)")
            }

            val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { reader ->
                reader.readText()
            }
            val telemetry = parseRealtimeJson(body, preferredDeviceId = deviceId)
            connected = true
            telemetry
        } catch (e: Exception) {
            connected = false
            throw e
        } finally {
            if (activeConnection === conn) {
                activeConnection = null
            }
            runCatching { conn.disconnect() }
        }
    }

    override fun disconnect() {
        connected = false
        val conn = activeConnection
        activeConnection = null
        if (conn != null) {
            runCatching { conn.disconnect() }
        }
    }

    override fun isConnected(): Boolean = connected

    companion object {
        private const val TIMEOUT_MS = 3500

        /**
         * Parses a Fronius `GetPowerFlowRealtimeData.fcgi` JSON payload using [org.json.JSONObject].
         */
        fun parseRealtimeJson(
            json: String,
            timestamp: Long = System.currentTimeMillis(),
            preferredDeviceId: Int = 1
        ): SolarTelemetry {
            val root = try {
                JSONObject(json)
            } catch (e: Exception) {
                throw IOException("Risposta JSON Fronius non valida: ${e.message}", e)
            }

            val head = root.optJSONObject("Head")
            val status = head?.optJSONObject("Status")
            if (status != null) {
                val code = status.optInt("Code", 0)
                if (code != 0) {
                    val userMsg = status.optString("UserMessage", "")
                    val reason = status.optString("Reason", "")
                    val details = when {
                        userMsg.isNotBlank() -> userMsg
                        reason.isNotBlank() -> reason
                        else -> "Codice errore $code"
                    }
                    throw IOException("Errore Fronius API ($code): $details")
                }
            }

            val body = root.optJSONObject("Body")
                ?: throw IOException("Risposta Fronius non valida: campo Body mancante")
            val data = body.optJSONObject("Data")
                ?: throw IOException("Risposta Fronius non valida: campo Body.Data mancante")
            val site = data.optJSONObject("Site")
                ?: throw IOException("Risposta Fronius non valida: campo Body.Data.Site mancante")
            val inverters = data.optJSONObject("Inverters")

            val rawPv = site.optNullableDouble("P_PV")
            val rawGrid = site.optNullableDouble("P_Grid")
            val rawLoad = site.optNullableDouble("P_Load")
            val rawAkku = site.optNullableDouble("P_Akku")
            val siteEDayWh = site.optNullableDouble("E_Day")

            var inverterPowerSum = 0.0
            var hasInverterPower = false
            var inverterEDaySumWh = 0.0
            var hasInverterEDay = false
            var socSum = 0.0
            var socCount = 0

            if (inverters != null) {
                val preferredInv = inverters.optJSONObject(preferredDeviceId.toString())
                if (preferredInv != null) {
                    preferredInv.optNullableDouble("P")?.let {
                        inverterPowerSum = it
                        hasInverterPower = true
                    }
                    preferredInv.optNullableDouble("E_Day")?.let {
                        inverterEDaySumWh = it
                        hasInverterEDay = true
                    }
                    preferredInv.optNullableDouble("SOC")?.let {
                        socSum = it
                        socCount = 1
                    }
                } else {
                    val keys = inverters.keys()
                    while (keys.hasNext()) {
                        val id = keys.next()
                        val inv = inverters.optJSONObject(id) ?: continue

                        inv.optNullableDouble("P")?.let {
                            inverterPowerSum += it
                            hasInverterPower = true
                        }
                        inv.optNullableDouble("E_Day")?.let {
                            inverterEDaySumWh += it
                            hasInverterEDay = true
                        }
                        inv.optNullableDouble("SOC")?.let {
                            socSum += it
                            socCount++
                        }
                    }
                }
            }

            return mapValues(
                pvW = rawPv,
                gridW = rawGrid,
                loadW = rawLoad,
                akkuW = rawAkku,
                eDayWh = siteEDayWh ?: if (hasInverterEDay) inverterEDaySumWh else null,
                socPct = if (socCount > 0) socSum / socCount else null,
                inverterActivePowerW = if (hasInverterPower) inverterPowerSum else null,
                timestamp = timestamp
            )
        }

        /**
         * Pure telemetry mapping and sign normalization from Fronius Solar API v1 units/signs
         * to SolarDeck unified [SolarTelemetry].
         *
         * Fronius -> SolarDeck sign alignment:
         * - P_PV (null -> 0): >= 0 W
         * - P_Grid (null -> 0): Fronius + import / - export -> negated (-P_Grid) so + = export, - = import
         * - P_Load (null -> 0): Fronius negative load -> normalized positive abs(P_Load)
         * - P_Akku (null -> 0): Fronius + discharge / - charge -> negated (-P_Akku) so + = charge, - = discharge
         * - E_Day (Wh -> kWh): divided by 1000.0
         */
        fun mapValues(
            pvW: Double?,
            gridW: Double?,
            loadW: Double?,
            akkuW: Double?,
            eDayWh: Double?,
            socPct: Double?,
            inverterActivePowerW: Double?,
            timestamp: Long = System.currentTimeMillis()
        ): SolarTelemetry {
            val pvPowerW = (pvW ?: 0.0).roundToInt().coerceAtLeast(0)
            val gridPowerW = if (gridW != null) (-gridW).roundToInt() else 0
            val batteryPowerW = if (akkuW != null) (-akkuW).roundToInt() else 0
            val dailyYieldKWh = ((eDayWh ?: 0.0) / 1000.0).coerceAtLeast(0.0)
            val batterySoc = (socPct ?: 0.0).coerceIn(0.0, 100.0)

            val hasBattery = akkuW != null || socPct != null
            val hasMeter = gridW != null || loadW != null

            val activePowerW = when {
                inverterActivePowerW != null -> inverterActivePowerW.roundToInt()
                loadW != null -> abs(loadW).roundToInt() + gridPowerW
                else -> pvPowerW
            }

            val normalizedLoadW = when {
                loadW != null -> abs(loadW).roundToInt()
                else -> (activePowerW - gridPowerW).coerceAtLeast(0)
            }

            return SolarTelemetry(
                pvPowerW = pvPowerW,
                activePowerW = activePowerW,
                dailyYieldKWh = dailyYieldKWh,
                gridPowerW = gridPowerW,
                batterySoc = batterySoc,
                batteryPowerW = batteryPowerW,
                loadPowerW = normalizedLoadW,
                timestamp = timestamp,
                isConnected = true,
                hasBattery = hasBattery,
                hasMeter = hasMeter,
                errorMessage = null
            )
        }

        private fun JSONObject.optNullableDouble(key: String): Double? {
            if (!has(key) || isNull(key)) return null
            val v = optDouble(key, Double.NaN)
            return if (v.isNaN() || v.isInfinite()) null else v
        }
    }
}

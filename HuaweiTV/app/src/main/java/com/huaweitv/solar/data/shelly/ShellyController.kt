package com.huaweitv.solar.data.shelly

import com.huaweitv.solar.data.model.AppSettings
import com.huaweitv.solar.data.model.ShellyGen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Local LAN HTTP controller for Shelly relays (Gen 1 and Gen 2+), zero cloud dependencies.
 */
class ShellyController {

    suspend fun turnOff(settings: AppSettings): Result<Unit> =
        turnOff(
            ip = settings.shelly1Ip,
            channel = settings.shelly1Channel,
            gen = settings.shelly1Gen
        )

    suspend fun turnOff(ip: String, channel: Int, gen: ShellyGen): Result<Unit> = withContext(Dispatchers.IO) {
        val url = buildTurnOffUrl(
            ip = ip,
            channel = channel,
            gen = gen
        )
        executeHttpGet(url)
    }

    private fun executeHttpGet(endpoint: String): Result<Unit> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                useCaches = false
                setRequestProperty("Connection", "close")
            }
            val code = conn.responseCode
            if (code in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("HTTP $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    companion object {
        private const val TIMEOUT_MS = 3000

        fun buildTurnOffUrl(ip: String, channel: Int, gen: ShellyGen): String {
            val cleanIp = ip.trim()
            val cleanChannel = channel.coerceAtLeast(0)
            return when (gen) {
                ShellyGen.GEN1 -> "http://$cleanIp/relay/$cleanChannel?turn=off"
                ShellyGen.GEN2 -> "http://$cleanIp/rpc/Switch.Set?id=$cleanChannel&on=false"
            }
        }
    }
}

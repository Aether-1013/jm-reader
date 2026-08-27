package com.jm.reader.data.net

import com.jm.reader.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Bootstraps the API host. Mirrors the web app's FETCH_HOST flow:
 *  1. fetch the encrypted host config from the CDN txt files
 *  2. decrypt with key = ASCII(md5("diosfjckwpqpdfjkvnqQjsik")) (AES-256-ECB)
 *  3. pick a random host from the `Server` array and form https://<server>/
 *  4. fall back to the embedded encrypted `hostCode` if the txt files fail
 */
class HostManager(
    private val session: SessionManager,
    private val http: OkHttpClient,
) {
    private val hostUrls = listOf(
        "https://rup4a04-c02.tos-cn-hongkong.bytepluses.com/newsvr-2025.txt",
        "https://rup4a04-c01.tos-ap-southeast-1.bytepluses.com/newsvr-2025.txt",
    )

    // Encrypted host config baked into the web bundle (REACT_APP_HOST_BACKUP_CODE).
    private val fallbackHostCode =
        "X+bnzYIcwF6C7Rd3T7njPM0aeKgOoB+o/+lwS/klMzdv/yrVPk0UikahXv/MGxHqaSCwOCfGQjX0QpMpSxvr4+vsg/4ohUY8jspsJ7gSoQU5ANBMM99J2WtKxGgIBLq9PjCaS/34KK9HSiLJdaXz40oGSEHkBl8L0tTfPRC+dmPlp2CJ/97anZkSqForX+hTFgVoS0BZl/gXUQdF2njjAjgJwg13qbTJd3QB0CExaztlrC1Z1QGhXNjxM0Zk5v8i8JoPtTe7LWW55r96oLJrDOG60uspZxlV+Jp3FOdRXFH++Mann1Vo88iv9kbTa1f1FkaUCEgPpxNKmnCpnNUhNgCZExIlg7RcQQ6Ru+ys1D4+GAhA3Z1gUDMsYIit/bD8H30ZoBip59iW0Nx4haPYM5Pb9GyYRAkIJfQRP46w1JQXMPir0MCxMnvFahb0xzOULRx+WBrOe/oMKD1Dsohhxw=="

    /** Returns the API base URL (https://<host>/), caching it in the session. */
    suspend fun bootstrap(): Result<String> {
        val config = fetchConfig()
        if (config != null) {
            val server = pickServer(config)
            if (server != null) {
                val apiUrl = "https://$server/"
                session.apiUrl = apiUrl
                return Result.success(apiUrl)
            }
        }
        // Fall back to the previously cached host before giving up.
        val cached = session.apiUrl
        if (!cached.isNullOrBlank()) return Result.success(cached)
        return Result.failure(IllegalStateException("無法取得 API 主機"))
    }

    private suspend fun fetchConfig(): JSONObject? = withContext(Dispatchers.IO) {
        for (u in hostUrls) {
            val text = try {
                http.newCall(Request.Builder().url(u).build()).execute().use { it.body?.string() }
            } catch (_: Exception) {
                null
            }
            if (text.isNullOrBlank()) continue
            val plain = Crypto.decryptHostText(text) ?: continue
            val json = runCatching { JSONObject(plain) }.getOrNull() ?: continue
            if (json.optJSONArray("Server") != null) return@withContext json
        }
        val plain = Crypto.decryptHostText(fallbackHostCode) ?: return@withContext null
        runCatching { JSONObject(plain) }.getOrNull()
    }

    private fun pickServer(config: JSONObject): String? {
        val arr = config.optJSONArray("Server") ?: return null
        val servers = (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        return servers.takeIf { it.isNotEmpty() }?.random()
    }
}

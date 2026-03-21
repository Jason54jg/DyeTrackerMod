package com.dyetracker.api

import com.dyetracker.DyeTrackerMod
import com.dyetracker.config.ConfigManager
import com.dyetracker.data.PlayerRngData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * HTTP client for communicating with the DyeTracker API.
 */
object ApiClient {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val CONTENT_TYPE_JSON = "application/json"
    private const val AUTH_HEADER = "Authorization"

    /**
     * Result of an API call.
     */
    sealed class ApiResult<out T> {
        data class Success<T>(val data: T) : ApiResult<T>()
        data class Error(val message: String, val statusCode: Int = 0) : ApiResult<Nothing>()
    }

    /**
     * Get the auth token from config if available.
     */
    private fun getAuthHeader(): String? {
        val token = ConfigManager.config.authToken
        return if (token.isNotEmpty()) "Bearer $token" else null
    }

    /**
     * Add auth header to request builder if token is available.
     */
    private fun HttpRequest.Builder.withAuth(): HttpRequest.Builder {
        getAuthHeader()?.let { header(AUTH_HEADER, it) }
        return this
    }

    /**
     * Start the verification process with a link code.
     * Returns a serverId challenge for Mojang verification.
     */
    fun startVerify(code: String): ApiResult<StartVerifyResponse> {
        val url = "${ConfigManager.config.apiUrl}/api/v1/auth/start-verify"
        val body = """{"code":"$code"}"""

        DyeTrackerMod.info("API: POST {} (code={})", url, code)

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            DyeTrackerMod.info("API: Response status={}", response.statusCode())

            if (response.statusCode() == 200) {
                val data = json.decodeFromString<StartVerifyResponse>(response.body())
                DyeTrackerMod.info("API: start-verify success, received serverId")
                ApiResult.Success(data)
            } else {
                val error = parseError(response.body())
                DyeTrackerMod.warn("start-verify failed: {} ({})", error, response.statusCode())
                ApiResult.Error(error, response.statusCode())
            }
        } catch (e: Exception) {
            DyeTrackerMod.error("start-verify exception", e)
            ApiResult.Error("Network error: ${e.message}")
        }
    }

    /**
     * Complete the verification process after calling Mojang's joinServer.
     */
    fun completeVerify(code: String, username: String): ApiResult<CompleteVerifyResponse> {
        val url = "${ConfigManager.config.apiUrl}/api/v1/auth/complete-verify"
        val body = """{"code":"$code","username":"$username"}"""

        DyeTrackerMod.info("API: POST {} (code={}, username={})", url, code, username)

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            DyeTrackerMod.info("API: Response status={}", response.statusCode())

            if (response.statusCode() == 200) {
                val data = json.decodeFromString<CompleteVerifyResponse>(response.body())
                DyeTrackerMod.info("API: complete-verify success, uuid={}", data.uuid)
                ApiResult.Success(data)
            } else {
                val error = parseError(response.body())
                DyeTrackerMod.warn("complete-verify failed: {} ({})", error, response.statusCode())
                ApiResult.Error(error, response.statusCode())
            }
        } catch (e: Exception) {
            DyeTrackerMod.error("complete-verify exception", e)
            ApiResult.Error("Network error: ${e.message}")
        }
    }

    /**
     * Verify the current auth token by calling /auth/me.
     * Returns user info if token is valid.
     */
    fun getMe(): ApiResult<AuthMeResponse> {
        val url = "${ConfigManager.config.apiUrl}/api/v1/auth/me"

        DyeTrackerMod.info("API: GET {} (verifying token)", url)

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .withAuth()
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            DyeTrackerMod.info("API: Response status={}", response.statusCode())

            if (response.statusCode() == 200) {
                val data = json.decodeFromString<AuthMeResponse>(response.body())
                DyeTrackerMod.info("API: Token verified for user={}", data.username)
                ApiResult.Success(data)
            } else {
                val error = parseError(response.body())
                DyeTrackerMod.warn("Token verification failed: {} ({})", error, response.statusCode())
                ApiResult.Error(error, response.statusCode())
            }
        } catch (e: Exception) {
            DyeTrackerMod.error("getMe exception", e)
            ApiResult.Error("Network error: ${e.message}")
        }
    }

    /**
     * Sync RNG data to the backend.
     * Requires authentication.
     */
    fun syncRngData(data: PlayerRngData): ApiResult<SyncRngDataResponse> {
        val url = "${ConfigManager.config.apiUrl}/api/v1/rng-data"

        // Build the request body using proper JSON encoding
        val requestBody = SyncRngDataRequest(
            slayerMeters = data.slayerMeters,
            dungeonMeters = data.dungeonMeters,
            nucleusMeter = data.nucleusMeter,
            experimentationMeter = data.experimentationMeter,
            mineshaftPity = data.mineshaftPity,
            archfiendDye = data.archfiendDye,
            copperDye = data.copperDye,
            modTimestamp = System.currentTimeMillis()
        )
        val body = json.encodeToString(requestBody)

        DyeTrackerMod.info("API: POST {} (syncing RNG data)", url)

        return try {
            val authHeader = getAuthHeader()
            if (authHeader == null) {
                DyeTrackerMod.warn("syncRngData: No auth token available")
                return ApiResult.Error("Not authenticated", 401)
            }

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .header(AUTH_HEADER, authHeader)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            DyeTrackerMod.info("API: Response status={}", response.statusCode())

            if (response.statusCode() == 200) {
                val responseData = json.decodeFromString<SyncRngDataResponse>(response.body())
                DyeTrackerMod.info("API: syncRngData success, updatedAt={}", responseData.updatedAt)
                ApiResult.Success(responseData)
            } else {
                val error = parseError(response.body())
                DyeTrackerMod.warn("syncRngData failed: {} ({})", error, response.statusCode())
                ApiResult.Error(error, response.statusCode())
            }
        } catch (e: Exception) {
            DyeTrackerMod.error("syncRngData exception", e)
            ApiResult.Error("Network error: ${e.message}")
        }
    }

    private fun parseError(body: String): String {
        return try {
            val errorResponse = json.decodeFromString<ApiErrorResponse>(body)
            errorResponse.message
        } catch (e: Exception) {
            "Unknown error"
        }
    }
}

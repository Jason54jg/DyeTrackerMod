package com.dyetracker.auth

import com.dyetracker.DyeTrackerMod
import com.dyetracker.api.ApiClient
import com.dyetracker.config.ConfigManager
import com.dyetracker.dyeprogress.DyeProgressStore
import net.minecraft.client.MinecraftClient
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * Handles account verification via Mojang session server.
 *
 * The verification flow:
 * 1. Call backend /start-verify with link code -> get serverId
 * 2. Call Mojang joinServer with session token and serverId
 * 3. Call backend /complete-verify -> backend calls Mojang hasJoined
 * 4. Save auth token to config on success
 */
object AccountVerification {

    /**
     * Result of the verification process.
     */
    data class VerificationResult(
        val success: Boolean,
        val message: String,
        val uuid: String? = null,
        val username: String? = null
    )

    // Dedicated executor to avoid ForkJoinPool issues with Minecraft's classloading/SSL
    private val verifyExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "DyeTracker-Verify").apply { isDaemon = true }
    }

    /**
     * Verify account ownership using a link code.
     * Runs asynchronously to avoid blocking the main thread.
     */
    fun verifyAccount(linkCode: String): CompletableFuture<VerificationResult> {
        // Capture session info on main thread (must be done before async to avoid classloader issues)
        val session = MinecraftClient.getInstance().session
        val username = session.username
        val uuid = session.uuidOrNull
        val accessToken = session.accessToken

        return CompletableFuture.supplyAsync({
            try {

                DyeTrackerMod.info("[1/4] Starting account verification for {} with code {}", username, linkCode)
                DyeTrackerMod.info("[1/4] Using API URL: {}", ConfigManager.config.apiUrl)

                // Step 1: Get serverId challenge from backend
                DyeTrackerMod.info("[2/4] Requesting serverId challenge from backend...")
                val startResult = ApiClient.startVerify(linkCode)
                if (startResult is ApiClient.ApiResult.Error) {
                    DyeTrackerMod.warn("[2/4] Failed to get serverId: {}", startResult.message)
                    return@supplyAsync VerificationResult(
                        success = false,
                        message = startResult.message
                    )
                }

                val serverId = (startResult as ApiClient.ApiResult.Success).data.serverId
                DyeTrackerMod.info("[2/4] Received serverId challenge: {}", serverId)

                // Step 2: Call Mojang joinServer directly via HTTP (bypasses sessionService mapping issues)
                DyeTrackerMod.info("[3/4] Calling Mojang joinServer via direct HTTP...")
                DyeTrackerMod.info("[3/4] Session UUID: {}", uuid)
                DyeTrackerMod.info("[3/4] Session username: {}", username)
                DyeTrackerMod.info("[3/4] Access token present: {}", accessToken.isNotEmpty())

                if (uuid == null) {
                    DyeTrackerMod.error("[3/4] Session UUID is null - are you logged in?")
                    return@supplyAsync VerificationResult(
                        success = false,
                        message = "Not logged in - session UUID is null"
                    )
                }

                try {
                    val joinUrl = URI.create("https://sessionserver.mojang.com/session/minecraft/join")
                    val joinBody = """{"accessToken":"$accessToken","selectedProfile":"${uuid.toString().replace("-", "")}","serverId":"$serverId"}"""

                    DyeTrackerMod.info("[3/4] POST to {}", joinUrl)

                    val request = java.net.http.HttpRequest.newBuilder()
                        .uri(joinUrl)
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(joinBody))
                        .timeout(java.time.Duration.ofSeconds(15))
                        .build()

                    val httpClient = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(10))
                        .build()

                    val response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                    DyeTrackerMod.info("[3/4] Mojang response status: {}", response.statusCode())

                    if (response.statusCode() == 204) {
                        DyeTrackerMod.info("[3/4] Mojang joinServer succeeded!")
                    } else {
                        DyeTrackerMod.error("[3/4] Mojang joinServer failed: {} - {}", response.statusCode(), response.body())
                        return@supplyAsync VerificationResult(
                            success = false,
                            message = "Mojang auth failed (${response.statusCode()}) - try restarting Minecraft"
                        )
                    }
                } catch (e: Exception) {
                    DyeTrackerMod.error("[3/4] Mojang joinServer error: {} - {}", e.javaClass.simpleName, e.message)
                    return@supplyAsync VerificationResult(
                        success = false,
                        message = "Mojang connection error: ${e.message}"
                    )
                }

                // Step 3: Complete verification with backend
                DyeTrackerMod.info("[4/4] Completing verification with backend...")
                val completeResult = ApiClient.completeVerify(linkCode, username)
                if (completeResult is ApiClient.ApiResult.Error) {
                    DyeTrackerMod.warn("[4/4] Backend verification failed: {}", completeResult.message)
                    return@supplyAsync VerificationResult(
                        success = false,
                        message = completeResult.message
                    )
                }

                val verifyData = (completeResult as ApiClient.ApiResult.Success).data
                DyeTrackerMod.info("[4/4] Backend verified! UUID: {}", verifyData.uuid)

                // Step 4: Save to config
                ConfigManager.update { config ->
                    config.copy(
                        linkedUuid = verifyData.uuid,
                        linkedUsername = verifyData.username,
                        authToken = verifyData.token
                    )
                }

                // Step 5: Verify token works by calling /auth/me
                DyeTrackerMod.info("[5/5] Verifying token with /auth/me...")
                val meResult = ApiClient.getMe()
                if (meResult is ApiClient.ApiResult.Error) {
                    DyeTrackerMod.warn("[5/5] Token verification failed: {}", meResult.message)
                    // Clear the config since token doesn't work
                    ConfigManager.update { config ->
                        config.copy(linkedUuid = "", linkedUsername = "", authToken = "")
                    }
                    return@supplyAsync VerificationResult(
                        success = false,
                        message = "Token verification failed: ${meResult.message}"
                    )
                }

                val meData = (meResult as ApiClient.ApiResult.Success).data
                DyeTrackerMod.info("[5/5] Token verified! Authenticated as: {}", meData.username)

                DyeTrackerMod.info("Account verification successful for {}", verifyData.username)

                VerificationResult(
                    success = true,
                    message = "Account linked successfully!",
                    uuid = verifyData.uuid,
                    username = verifyData.username
                )

            } catch (e: Exception) {
                DyeTrackerMod.error("Verification failed", e)
                VerificationResult(
                    success = false,
                    message = "Verification error: ${e.message}"
                )
            }
        }, verifyExecutor)
    }

    /**
     * Check if the current account is already linked.
     */
    fun isLinked(): Boolean = ConfigManager.config.isLinked()

    /**
     * Unlink the current account.
     */
    fun unlink() {
        ConfigManager.update { config ->
            config.copy(
                linkedUuid = "",
                linkedUsername = "",
                authToken = ""
            )
        }
        // Drop volatile per-account dye progress so widgets don't show the old account's stale
        // values after unlinking (placements remain in config but render as Loading until relinked).
        DyeProgressStore.clear()
        DyeTrackerMod.info("Account unlinked")
    }
}

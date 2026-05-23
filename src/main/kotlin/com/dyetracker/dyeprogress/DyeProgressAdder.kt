package com.dyetracker.dyeprogress

import com.dyetracker.api.ApiClient
import com.dyetracker.api.ProfileSummary
import com.dyetracker.config.ConfigManager
import com.dyetracker.rotation.DyeSprites

/**
 * Shared "resolve a typed profile name and add a single-dye widget" logic, used by both the
 * in-screen add panel (task 34-6) and the `/dyetracker dye add` command (task 34-7) so the
 * validation + resolution path lives in one place.
 *
 * [resolveProfileId] is pure and unit-tested; [resolveAndAdd] performs the blocking profile
 * fetch and persists the widget, so it must be called off the main thread.
 */
object DyeProgressAdder {

    /** Outcome of [resolveAndAdd], mapped to UI feedback by the caller. */
    sealed class Result {
        /** Widget added; [id] is the new widget's id (for auto-focus). */
        data class Added(val id: String) : Result()

        /** No linked account / blank username. */
        object NotLinked : Result()

        /** The dye id is not a known bundled dye. */
        object InvalidDye : Result()

        /** The profile field was empty. */
        object EmptyProfile : Result()

        /** No profile matched the typed name; [available] lists the player's profile names. */
        data class UnknownProfile(val typed: String, val available: List<String>) : Result()

        /** The profiles fetch failed; [message] is the error detail. */
        data class NetworkError(val message: String) : Result()
    }

    /**
     * Resolve a typed profile name to its `profileId` via a case-insensitive `cute_name` match,
     * or null if none matches. Pure — no network.
     */
    fun resolveProfileId(profiles: List<ProfileSummary>, typedName: String): String? {
        val target = typedName.trim()
        if (target.isEmpty()) return null
        return profiles.firstOrNull { it.cuteName.equals(target, ignoreCase = true) }?.id
    }

    /**
     * Validate inputs, resolve [profileName] → profileId for [username] via the profiles endpoint,
     * add the widget to config, and trigger an immediate poll so its progress appears fast.
     * Blocking (HTTP) — call off the main thread.
     */
    fun resolveAndAdd(username: String, dyeId: String, profileName: String): Result {
        if (username.isBlank()) return Result.NotLinked
        if (!DyeSprites.has(dyeId)) return Result.InvalidDye
        val name = profileName.trim()
        if (name.isEmpty()) return Result.EmptyProfile

        return when (val res = ApiClient.fetchProfiles(username)) {
            is ApiClient.ApiResult.Success -> {
                val profileId = resolveProfileId(res.data.profiles, name)
                    ?: return Result.UnknownProfile(name, res.data.profiles.map { it.cuteName })
                val cfg = DyeProgressWidgetConfig(
                    id = DyeProgressWidgetConfig.newId(),
                    dyeId = dyeId,
                    profileName = name,
                    profileId = profileId,
                )
                ConfigManager.dyeProgressPlacements.add(cfg)
                DyeProgressPoller.pollOnce()
                Result.Added(cfg.id)
            }

            is ApiClient.ApiResult.Error -> Result.NetworkError(res.message)
        }
    }
}

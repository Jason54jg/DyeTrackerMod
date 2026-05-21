package com.dyetracker.overlay

// All texture construction and release MUST run on the render thread because Mojang's
// `NativeImage` / `NativeImageBackedTexture` allocate native (off-heap) buffers and
// upload OpenGL textures. Calling from any other thread crashes the game. `upload`
// and `release` accept any-thread calls and schedule via `MinecraftClient.execute`.

import com.dyetracker.DyeTrackerMod
import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the GPU resources behind every active overlay. Converts a [OverlayDecoder.DecodedImage]
 * into per-frame `NativeImageBackedTexture`s registered under stable `Identifier`s,
 * then exposes a wall-clock-based [currentFrameIdentifier] selector. Renderer code
 * pulls an Identifier per overlay every frame and draws it via `DrawContext`.
 */
object OverlayTextureManager {

    private const val MOD_ID = "dyetracker"
    private const val FRAME_ID_PREFIX = "gif_"
    private const val FRAME_ID_INFIX = "_frame_"
    private const val BYTES_PER_PIXEL = 4
    private const val HEX_RADIX = 16
    private val ID_SANITIZER_REGEX = Regex("[^a-z0-9_]")

    private data class OverlayState(
        val frameIds: List<Identifier>,
        val textures: List<NativeImageBackedTexture>,
        val cumulativeDelaysMs: LongArray,
        val totalDurationMs: Long,
        val width: Int,
        val height: Int,
        @Volatile var lastFrameIndex: Int = 0,
    )

    private val overlays = ConcurrentHashMap<String, OverlayState>()
    private val warnedMissing = ConcurrentHashMap.newKeySet<String>()

    /**
     * Upload all frames of [decoded] under [overlayId]. Returns a future that completes
     * after textures are registered on the render thread. Safe to call from any thread.
     */
    fun upload(overlayId: String, decoded: OverlayDecoder.DecodedImage): CompletableFuture<Unit> {
        val future = CompletableFuture<Unit>()
        val client = MinecraftClient.getInstance()
        if (decoded.frames.isEmpty()) {
            future.completeExceptionally(IllegalArgumentException("upload requires at least one frame"))
            return future
        }
        client.execute {
            try {
                releaseInternal(overlayId) // replace if already uploaded

                val sanitized = sanitize(overlayId)
                val frameIds = ArrayList<Identifier>(decoded.frames.size)
                val textures = ArrayList<NativeImageBackedTexture>(decoded.frames.size)

                for ((index, frame) in decoded.frames.withIndex()) {
                    val nativeImage = bufferedImageToNativeImage(frame.image)
                    val id = Identifier.of(MOD_ID, "${FRAME_ID_PREFIX}${sanitized}${FRAME_ID_INFIX}$index")
                    val texture = NativeImageBackedTexture({ id.toString() }, nativeImage)
                    client.textureManager.registerTexture(id, texture)
                    frameIds.add(id)
                    textures.add(texture)
                }

                val cumulative = LongArray(decoded.frames.size)
                var running = 0L
                for ((i, frame) in decoded.frames.withIndex()) {
                    // Static frame uses Int.MAX_VALUE as a sentinel; clamp to a sane long so
                    // wallClock % total doesn't overflow.
                    val d = frame.delayMs.toLong().coerceAtMost(Int.MAX_VALUE.toLong())
                    running += d
                    cumulative[i] = running
                }

                val state = OverlayState(
                    frameIds = frameIds,
                    textures = textures,
                    cumulativeDelaysMs = cumulative,
                    totalDurationMs = running.coerceAtLeast(1L),
                    width = decoded.width,
                    height = decoded.height,
                )
                overlays[overlayId] = state
                warnedMissing.remove(overlayId)

                val vramBytes = decoded.width.toLong() * decoded.height * BYTES_PER_PIXEL * decoded.frames.size
                DyeTrackerMod.info(
                    "Overlay '{}' uploaded ({} frames, {}x{}, ~{} KB VRAM)",
                    overlayId, decoded.frames.size, decoded.width, decoded.height, vramBytes / 1024
                )
                future.complete(Unit)
            } catch (e: Throwable) {
                DyeTrackerMod.error("Failed to upload overlay '$overlayId'", e)
                future.completeExceptionally(e)
            }
        }
        return future
    }

    /** Release an overlay's textures. Schedules to render thread if needed. */
    fun release(overlayId: String) {
        val client = MinecraftClient.getInstance()
        if (client.isOnThread) {
            releaseInternal(overlayId)
        } else {
            client.execute { releaseInternal(overlayId) }
        }
    }

    /** Release every overlay; called on mod/client shutdown. */
    fun releaseAll() {
        val client = MinecraftClient.getInstance()
        if (client.isOnThread) {
            overlays.keys.toList().forEach(::releaseInternal)
        } else {
            client.execute { overlays.keys.toList().forEach(::releaseInternal) }
        }
    }

    private fun releaseInternal(overlayId: String) {
        val state = overlays.remove(overlayId) ?: return
        val client = MinecraftClient.getInstance()
        for (id in state.frameIds) {
            try {
                // destroyTexture invokes texture.close() internally; do NOT also call
                // state.textures[i].close() — that would be a double-close on NativeImage.
                client.textureManager.destroyTexture(id)
            } catch (e: Throwable) {
                DyeTrackerMod.warn("destroyTexture failed for {}: {}", id, e.message ?: e.javaClass.simpleName)
            }
        }
        warnedMissing.remove(overlayId)
        OverlayHudRenderer.clearFirstRenderMark(overlayId)
        DyeTrackerMod.info("Overlay '{}' released ({} frames)", overlayId, state.frameIds.size)
    }

    /**
     * Identifier of the frame to draw at [wallClockMs]. Returns null if [overlayId] has
     * not been uploaded yet. When [paused] is true, returns the frame previously selected
     * (or frame 0 if none yet) so animation freezes on the pause screen.
     */
    fun currentFrameIdentifier(overlayId: String, wallClockMs: Long, paused: Boolean): Identifier? {
        val state = overlays[overlayId]
        if (state == null) {
            if (warnedMissing.add(overlayId)) {
                DyeTrackerMod.warn("Render requested for overlay '{}' that is not uploaded yet", overlayId)
            }
            return null
        }
        if (paused) {
            return state.frameIds[state.lastFrameIndex]
        }
        val total = state.totalDurationMs
        val t = if (total <= 0L) 0L else ((wallClockMs % total) + total) % total
        val idx = findFrameIndex(state.cumulativeDelaysMs, t)
        state.lastFrameIndex = idx
        return state.frameIds[idx]
    }

    /** Source width/height in pixels (the rendered size scales by [GifOverlayConfig.scale]). */
    fun dimensions(overlayId: String): Pair<Int, Int>? =
        overlays[overlayId]?.let { it.width to it.height }

    /** Allocation-free width accessor for the hot render loop. Returns -1 if missing. */
    fun widthOf(overlayId: String): Int = overlays[overlayId]?.width ?: -1

    /** Allocation-free height accessor for the hot render loop. Returns -1 if missing. */
    fun heightOf(overlayId: String): Int = overlays[overlayId]?.height ?: -1

    /**
     * Binary search for the smallest index `i` with `cumulative[i] > t`. The cumulative
     * array is strictly increasing (delays are clamped >= 1ms by the decoder), so this
     * always finds a unique index. For `t >= cumulative.last()`, returns `last()` —
     * caller is responsible for the wall-clock modulo.
     */
    internal fun findFrameIndex(cumulative: LongArray, t: Long): Int {
        if (cumulative.isEmpty()) return 0
        var lo = 0
        var hi = cumulative.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (cumulative[mid] > t) {
                hi = mid
            } else {
                lo = mid + 1
            }
        }
        return lo
    }

    /**
     * Sanitize an arbitrary overlay id into a unique Identifier path. Mojang allows
     * `[a-z0-9_./-]+`; we use a stricter `[a-z0-9_]` subset (avoids `.` / `/` splitting
     * the path into segments) and append a stable hash suffix so distinct ids that
     * sanitize to the same prefix (e.g. `"abc-1"` vs `"abc_1"`) still produce distinct
     * Identifiers — collisions would otherwise cause one overlay's release to destroy
     * another overlay's live textures.
     */
    private fun sanitize(overlayId: String): String {
        val lower = overlayId.lowercase()
        val cleaned = ID_SANITIZER_REGEX.replace(lower, "_").ifEmpty { "anon" }
        val suffix = overlayId.hashCode().toUInt().toString(HEX_RADIX)
        return "${cleaned}_$suffix"
    }

    private fun bufferedImageToNativeImage(image: BufferedImage): NativeImage {
        val w = image.width
        val h = image.height
        val argbArray = IntArray(w * h)
        image.getRGB(0, 0, w, h, argbArray, 0, w)
        val ni = NativeImage(w, h, false)
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                ni.setColorArgb(x, y, argbArray[i++])
            }
        }
        return ni
    }
}

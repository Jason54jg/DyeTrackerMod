package com.dyetracker.ui.texture

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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Feature-agnostic GPU texture lifecycle + frame-animation backend for the UI toolkit.
 * Converts an [ImageFrames] (one or more frames + intrinsic size) into per-frame
 * `NativeImageBackedTexture`s registered under stable `Identifier`s keyed by an arbitrary
 * string id, then exposes a wall-clock-based [currentFrameIdentifier] selector. Sprite/image
 * widgets pull an Identifier per id every frame and draw it via `DrawContext`.
 *
 * Generalized from the GIF-specific overlay texture manager (PBI 28) so any image-bearing
 * widget reuses the same upload/release/animation machinery — no per-feature texture code.
 */
object ImageTextureManager {

    private const val FRAME_ID_PREFIX = "img_"
    private const val FRAME_ID_INFIX = "_frame_"
    private const val BYTES_PER_PIXEL = 4
    private const val HEX_RADIX = 16
    private val ID_SANITIZER_REGEX = Regex("[^a-z0-9_]")

    private data class ImageState(
        val frameIds: List<Identifier>,
        val textures: List<NativeImageBackedTexture>,
        val cumulativeDelaysMs: LongArray,
        val totalDurationMs: Long,
        val width: Int,
        val height: Int,
        @Volatile var lastFrameIndex: Int = 0,
    )

    private val images = ConcurrentHashMap<String, ImageState>()
    private val warnedMissing = ConcurrentHashMap.newKeySet<String>()
    private val releaseListeners = CopyOnWriteArrayList<(String) -> Unit>()

    /**
     * Register [listener] to be invoked with an image id immediately after its textures are
     * released (on the render thread). Lets features hook release — e.g. clear per-id debug
     * bookkeeping — without the toolkit depending on feature-specific code. Idempotent
     * listeners are recommended; the same listener added twice fires twice.
     */
    fun addReleaseListener(listener: (String) -> Unit) {
        releaseListeners.add(listener)
    }

    /**
     * Upload all frames of [image] under [id]. Returns a future that completes after textures
     * are registered on the render thread. Replaces any existing entry for [id]. Safe to call
     * from any thread.
     */
    fun upload(id: String, image: ImageFrames): CompletableFuture<Unit> {
        val future = CompletableFuture<Unit>()
        val client = MinecraftClient.getInstance()
        if (image.frames.isEmpty()) {
            future.completeExceptionally(IllegalArgumentException("upload requires at least one frame"))
            return future
        }
        client.execute {
            try {
                releaseInternal(id) // replace if already uploaded

                val sanitized = sanitize(id)
                val frameIds = ArrayList<Identifier>(image.frames.size)
                val textures = ArrayList<NativeImageBackedTexture>(image.frames.size)

                for ((index, frame) in image.frames.withIndex()) {
                    val nativeImage = bufferedImageToNativeImage(frame.image)
                    val frameId = Identifier.of(DyeTrackerMod.MOD_ID, "${FRAME_ID_PREFIX}${sanitized}${FRAME_ID_INFIX}$index")
                    val texture = NativeImageBackedTexture({ frameId.toString() }, nativeImage)
                    client.textureManager.registerTexture(frameId, texture)
                    frameIds.add(frameId)
                    textures.add(texture)
                }

                val cumulative = LongArray(image.frames.size)
                var running = 0L
                for ((i, frame) in image.frames.withIndex()) {
                    // Static frame uses Int.MAX_VALUE as a sentinel; clamp to a sane long so
                    // wallClock % total doesn't overflow.
                    val d = frame.delayMs.toLong().coerceAtMost(Int.MAX_VALUE.toLong())
                    running += d
                    cumulative[i] = running
                }

                val state = ImageState(
                    frameIds = frameIds,
                    textures = textures,
                    cumulativeDelaysMs = cumulative,
                    totalDurationMs = running.coerceAtLeast(1L),
                    width = image.width,
                    height = image.height,
                )
                images[id] = state
                warnedMissing.remove(id)

                val vramBytes = image.width.toLong() * image.height * BYTES_PER_PIXEL * image.frames.size
                DyeTrackerMod.info(
                    "Image '{}' uploaded ({} frames, {}x{}, ~{} KB VRAM)",
                    id, image.frames.size, image.width, image.height, vramBytes / 1024
                )
                future.complete(Unit)
            } catch (e: Throwable) {
                DyeTrackerMod.error("Failed to upload image '$id'", e)
                future.completeExceptionally(e)
            }
        }
        return future
    }

    /** Release an image's textures. Schedules to render thread if needed. */
    fun release(id: String) {
        val client = MinecraftClient.getInstance()
        if (client.isOnThread) {
            releaseInternal(id)
        } else {
            client.execute { releaseInternal(id) }
        }
    }

    /** Release every image; called on mod/client shutdown. */
    fun releaseAll() {
        val client = MinecraftClient.getInstance()
        if (client.isOnThread) {
            images.keys.toList().forEach(::releaseInternal)
        } else {
            client.execute { images.keys.toList().forEach(::releaseInternal) }
        }
    }

    private fun releaseInternal(id: String) {
        val state = images.remove(id) ?: return
        val client = MinecraftClient.getInstance()
        for (frameId in state.frameIds) {
            try {
                // destroyTexture invokes texture.close() internally; do NOT also call
                // state.textures[i].close() — that would be a double-close on NativeImage.
                client.textureManager.destroyTexture(frameId)
            } catch (e: Throwable) {
                DyeTrackerMod.warn("destroyTexture failed for {}: {}", frameId, e.message ?: e.javaClass.simpleName)
            }
        }
        warnedMissing.remove(id)
        releaseListeners.forEach { it(id) }
        DyeTrackerMod.info("Image '{}' released ({} frames)", id, state.frameIds.size)
    }

    /**
     * Identifier of the frame to draw at [wallClockMs]. Returns null if [id] has not been
     * uploaded yet. When [paused] is true, returns the frame previously selected (or frame 0
     * if none yet) so animation freezes on the pause screen.
     */
    fun currentFrameIdentifier(id: String, wallClockMs: Long, paused: Boolean): Identifier? {
        val state = images[id]
        if (state == null) {
            if (warnedMissing.add(id)) {
                DyeTrackerMod.warn("Render requested for image '{}' that is not uploaded yet", id)
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

    /** Source width/height in pixels (the rendered size scales by the widget/placement scale). */
    fun dimensions(id: String): Pair<Int, Int>? =
        images[id]?.let { it.width to it.height }

    /** Allocation-free width accessor for the hot render loop. Returns -1 if missing. */
    fun widthOf(id: String): Int = images[id]?.width ?: -1

    /** Allocation-free height accessor for the hot render loop. Returns -1 if missing. */
    fun heightOf(id: String): Int = images[id]?.height ?: -1

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
     * Sanitize an arbitrary id into a unique Identifier path. Mojang allows `[a-z0-9_./-]+`;
     * we use a stricter `[a-z0-9_]` subset (avoids `.` / `/` splitting the path into
     * segments) and append a stable hash suffix so distinct ids that sanitize to the same
     * prefix (e.g. `"abc-1"` vs `"abc_1"`) still produce distinct Identifiers — collisions
     * would otherwise cause one image's release to destroy another image's live textures.
     */
    private fun sanitize(id: String): String {
        val lower = id.lowercase()
        val cleaned = ID_SANITIZER_REGEX.replace(lower, "_").ifEmpty { "anon" }
        val suffix = id.hashCode().toUInt().toString(HEX_RADIX)
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

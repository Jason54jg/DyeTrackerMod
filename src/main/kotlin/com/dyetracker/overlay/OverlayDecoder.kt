package com.dyetracker.overlay

import com.dyetracker.DyeTrackerMod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.NodeList
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.FileImageInputStream

/**
 * Decodes a cached image file into per-frame [BufferedImage]s plus delays.
 *
 * GIFs return one [DecodedFrame] per frame with delays from the
 * `GraphicControlExtension`. Static formats (PNG, JPEG) collapse to a single frame
 * with effectively infinite delay. Multi-frame GIFs are composed onto a canvas so each
 * returned frame is the fully-rendered image at that moment (the JDK GIF reader emits
 * raw subframes — callers should not need to handle disposal themselves).
 */
object OverlayDecoder {

    private const val MAX_FRAMES = 500
    private const val MIN_DELAY_MS = 20
    private const val FLOORED_DELAY_MS = 100
    private const val GIF_FORMAT_NAME = "gif"
    private const val GIF_STREAM_METADATA_FORMAT = "javax_imageio_gif_stream_1.0"
    private const val GIF_IMAGE_METADATA_FORMAT = "javax_imageio_gif_image_1.0"
    private const val LOGICAL_SCREEN_DESCRIPTOR = "LogicalScreenDescriptor"
    private const val IMAGE_DESCRIPTOR = "ImageDescriptor"
    private const val GRAPHIC_CONTROL_EXTENSION = "GraphicControlExtension"
    private const val LOGICAL_SCREEN_WIDTH_ATTR = "logicalScreenWidth"
    private const val LOGICAL_SCREEN_HEIGHT_ATTR = "logicalScreenHeight"
    private const val IMAGE_LEFT_ATTR = "imageLeftPosition"
    private const val IMAGE_TOP_ATTR = "imageTopPosition"
    private const val DELAY_TIME_ATTR = "delayTime"
    private const val DISPOSAL_METHOD_ATTR = "disposalMethod"
    private const val DISPOSAL_RESTORE_TO_PREVIOUS = "restoreToPrevious"
    private const val DISPOSAL_RESTORE_TO_BACKGROUND = "restoreToBackgroundColor"
    private const val CENTISECONDS_TO_MS = 10

    /** A single decoded frame and its on-screen duration in ms. */
    data class DecodedFrame(val image: BufferedImage, val delayMs: Int)

    /** A fully decoded image: ordered frames plus total animation duration. */
    data class DecodedImage(
        val frames: List<DecodedFrame>,
        val totalDurationMs: Int,
        val width: Int,
        val height: Int,
    )

    /** Why a decode failed. */
    sealed class Reason {
        data class Corrupt(val cause: Throwable?) : Reason()
        data class Unsupported(val format: String) : Reason()
        data object TooManyFrames : Reason()
        data object Empty : Reason()
    }

    /** Throwable wrapper for a [Reason]. */
    class OverlayDecodeException(val reason: Reason) :
        RuntimeException(reasonMessage(reason))

    /**
     * Decode [path] into a [DecodedImage]. Runs on [Dispatchers.IO] — `ImageIO.read`
     * is blocking.
     */
    suspend fun decode(path: Path, contentType: String): Result<DecodedImage> =
        withContext(Dispatchers.IO) {
            try {
                val isGif = contentType.lowercase().contains(GIF_FORMAT_NAME)
                val result = if (isGif) decodeGif(path) else decodeStatic(path)
                DyeTrackerMod.info(
                    "Decoded {} frames ({} ms total) from {}",
                    result.frames.size, result.totalDurationMs, path.fileName
                )
                Result.success(result)
            } catch (e: OverlayDecodeException) {
                DyeTrackerMod.warn("Decode failed for {}: {}", path.fileName, e.message)
                Result.failure(e)
            } catch (e: Throwable) {
                DyeTrackerMod.warn("Decode unexpected error for {}: {}", path.fileName, e.message ?: "(no message)")
                Result.failure(OverlayDecodeException(Reason.Corrupt(e)))
            }
        }

    private fun decodeGif(path: Path): DecodedImage {
        if (!Files.exists(path) || Files.size(path) == 0L) {
            throw OverlayDecodeException(Reason.Empty)
        }
        val readers = ImageIO.getImageReadersByFormatName(GIF_FORMAT_NAME)
        if (!readers.hasNext()) throw OverlayDecodeException(Reason.Unsupported(GIF_FORMAT_NAME))
        val reader: ImageReader = readers.next()

        var inputStream: FileImageInputStream? = null
        try {
            inputStream = FileImageInputStream(path.toFile())
            reader.setInput(inputStream, false, false)

            val frameCount = try {
                reader.getNumImages(true)
            } catch (e: Exception) {
                throw OverlayDecodeException(Reason.Corrupt(e))
            }
            if (frameCount <= 0) throw OverlayDecodeException(Reason.Empty)
            if (frameCount > MAX_FRAMES) throw OverlayDecodeException(Reason.TooManyFrames)

            val (canvasW, canvasH) = readLogicalScreenSize(reader)

            val canvas = BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB)
            var previousCanvasSnapshot: BufferedImage? = null

            val frames = ArrayList<DecodedFrame>(frameCount)
            var totalMs = 0
            for (i in 0 until frameCount) {
                val sub = try {
                    reader.read(i)
                } catch (e: Exception) {
                    throw OverlayDecodeException(Reason.Corrupt(e))
                }

                val imgMeta = reader.getImageMetadata(i).getAsTree(GIF_IMAGE_METADATA_FORMAT) as IIOMetadataNode
                val (offsetX, offsetY) = readImageOffset(imgMeta)
                val gce = firstChild(imgMeta, GRAPHIC_CONTROL_EXTENSION)
                val delayCs = gce?.getAttribute(DELAY_TIME_ATTR)?.toIntOrNull() ?: 0
                val disposal = gce?.getAttribute(DISPOSAL_METHOD_ATTR) ?: ""

                if (disposal == DISPOSAL_RESTORE_TO_PREVIOUS) {
                    previousCanvasSnapshot = deepCopy(canvas)
                }

                val g: Graphics2D = canvas.createGraphics()
                g.drawImage(sub, offsetX, offsetY, null)
                g.dispose()

                val snapshot = deepCopy(canvas)
                val delayMs = clampDelay(delayCs * CENTISECONDS_TO_MS)
                frames.add(DecodedFrame(snapshot, delayMs))
                totalMs += delayMs

                when (disposal) {
                    DISPOSAL_RESTORE_TO_BACKGROUND -> clearRect(canvas, offsetX, offsetY, sub.width, sub.height)
                    DISPOSAL_RESTORE_TO_PREVIOUS -> previousCanvasSnapshot?.let { copyInto(it, canvas) }
                    // "none", "doNotDispose", unknown → leave canvas as-is
                }
            }

            return DecodedImage(frames, totalMs, canvasW, canvasH)
        } finally {
            reader.dispose()
            inputStream?.close()
        }
    }

    private fun decodeStatic(path: Path): DecodedImage {
        if (Files.exists(path) && Files.size(path) == 0L) {
            throw OverlayDecodeException(Reason.Empty)
        }
        val img: BufferedImage = try {
            ImageIO.read(path.toFile())
        } catch (e: Exception) {
            throw OverlayDecodeException(Reason.Corrupt(e))
        } ?: throw OverlayDecodeException(Reason.Corrupt(null))
        // Normalize to TYPE_INT_ARGB so callers can read pixels uniformly.
        val argb = if (img.type == BufferedImage.TYPE_INT_ARGB) img else deepCopy(img)
        return DecodedImage(
            frames = listOf(DecodedFrame(argb, Int.MAX_VALUE)),
            totalDurationMs = 0,
            width = argb.width,
            height = argb.height,
        )
    }

    private fun readLogicalScreenSize(reader: ImageReader): Pair<Int, Int> {
        val streamMeta = reader.streamMetadata?.getAsTree(GIF_STREAM_METADATA_FORMAT) as? IIOMetadataNode
            ?: return readFirstFrameSize(reader)
        val lsd = firstChild(streamMeta, LOGICAL_SCREEN_DESCRIPTOR) ?: return readFirstFrameSize(reader)
        val w = lsd.getAttribute(LOGICAL_SCREEN_WIDTH_ATTR).toIntOrNull() ?: 0
        val h = lsd.getAttribute(LOGICAL_SCREEN_HEIGHT_ATTR).toIntOrNull() ?: 0
        if (w <= 0 || h <= 0) return readFirstFrameSize(reader)
        return w to h
    }

    private fun readFirstFrameSize(reader: ImageReader): Pair<Int, Int> {
        val w = reader.getWidth(0).coerceAtLeast(1)
        val h = reader.getHeight(0).coerceAtLeast(1)
        return w to h
    }

    private fun readImageOffset(imgMeta: IIOMetadataNode): Pair<Int, Int> {
        val desc = firstChild(imgMeta, IMAGE_DESCRIPTOR) ?: return 0 to 0
        val x = desc.getAttribute(IMAGE_LEFT_ATTR).toIntOrNull() ?: 0
        val y = desc.getAttribute(IMAGE_TOP_ATTR).toIntOrNull() ?: 0
        return x to y
    }

    private fun firstChild(node: IIOMetadataNode, tag: String): IIOMetadataNode? {
        val list: NodeList = node.getElementsByTagName(tag)
        return list.item(0) as? IIOMetadataNode
    }

    private fun clampDelay(delayMs: Int): Int =
        if (delayMs < MIN_DELAY_MS) FLOORED_DELAY_MS else delayMs

    private fun deepCopy(src: BufferedImage): BufferedImage {
        val copy = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        val g = copy.createGraphics()
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return copy
    }

    private fun copyInto(src: BufferedImage, dst: BufferedImage) {
        val g = dst.createGraphics()
        g.composite = java.awt.AlphaComposite.Src // overwrite, including alpha
        g.drawImage(src, 0, 0, null)
        g.dispose()
    }

    private fun clearRect(canvas: BufferedImage, x: Int, y: Int, w: Int, h: Int) {
        val g = canvas.createGraphics()
        g.composite = java.awt.AlphaComposite.Clear
        g.fillRect(x, y, w, h)
        g.dispose()
    }
}

private fun reasonMessage(reason: OverlayDecoder.Reason): String = when (reason) {
    is OverlayDecoder.Reason.Corrupt -> "corrupt image: ${reason.cause?.javaClass?.simpleName ?: "unknown"}: ${reason.cause?.message ?: "(no detail)"}"
    is OverlayDecoder.Reason.Unsupported -> "unsupported format: ${reason.format}"
    OverlayDecoder.Reason.TooManyFrames -> "image has more than 500 frames"
    OverlayDecoder.Reason.Empty -> "image is empty or has no frames"
}

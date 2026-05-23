package com.dyetracker.ui.texture

import java.awt.image.BufferedImage

/** A single decoded frame and how long it displays, in milliseconds. */
data class ImageFrame(val image: BufferedImage, val delayMs: Int)

/**
 * Decoded image frames plus intrinsic source dimensions, ready for GPU upload by
 * [ImageTextureManager]. A static image is a single frame; an animated image is N frames
 * with per-frame delays.
 *
 * This is the feature-agnostic input the texture manager consumes, so any caller (the GIF
 * decoder, bundled sprite PNGs, future widgets) can upload images without the toolkit
 * depending on a specific decoder.
 */
data class ImageFrames(
    val frames: List<ImageFrame>,
    val width: Int,
    val height: Int,
)

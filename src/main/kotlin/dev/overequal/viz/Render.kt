package dev.overequal.viz

import org.jetbrains.kotlinx.kandy.ir.Plot
import org.jetbrains.kotlinx.kandy.letsplot.export.toBufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Rasterize a Kandy [Plot] to PNG bytes in-memory (via Lets-Plot's
 * `toBufferedImage`), so charts can be uploaded straight to Discord without
 * touching the filesystem. [scale] oversamples for crisp text.
 */
fun Plot.toPngBytes(
    scale: Number = 2,
    dpi: Number? = null,
): ByteArray {
    val image = toBufferedImage(scale, dpi)
    val out = ByteArrayOutputStream()
    ImageIO.write(image, "png", out)
    return out.toByteArray()
}

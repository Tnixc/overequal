package dev.overequal

import dev.overequal.viz.ChartStyle.flexoki
import dev.overequal.viz.Theme
import dev.overequal.viz.toPngBytes
import org.jetbrains.kotlinx.kandy.dsl.categorical
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.barsH
import java.io.File

/**
 * TEMPORARY smoke test: render one Flexoki horizontal bar chart headlessly and
 * write it to out/smoke.png to validate the Kandy -> Lets-Plot PNG pipeline
 * before porting the real visualizations. Replaced by the CLI/bot dispatcher.
 */
fun main() {
    System.setProperty("java.awt.headless", "true")

    val names = listOf("delta", "charlie", "bravo", "alpha")
    val values = listOf(120, 240, 360, 500)
    val colors = Theme.gradient(Theme.BLUE, names.size)

    val chart =
        plot {
            barsH {
                y(names)
                x(values) { axis.name = "Messages" }
                fillColor(names) {
                    scale = categorical(*names.zip(colors).toTypedArray())
                }
            }
            layout {
                title = "Smoke test — Flexoki horizontal bars"
                subtitle = "validating headless PNG export"
                size = 900 to 600
                flexoki()
            }
        }

    val bytes = chart.toPngBytes()
    File("out").mkdirs()
    File("out/smoke.png").writeBytes(bytes)
    println("wrote out/smoke.png (${bytes.size} bytes)")
}

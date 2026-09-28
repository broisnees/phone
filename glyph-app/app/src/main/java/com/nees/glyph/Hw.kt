package com.nees.glyph

import android.util.Log
import java.io.OutputStreamWriter
import java.util.concurrent.Executors

/**
 * The Phone (1) Glyph LEDs: 15 channels in Glyph Developer Kit order, mapped to the aw210xx
 * driver's hardware LED numbers (see drivers/leds/leds_aw210xx.c, frame_leds_effect).
 */
enum class Zone(val label: String, val hw: Int) {
    A("Camera", 7),
    B("Diagonal", 1),
    C1("Ring 1", 2), C2("Ring 2", 3), C3("Ring 3", 4), C4("Ring 4", 5),
    E("Dot", 16),
    D1("Strip 1", 13), D2("Strip 2", 11), D3("Strip 3", 9), D4("Strip 4", 12),
    D5("Strip 5", 10), D6("Strip 6", 14), D7("Strip 7", 15), D8("Strip 8", 8);

    companion object {
        val ring = listOf(C1, C2, C3, C4)
        /** Bottom → top. */
        val strip = listOf(D1, D2, D3, D4, D5, D6, D7, D8)
        const val COUNT = 15
    }
}

/**
 * Writes LED brightness through one long-lived root shell. Only channels whose value changed
 * are written, so a static frame costs nothing and a visualizer frame touches a handful of LEDs.
 */
object Hw {
    private const val NODE = "/sys/class/leds/aw210xx_led/single_led_br"
    const val MAX = 4095

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "glyph-hw").apply { isDaemon = true } }
    private var shell: Process? = null
    private var writer: OutputStreamWriter? = null
    private val last = IntArray(Zone.COUNT) { -1 }

    @Volatile var available = true; private set

    /** [levels] are 0..4095 per [Zone] ordinal. */
    fun write(levels: IntArray) {
        val frame = levels.copyOf()
        io.execute {
            val sb = StringBuilder()
            for (i in 0 until Zone.COUNT) {
                val v = frame[i].coerceIn(0, MAX)
                if (v != last[i]) {
                    sb.append("echo '").append(Zone.entries[i].hw).append(' ').append(v).append("' > ").append(NODE).append('\n')
                    last[i] = v
                }
            }
            if (sb.isNotEmpty()) send(sb.toString())
        }
    }


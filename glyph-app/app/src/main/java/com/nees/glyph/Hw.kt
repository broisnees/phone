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

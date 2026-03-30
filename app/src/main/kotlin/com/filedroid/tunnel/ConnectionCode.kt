package com.filedroid.tunnel

import kotlin.random.Random

/**
 * Generates human-friendly connection codes for relay tunnels.
 *
 * Format: 4 random words joined by hyphens, e.g. "ocean-blue-river-sun"
 * The code is the tunnel ID — both host and client use the same code
 * to connect through the relay server.
 */
object ConnectionCode {

    private val WORDS = arrayOf(
        // 4-7 letter common English words, easy to read and type
        "apple", "ocean", "river", "stone", "flame", "cloud", "maple", "tiger",
        "candy", "mango", "lemon", "berry", "peach", "grape", "melon", "olive",
        "beach", "cliff", "field", "forest", "garden", "harbor", "island", "jungle",
        "meadow", "palace", "castle", "valley", "bridge", "tower", "prairie", "desert",
        "arctic", "breeze", "candle", "dagger", "eagle", "falcon", "glacier", "hammer",
        "iron", "jewel", "knight", "lantern", "marble", "nebula", "orchid", "phoenix",
        "quartz", "rocket", "silver", "thunder", "umbra", "vector", "willow", "xenon",
        "yacht", "amber", "blaze", "cedar", "delta", "ember", "frost", "glyph",
        "haven", "ivory", "jade", "karma", "lotus", "mirage", "nexus", "orbit",
        "pixel", "quest", "ridge", "solar", "token", "unity", "vivid", "warp",
        "azure", "brass", "crown", "drift", "ether", "forge", "glyph", "haze",
        "iris", "joker", "kite", "lunar", "mystic", "nova", "orbit", "prism",
        "quail", "radar", "sage", "tulip", "ultra", "vapor", "wren", "zephyr",
        "alpha", "bravo", "coral", "delta", "echo", "freta", "gamma", "hydra",
        "indigo", "joker", "kilo", "lima", "mocha", "ninja", "omega", "pulse",
        "queen", "robin", "sigma", "tango", "ultra", "victor", "whiskey", "xray"
    )

    private val WORD_LIST = WORDS.distinct()

    /** Generate a 4-word connection code like "ocean-blue-river-sun". */
    fun generate(): String {
        val indices = List(4) { Random.nextInt(WORD_LIST.size) }
        return indices.joinToString("-") { WORD_LIST[it] }
    }

    /** Validate a connection code format (4 words separated by hyphens). */
    fun isValid(code: String): Boolean {
        val parts = code.split("-")
        if (parts.size != 4) return false
        return parts.all { it.isNotBlank() && it.length in 2..12 }
    }

    /**
     * Convert a code into a relay-compatible tunnel ID.
     * The code IS the tunnel ID — no transformation needed.
     */
    fun toTunnelId(code: String): String = code.lowercase().trim()
}

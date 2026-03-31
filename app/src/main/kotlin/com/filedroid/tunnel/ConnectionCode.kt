package com.filedroid.tunnel

import kotlin.random.Random

/**
 * Generates human-friendly connection codes for relay tunnels.
 *
 * Format: 5 random words joined by hyphens, e.g. "ocean-phoenix-ivory-nexus-drift"
 * The code is the tunnel ID — both host and client use the same code
 * to connect through the relay server.
 */
object ConnectionCode {

    private val WORDS = listOf(
        // Distinctive 3-8 letter words, chosen to be easy to read/recognize
        // but uncommon enough to resist casual guessing
        "adobe", "alpine", "amber", "anvil", "apex", "arcane", "arctic", "atlas",
        "azure", "bamboo", "basalt", "beacon", "blaze", "bonfire", "boulder", "braze",
        "cedar", "chisel", "cipher", "cobalt", "coral", "crater", "crown", "crystal",
        "dagger", "delta", "dune", "eclipse", "ember", "emerald", "enigma", "epoch",
        "ether", "exile", "falcon", "fenix", "fjord", "flint", "forge", "fossil",
        "frost", "glacier", "glyph", "granite", "gravar", "havoc", "helix", "horizon",
        "hypsum", "ignite", "indigo", "iron", "ivory", "jade", "jasper", "jigsaw",
        "juniper", "karma", "knight", "lattice", "lava", "lunar", "magnet", "marble",
        "meadow", "mercury", "mirage", "monolith", "myrtle", "nebula", "nickel", "nimbus",
        "nova", "obsidian", "octave", "olive", "onyx", "opal", "orbital", "osprey",
        "phantom", "phoenix", "pillar", "plasma", "prism", "prism", "pyrite", "quartz",
        "quasar", "radar", "ravine", "ridge", "ripple", "rune", "sable", "sapphire",
        "shale", "sierra", "silver", "slate", "solstice", "sparrow", "spire", "summit",
        "tarnish", "topaz", "tundra", "umbra", "utopia", "valor", "vapor", "vertex",
        "vortex", "whisper", "wren", "xerces", "xylon", "yarrow", "zephyr", "zircon"
    ).distinct()

    /** Generate a 5-word connection code like "ocean-phoenix-ivory-nexus-drift". */
    fun generate(): String {
        val indices = List(5) { Random.nextInt(WORD_LIST.size) }
        return indices.joinToString("-") { WORD_LIST[it] }
    }

    /** Validate a connection code format (5 words separated by hyphens). */
    fun isValid(code: String): Boolean {
        val parts = code.split("-")
        if (parts.size != 5) return false
        return parts.all { it.isNotBlank() && it.length in 2..12 }
    }

    /**
     * Convert a code into a relay-compatible tunnel ID.
     * The code IS the tunnel ID — no transformation needed.
     */
    fun toTunnelId(code: String): String = code.lowercase().trim()

    private val WORD_LIST = WORDS
}

package com.filedroid

// Feature: filedroid, Property 5: Port validation accepts exactly the valid range

import com.filedroid.ui.settings.isValidPort
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class PortValidationPropertyTest : FunSpec({

    test("Property 5: isValidPort returns true iff integer is in [1, 65535]") {
        checkAll(iterations = 1000, Arb.int()) { n ->
            isValidPort(n.toString()) shouldBe (n in 1..65535)
        }
    }

    test("Property 5: isValidPort returns false for non-numeric strings") {
        checkAll(iterations = 500, Arb.string()) { s ->
            val isNumericInRange = s.toIntOrNull()?.let { it in 1..65535 } ?: false
            if (!isNumericInRange) {
                val expected = s.toIntOrNull()?.let { it in 1..65535 } ?: false
                isValidPort(s) shouldBe expected
            }
        }
    }
})

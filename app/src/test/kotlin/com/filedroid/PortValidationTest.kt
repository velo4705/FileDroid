package com.filedroid

import com.filedroid.ui.settings.isValidPort
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PortValidationTest : FunSpec({

    test("1 is valid (lower boundary)") {
        isValidPort("1") shouldBe true
    }

    test("65535 is valid (upper boundary)") {
        isValidPort("65535") shouldBe true
    }

    test("65536 is invalid (above range)") {
        isValidPort("65536") shouldBe false
    }

    test("non-numeric string 'abc' is invalid") {
        isValidPort("abc") shouldBe false
    }

    test("empty string is invalid") {
        isValidPort("") shouldBe false
    }

    test("typical FTP port 2121 is valid") {
        isValidPort("2121") shouldBe true
    }

    test("typical SFTP port 2222 is valid") {
        isValidPort("2222") shouldBe true
    }

    test("0 is invalid") {
        isValidPort("0") shouldBe false
    }

    test("1 is valid (lower boundary)") {
        isValidPort("1") shouldBe true
    }

    test("1023 is valid") {
        isValidPort("1023") shouldBe true
    }

    test("21 is valid (standard FTP port)") {
        isValidPort("21") shouldBe true
    }

    test("22 is valid (standard SFTP port)") {
        isValidPort("22") shouldBe true
    }

    test("negative number is invalid") {
        isValidPort("-1") shouldBe false
    }
})

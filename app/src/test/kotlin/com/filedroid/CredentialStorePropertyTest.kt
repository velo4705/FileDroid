package com.filedroid

// Feature: filedroid, Property 1: No plaintext credentials in backing store

import com.filedroid.security.CredentialKeys
import com.filedroid.security.CredentialStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property 1: For any string written to CredentialStore, getString returns the exact same value.
 * Validates: Requirements 3.4, 3.10, 8.3
 *
 * Note: The "no plaintext in backing store" aspect of Property 1 requires an instrumented test
 * with real EncryptedSharedPreferences (deferred to Milestone 6). This test verifies the
 * round-trip contract of the CredentialStore interface using an in-memory fake.
 */
class CredentialStorePropertyTest : FunSpec({

    fun fakeStore(): CredentialStore {
        val map = mutableMapOf<String, String>()
        return object : CredentialStore {
            override fun putString(key: String, value: String) { map[key] = value }
            override fun getString(key: String): String? = map[key]
            override fun remove(key: String) { map.remove(key) }
            override fun hasServerPassword(): Boolean =
                map[CredentialKeys.SERVER_PASSWORD]?.isNotEmpty() == true
        }
    }

    test("Property 1: for any string value, getString returns the exact written value") {
        val store = fakeStore()
        checkAll(iterations = 500, Arb.string()) { value ->
            store.putString("prop_test_key", value)
            store.getString("prop_test_key") shouldBe value
        }
    }

    test("Property 1: hasServerPassword reflects whether a non-empty password is stored") {
        val store = fakeStore()
        checkAll(iterations = 200, Arb.string(minSize = 1)) { password ->
            store.putString(CredentialKeys.SERVER_PASSWORD, password)
            store.hasServerPassword() shouldBe true
            store.remove(CredentialKeys.SERVER_PASSWORD)
            store.hasServerPassword() shouldBe false
        }
    }
})

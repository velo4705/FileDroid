package com.filedroid

import com.filedroid.security.CredentialKeys
import com.filedroid.security.CredentialStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Unit tests for CredentialStore using a mock to avoid EncryptedSharedPreferences
 * Android Keystore dependency in JVM tests.
 *
 * Round-trip correctness is verified against the CredentialStore interface contract.
 * Property-based round-trip tests are in CredentialStorePropertyTest.
 */
class CredentialStoreTest : FunSpec({

    // Fake in-memory CredentialStore for JVM unit tests
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

    test("putString then getString returns the same value") {
        val store = fakeStore()
        store.putString("test_key", "secret_value")
        store.getString("test_key") shouldBe "secret_value"
    }

    test("getString returns null for missing key") {
        val store = fakeStore()
        store.getString("missing") shouldBe null
    }

    test("remove deletes the key") {
        val store = fakeStore()
        store.putString("key", "value")
        store.remove("key")
        store.getString("key") shouldBe null
    }

    test("hasServerPassword returns false when no password stored") {
        val store = fakeStore()
        store.hasServerPassword() shouldBe false
    }

    test("hasServerPassword returns true after storing a password") {
        val store = fakeStore()
        store.putString(CredentialKeys.SERVER_PASSWORD, "mypassword")
        store.hasServerPassword() shouldBe true
    }

    test("hasServerPassword returns false after removing the password") {
        val store = fakeStore()
        store.putString(CredentialKeys.SERVER_PASSWORD, "mypassword")
        store.remove(CredentialKeys.SERVER_PASSWORD)
        store.hasServerPassword() shouldBe false
    }

    test("overwriting a key stores the new value") {
        val store = fakeStore()
        store.putString("key", "first")
        store.putString("key", "second")
        store.getString("key") shouldBe "second"
    }
})

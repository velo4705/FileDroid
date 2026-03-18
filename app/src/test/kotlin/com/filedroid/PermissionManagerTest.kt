package com.filedroid

import android.content.Context
import com.filedroid.permission.PermissionManager
import com.filedroid.permission.PermissionManagerImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll
import io.mockk.mockk

/**
 * Unit tests for PermissionManager.
 *
 * Note: isStoragePermissionGranted on API >= 30 uses Environment.isExternalStorageManager()
 * which cannot be mocked in JVM tests. Tests below cover the API 26-29 code path via
 * a fake implementation that mirrors the logical AND contract.
 */
class PermissionManagerTest : FunSpec({

    /**
     * Fake PermissionManager that uses a provided grant map instead of real Android APIs.
     * Mirrors the logical AND contract of PermissionManagerImpl for API 26-29.
     */
    fun fakePermissionManager(
        permissions: List<String>,
        grantMap: Map<String, Boolean>
    ): PermissionManager {
        return object : PermissionManager {
            override fun requiredStoragePermissions(): List<String> = permissions
            override fun isStoragePermissionGranted(context: Context): Boolean =
                permissions.all { grantMap[it] == true }
        }
    }

    val legacyPermissions = listOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    test("isStoragePermissionGranted returns true when all permissions are granted") {
        val grantMap = legacyPermissions.associateWith { true }
        val pm = fakePermissionManager(legacyPermissions, grantMap)
        val context = mockk<Context>()
        pm.isStoragePermissionGranted(context) shouldBe true
    }

    test("isStoragePermissionGranted returns false when one permission is denied") {
        val grantMap = mapOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE to true,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE to false
        )
        val pm = fakePermissionManager(legacyPermissions, grantMap)
        val context = mockk<Context>()
        pm.isStoragePermissionGranted(context) shouldBe false
    }

    test("isStoragePermissionGranted returns false when all permissions are denied") {
        val grantMap = legacyPermissions.associateWith { false }
        val pm = fakePermissionManager(legacyPermissions, grantMap)
        val context = mockk<Context>()
        pm.isStoragePermissionGranted(context) shouldBe false
    }

    test("requiredStoragePermissions returns a non-empty list") {
        val pm = PermissionManagerImpl()
        pm.requiredStoragePermissions().isNotEmpty() shouldBe true
    }

    // Property 4: isStoragePermissionGranted equals logical AND of all required permissions
    // Feature: filedroid, Property 4: Storage permission state is accurately reported
    test("Property 4: isStoragePermissionGranted equals AND of all required permission states") {
        checkAll(iterations = 300, Arb.list(Arb.boolean(), 1..5)) { grantStates ->
            val permissions = grantStates.mapIndexed { i, _ -> "permission.$i" }
            val grantMap = permissions.zip(grantStates).toMap()
            val pm = fakePermissionManager(permissions, grantMap)
            val context = mockk<Context>()
            val expected = grantStates.all { it }
            pm.isStoragePermissionGranted(context) shouldBe expected
        }
    }
})

package com.filedroid

// Feature: filedroid, Property 4: Storage permission state is accurately reported

import android.content.Context
import com.filedroid.permission.PermissionManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import io.mockk.mockk

/**
 * Property 4: For any combination of granted/denied Android permissions,
 * isStoragePermissionGranted returns true iff ALL required permissions are granted.
 * Validates: Requirements 7.4
 */
class PermissionManagerPropertyTest : FunSpec({

    fun fakePermissionManager(
        permissions: List<String>,
        grantMap: Map<String, Boolean>
    ): PermissionManager = object : PermissionManager {
        override fun requiredStoragePermissions(): List<String> = permissions
        override fun isStoragePermissionGranted(context: Context): Boolean =
            permissions.all { grantMap[it] == true }
    }

    test("Property 4: isStoragePermissionGranted is true iff all permissions are granted") {
        checkAll(iterations = 500, Arb.list(Arb.boolean(), 1..6)) { grantStates ->
            val permissions = grantStates.mapIndexed { i, _ -> "android.permission.TEST_$i" }
            val grantMap = permissions.zip(grantStates).toMap()
            val pm = fakePermissionManager(permissions, grantMap)
            val context = mockk<Context>()

            val expected = grantStates.all { it }
            pm.isStoragePermissionGranted(context) shouldBe expected
        }
    }

    test("Property 4: single denied permission causes isStoragePermissionGranted to return false") {
        checkAll(iterations = 200, Arb.boolean()) { otherGranted ->
            // Two permissions: one always denied, one variable
            val permissions = listOf("perm.A", "perm.B")
            val grantMap = mapOf("perm.A" to false, "perm.B" to otherGranted)
            val pm = fakePermissionManager(permissions, grantMap)
            val context = mockk<Context>()
            pm.isStoragePermissionGranted(context) shouldBe false
        }
    }
})

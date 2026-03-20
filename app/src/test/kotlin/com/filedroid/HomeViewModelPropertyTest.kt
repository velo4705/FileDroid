package com.filedroid

// Feature: filedroid, Property 2: Server start blocked without credentials

import android.content.Context
import com.filedroid.permission.PermissionManager
import com.filedroid.security.CredentialStore
import com.filedroid.ui.home.HomeViewModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk

/**
 * Property 2: For any HomeUiState where hasServerPassword is false,
 * canStartServer must be false regardless of other state fields.
 * Validates: Requirements 8.4, 8.5
 */
class HomeViewModelPropertyTest : FunSpec({

    fun buildViewModel(hasPassword: Boolean, hasPermission: Boolean): HomeViewModel {
        val store = mockk<CredentialStore>()
        every { store.hasServerPassword() } returns hasPassword

        val permManager = mockk<PermissionManager>()
        val context = mockk<Context>(relaxed = true)
        every { permManager.isStoragePermissionGranted(context) } returns hasPermission

        val cm = mockk<android.net.ConnectivityManager>(relaxed = true)
        every { context.getSystemService(android.net.ConnectivityManager::class.java) } returns cm

        return HomeViewModel(store, permManager, context)
    }

    test("Property 2: canStartServer is always false when hasServerPassword is false") {
        checkAll(iterations = 200, Arb.boolean()) { storageGranted ->
            val vm = buildViewModel(hasPassword = false, hasPermission = storageGranted)
            vm.uiState.value.canStartServer shouldBe false
        }
    }

    test("Property 2: canStartServer equals (hasPassword AND hasPermission) for all combinations") {
        checkAll(iterations = 200, Arb.boolean(), Arb.boolean()) { hasPassword, hasPermission ->
            val vm = buildViewModel(hasPassword = hasPassword, hasPermission = hasPermission)
            vm.uiState.value.canStartServer shouldBe (hasPassword && hasPermission)
        }
    }
})

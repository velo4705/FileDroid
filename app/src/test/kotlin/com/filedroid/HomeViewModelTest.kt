package com.filedroid

import android.content.Context
import com.filedroid.permission.PermissionManager
import com.filedroid.security.CredentialKeys
import com.filedroid.security.CredentialStore
import com.filedroid.ui.home.HomeViewModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class HomeViewModelTest : FunSpec({

    fun buildViewModel(
        hasPassword: Boolean,
        hasPermission: Boolean
    ): HomeViewModel {
        val store = mockk<CredentialStore>()
        every { store.hasServerPassword() } returns hasPassword

        val permManager = mockk<PermissionManager>()
        val context = mockk<Context>()
        every { permManager.isStoragePermissionGranted(context) } returns hasPermission

        return HomeViewModel(store, permManager, context)
    }

    test("canStartServer is false when hasServerPassword is false") {
        val vm = buildViewModel(hasPassword = false, hasPermission = true)
        vm.uiState.value.canStartServer shouldBe false
    }

    test("canStartServer is false when storagePermissionGranted is false") {
        val vm = buildViewModel(hasPassword = true, hasPermission = false)
        vm.uiState.value.canStartServer shouldBe false
    }

    test("canStartServer is false when both hasServerPassword and storagePermissionGranted are false") {
        val vm = buildViewModel(hasPassword = false, hasPermission = false)
        vm.uiState.value.canStartServer shouldBe false
    }

    test("canStartServer is true when both hasServerPassword and storagePermissionGranted are true") {
        val vm = buildViewModel(hasPassword = true, hasPermission = true)
        vm.uiState.value.canStartServer shouldBe true
    }

    test("hasServerPassword reflects credential store state") {
        val vm = buildViewModel(hasPassword = false, hasPermission = false)
        vm.uiState.value.hasServerPassword shouldBe false
    }

    test("storagePermissionGranted reflects permission manager state") {
        val vm = buildViewModel(hasPassword = true, hasPermission = false)
        vm.uiState.value.storagePermissionGranted shouldBe false
    }
})

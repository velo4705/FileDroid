package com.filedroid

import com.filedroid.permission.PermissionManager
import com.filedroid.security.CredentialStore
import com.filedroid.ui.settings.SettingsViewModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.mockk.mockk

class SettingsViewModelTest : FunSpec({

    fun buildViewModel(): SettingsViewModel {
        val store = mockk<CredentialStore>(relaxed = true)
        val permManager = mockk<PermissionManager>(relaxed = true)
        return SettingsViewModel(store, permManager)
    }

    // --- FTP port ---

    test("entering invalid FTP port '999' sets ftpPortError") {
        val vm = buildViewModel()
        vm.updateFtpPort("999")
        vm.uiState.value.ftpPortError shouldNotBe null
    }

    test("entering valid FTP port '8080' clears ftpPortError") {
        val vm = buildViewModel()
        vm.updateFtpPort("999")   // set error first
        vm.updateFtpPort("8080")  // then clear it
        vm.uiState.value.ftpPortError shouldBe null
    }

    test("entering boundary FTP port '1024' clears ftpPortError") {
        val vm = buildViewModel()
        vm.updateFtpPort("1024")
        vm.uiState.value.ftpPortError shouldBe null
    }

    test("entering boundary FTP port '65535' clears ftpPortError") {
        val vm = buildViewModel()
        vm.updateFtpPort("65535")
        vm.uiState.value.ftpPortError shouldBe null
    }

    test("entering FTP port '65536' sets ftpPortError") {
        val vm = buildViewModel()
        vm.updateFtpPort("65536")
        vm.uiState.value.ftpPortError shouldNotBe null
    }

    test("entering non-numeric FTP port 'abc' sets ftpPortError") {
        val vm = buildViewModel()
        vm.updateFtpPort("abc")
        vm.uiState.value.ftpPortError shouldNotBe null
    }

    // --- SFTP port ---

    test("entering invalid SFTP port '999' sets sftpPortError") {
        val vm = buildViewModel()
        vm.updateSftpPort("999")
        vm.uiState.value.sftpPortError shouldNotBe null
    }

    test("entering valid SFTP port '2222' clears sftpPortError") {
        val vm = buildViewModel()
        vm.updateSftpPort("999")
        vm.updateSftpPort("2222")
        vm.uiState.value.sftpPortError shouldBe null
    }

    // --- Property-based: error state matches validation ---

    test("ftpPortError is null iff port is in valid range (property)") {
        // Feature: filedroid, Property 5 (SettingsViewModel): Port error state matches validation
        val vm = buildViewModel()
        checkAll(iterations = 500, Arb.int(-100, 70000)) { n ->
            vm.updateFtpPort(n.toString())
            val expectedError = if (n in 1024..65535) null else "non-null"
            if (n in 1024..65535) {
                vm.uiState.value.ftpPortError shouldBe null
            } else {
                vm.uiState.value.ftpPortError shouldNotBe null
            }
        }
    }
})

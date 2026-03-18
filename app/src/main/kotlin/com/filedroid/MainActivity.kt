package com.filedroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.filedroid.security.CredentialKeys
import com.filedroid.security.CredentialStore
import com.filedroid.ui.navigation.FileDroidApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var credentialStore: CredentialStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // R7.3 — prompt to set server password on first launch
            var showPasswordSetup by remember {
                mutableStateOf(!credentialStore.hasServerPassword())
            }

            if (showPasswordSetup) {
                FirstLaunchPasswordDialog(
                    onSet = { password ->
                        credentialStore.putString(CredentialKeys.SERVER_PASSWORD, password)
                        showPasswordSetup = false
                    },
                    onSkip = { showPasswordSetup = false }
                )
            } else {
                FileDroidApp()
            }
        }
    }
}

@Composable
private fun FirstLaunchPasswordDialog(
    onSet: (String) -> Unit,
    onSkip: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && password != confirm

    AlertDialog(
        onDismissRequest = {},  // not dismissible — must choose
        title = { Text("Set Server Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Set a password for the FTP/SFTP server before anyone can connect to your device.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm password") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = mismatch,
                    supportingText = if (mismatch) ({ Text("Passwords don't match") }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSet(password) },
                enabled = password.isNotBlank() && password == confirm
            ) { Text("Set Password") }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Skip") }
        }
    )
}

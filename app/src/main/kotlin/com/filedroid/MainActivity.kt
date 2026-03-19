package com.filedroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.filedroid.security.CredentialKeys
import com.filedroid.security.CredentialStore
import com.filedroid.ui.navigation.FileDroidApp
import com.filedroid.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var credentialStore: CredentialStore

    // Single activity-scoped ThemeViewModel — shared across all screens
    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var showPasswordSetup by remember {
                mutableStateOf(!credentialStore.hasServerPassword())
            }

            if (showPasswordSetup) {
                FirstLaunchPasswordDialog(
                    onSet = { username, password ->
                        credentialStore.putString("server_username", username)
                        credentialStore.putString(CredentialKeys.SERVER_PASSWORD, password)
                        showPasswordSetup = false
                    },
                    onSkip = { showPasswordSetup = false }
                )
            } else {
                FileDroidApp(themeViewModel = themeViewModel)
            }
        }
    }
}

@Composable
private fun FirstLaunchPasswordDialog(
    onSet: (String, String) -> Unit,
    onSkip: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && password != confirm

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Set Server Credentials") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Set credentials for the FTP/SFTP server so other devices can connect to yours.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                onClick = { onSet(username, password) },
                enabled = username.isNotBlank() && password.isNotBlank() && password == confirm
            ) { Text("Set Credentials") }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Skip") }
        }
    )
}

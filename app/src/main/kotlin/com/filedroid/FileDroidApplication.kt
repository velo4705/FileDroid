package com.filedroid

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

@HiltAndroidApp
class FileDroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Register BC so SSHJ can use X25519 and other modern algorithms
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }
}

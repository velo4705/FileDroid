package com.filedroid

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

@HiltAndroidApp
class FileDroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Register full BC so SSHJ can use X25519 and other modern algorithms.
        // Android ships a stripped BC — insert ours at position 1 to take priority.
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}

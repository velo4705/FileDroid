plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.filedroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.filedroid"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("KEYSTORE_BASE64")?.isNotBlank() == true) {
                signingConfigs.create("release") {
                    val keystoreFile = rootProject.file("release.keystore")
                    storeFile = keystoreFile
                    storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                    keyAlias = System.getenv("KEY_ALIAS") ?: ""
                    keyPassword = System.getenv("KEY_PASSWORD") ?: ""
                }
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
            // Merge service loader files from all JARs (required for Apache MINA SSHD)
            merges += "META-INF/services/**"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Activity + Lifecycle
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Security
    implementation(libs.security.crypto)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Networking — FTP + SFTP client
    implementation(libs.commons.net)
    implementation(libs.sshj)
    implementation(libs.bouncycastle)

    // Server — Apache MINA SSHD + Apache FTP Server
    implementation(libs.mina.sshd.core)
    implementation(libs.mina.sshd.sftp)
    implementation(libs.ftpserver.core)

    // Document file (folder picker support)
    implementation(libs.documentfile)

    // WebSocket client (for relay tunnel)
    implementation(libs.okhttp)

    // Unit tests
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.property)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}

// Use JUnit Platform (required for Kotest)
tasks.withType<Test> {
    useJUnitPlatform()
}

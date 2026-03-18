# FileDroid

A dual-panel file manager for Android, built for FTP, SFTP, and FTPS — like FileZilla, but on your phone.

## What it does

- **Local panel** — browse and manage files on your Android device, including Termux storage
- **Remote panel** — connect to any FTP/SFTP/FTPS server and browse its files
- **Transfer** — upload and download files between your device and a remote server
- **Server mode** — run an FTP/SFTP server on your phone so FileZilla and other clients can connect in
- **SSH Manager** — open terminal sessions to remote hosts (PCs, servers, other phones)

## Tech Stack

- Kotlin + Jetpack Compose
- Hilt (dependency injection)
- Apache MINA SSHD (SFTP server + SSH client)
- Apache FTP Server (FTP server)
- Room (connection profile storage)
- EncryptedSharedPreferences + Android Keystore (credential storage)

## Building

This project builds via GitHub Actions. Every push to `main` produces a debug APK.

1. Push to `main`
2. Go to [Actions](../../actions) → latest run → download `filedroid-debug` artifact
3. Sideload to your device:
   ```bash
   adb install -r app-debug.apk
   ```

## Milestones

| # | Scope | Status |
|---|---|---|
| M1 | Project foundation — scaf
folding, DI, nav, CI | ✅ Done |
| M2 | Local file browser, file ops, Termux bookmark | Up next |
| M3 | FTP/SFTP client, connection profiles, remote browser | |
| M4 | File transfer engine, progress, queue, cancel | |
| M5 | FTP/SFTP server (inbound), background service | |
| M6 | SSH Manager, terminal UI | |
| M7 | Polish, FTPS, release signing, QA | |

## Minimum Requirements

- Android 8.0 (API 26) or higher
- Wi-Fi or Ethernet connection for server/client features
 
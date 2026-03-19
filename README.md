# FileDroid

A dual-panel file manager for Android with built-in FTP, SFTP, and FTPS support — like FileZilla, but on your phone.

[![Build](https://github.com/velo4705/FileDroid/actions/workflows/build.yml/badge.svg)](https://github.com/velo4705/FileDroid/actions/workflows/build.yml)

## Features

- **Local browser** — browse, copy, move, rename, and delete files on your device, including Termux storage
- **Remote browser** — connect to any FTP, FTPS, or SFTP server and manage its files
- **Private key auth** — authenticate to SFTP servers using PEM or OpenSSH private keys
- **Transfer queue** — upload and download files with live progress, pause, and cancel
- **Server mode** — run an FTP or SFTP server on your phone so desktop clients (FileZilla, WinSCP, etc.) can connect in
- **Interface binding** — choose which network interface the server listens on
- **SSH terminal** — open interactive terminal sessions to remote hosts with multi-tab support and ANSI color rendering
- **Secure storage** — credentials stored in EncryptedSharedPreferences backed by Android Keystore

## Requirements

- Android 8.0 (API 26) or higher
- Wi-Fi or local network connection for server and client features

## Installation

Builds are produced automatically by GitHub Actions on every push to `main`.

1. Go to [Actions](../../actions) → latest passing run
2. Download the `filedroid-debug` artifact
3. Sideload to your device:
   ```bash
   adb install -r app-debug.apk
   ```

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Navigation | Navigation Compose |
| Database | Room |
| Credentials | EncryptedSharedPreferences + Android Keystore |
| FTP/FTPS client | Apache Commons Net |
| SFTP client | SSHJ |
| FTP server | Apache FTP Server |
| SFTP server | Apache MINA SSHD |

## Default Ports

| Protocol | Default port |
|---|---|
| FTP | 2121 |
| SFTP | 2222 |

Ports can be changed in Settings.

# FileDroid

<p align="center">
  <img src="https://img.shields.io/badge/%F0%9F%93%82-FileDroid-1565C0?style=for-the-badge" alt="FileDroid" />
</p>

<p align="center">
  A dual-panel file manager for Android with built-in FTP, SFTP, and FTPS support.<br/>
  Like FileZilla, but on your phone.
</p>

<p align="center">
  <a href="../../actions/workflows/build.yml">
    <img src="https://github.com/velo4705/FileDroid/actions/workflows/build.yml/badge.svg" alt="Build" />
  </a>
  <a href="../../releases/latest">
    <img src="https://img.shields.io/github/v/release/velo4705/FileDroid?label=download" alt="Latest release" />
  </a>
  <img src="https://img.shields.io/badge/license-MIT-blue" alt="MIT license" />
  <img src="https://img.shields.io/badge/Android-8.0%2B-green" alt="Android 8.0+" />
</p>

---

## Download

Get the latest APK from [**Releases**](../../releases/latest) and install it directly on your device.

FileDroid is also coming to **F-Droid** and **IzzyOnDroid** — stay tuned.

> Android may ask you to allow installation from unknown sources. This is normal for sideloaded APKs.

---

## Features

- **Local browser** — browse, rename, delete, and create files and folders on your device, including Termux storage
- **Remote browser** — connect to any FTP, FTPS, or SFTP server and manage its files
- **File editor** — preview and edit text and code files directly on your device or on a remote server
- **Folder transfer** — upload and download entire folder trees, not just single files
- **Transfer queue** — live progress, speed indicator, cancel, and retry
- **Server mode** — run an FTP or SFTP server on your phone so desktop clients (FileZilla, WinSCP, etc.) can connect in
- **SSH terminal** — interactive terminal sessions to remote hosts with ANSI color rendering
- **Private key auth** — authenticate to SFTP servers using PEM or OpenSSH private keys
- **Themes** — Light, Dark, and Material You (Android 12+), with 8 accent colors and 3 font sizes
- **Search** — filter files and folders by name in both local and remote browsers
- **Secure storage** — all credentials stored in EncryptedSharedPreferences backed by Android Keystore

---

## Requirements

- Android 8.0 (API 26) or higher
- Wi-Fi or local network for server and remote client features

---

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Navigation | Navigation Compose |
| Database | Room |
| Credentials | EncryptedSharedPreferences + Android Keystore |
| FTP/FTPS client | Apache Commons Net |
| SFTP client | SSHJ + Bouncy Castle |
| FTP server | Apache FTP Server |
| SFTP server | Apache MINA SSHD |

---

## Default Ports

| Protocol | Default |
|---|---|
| FTP | 2121 |
| SFTP | 2222 |

Ports can be changed in **Settings → Server Ports**.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## License

FileDroid is released under the [MIT License](LICENSE).

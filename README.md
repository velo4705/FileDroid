# FileDroid

<p align="center">
  <img src=".branding/icon.svg" alt="FileDroid icon" width="108" height="108" />
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

FileDroid is also available on **F-Droid** and **IzzyOnDroid**:
- **IzzyOnDroid**: [FileDroid on IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/com.filedroid) — available now
- **F-Droid**: Coming soon

> Android may ask you to allow installation from unknown sources. This is normal for sideloaded APKs.

---

## Features

- **Local browser** — browse, rename, delete, and create files and folders on your device, including Termux storage
- **Remote browser** — connect to any FTP, FTPS, or SFTP server and manage its files
- **File editor** — preview and edit text and code files directly on your device or on a remote server
- **Folder transfer** — upload and download entire folder trees, not just single files
- **Transfer queue** — live progress, speed indicator, cancel, and retry
- **Server mode** — run an FTP or SFTP server on your phone so desktop clients (FileZilla, WinSCP, etc.) can connect in
- **Relay tunnel** — connect two FileDroid instances across any network (WiFi, mobile data, or mixed) via a relay server
- **In-app update checker** — get notified when new versions are available
- **SSH terminal** — interactive terminal sessions to remote hosts with ANSI color rendering
- **Private key auth** — authenticate to SFTP servers using PEM or OpenSSH private keys
- **Themes** — Light, Dark, and Material You (Android 12+), with 8 accent colors and 3 font sizes
- **Search** — filter files and folders by name in both local and remote browsers
- **Secure storage** — all credentials stored in EncryptedSharedPreferences backed by Android Keystore

---

## Requirements

- Android 8.0 (API 26) or higher
- Local network (same Wi-Fi) for direct FTP/SFTP connections
- Relay tunnel — works over any network including mobile data, no config needed

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

## Connecting to a remote machine from FileDroid

To connect to a machine, you need its **IP address**, **port**, **username**, and **password** (or private key for SFTP). Here's how to find them on each platform.

> **Remote access over mobile data?** Use the **FileDroid Relay Tunnel** — works over any network, no router config needed. See [Remote Access via Relay Tunnel](#remote-access-via-relay-tunnel).

---

### Android (without Termux)

Use a file manager app that has a built-in FTP server, such as [Material Files](https://play.google.com/store/apps/details?id=me.zhanghai.android.files) or [MiXplorer](https://mixplorer.com/).

1. Open the app and find its **FTP server** option (usually under a menu or sidebar)
2. Start the server — the app will display:
   - **IP address** (e.g. `192.168.1.x`)
   - **Port** (usually `2121`)
   - **Username and password** (you set these in the app, or it uses anonymous access)
3. Enter those details in FileDroid to connect

> Both devices must be on the same Wi-Fi network.

---

### iOS

Use an app that can host a local FTP server, such as [Documents by Readdle](https://apps.apple.com/app/documents-by-readdle/id364901807).

1. Open the app → tap the Wi-Fi / computer icon to start the local server
2. The app shows the full connection address (e.g. `ftp://192.168.1.x:2121`) along with any username/password
3. Enter those details in FileDroid to connect

> iOS sandboxes each app, so you can only browse files that the server app itself has access to — not the full iOS filesystem.

---

### Windows

**Find the IP address**
1. Open Command Prompt (`Win + R` → type `cmd`)
2. Run:
   ```
   ipconfig
   ```
3. Look for `IPv4 Address` under your active network adapter (e.g. `192.168.1.10`)

**Find the username**
```
echo %USERNAME%
```

**Set up an FTP/SFTP server to connect to**
- Windows has no built-in FTP/SFTP server. Install one:
  - **FileZilla Server** (FTP/FTPS) — free, easy to set up
  - **OpenSSH** (SFTP) — built into Windows 10/11:
    1. `Settings → Apps → Optional Features → Add a feature → OpenSSH Server`
    2. Start it: `Services → OpenSSH SSH Server → Start`
    3. Default SFTP port: `22`, username = your Windows username, password = your Windows password

---

### macOS

**Find the IP address**
1. `System Settings → Wi-Fi → Details` next to your network
2. Or in Terminal:
   ```bash
   ipconfig getifaddr en0
   ```

**Find the username**
```bash
whoami
```

**Enable SFTP (SSH)**
1. `System Settings → General → Sharing → Remote Login → On`
2. Default port: `22`, username = your macOS username, password = your macOS login password

---

### Linux

**Find the IP address**
```bash
ip a
# or
hostname -I
```

**Find the username**
```bash
whoami
```

**Enable SFTP (SSH)**

OpenSSH is the standard SSH/SFTP server on all distros. Install and start it:

Debian / Ubuntu / Linux Mint / Pop!_OS:
```bash
sudo apt install openssh-server
sudo systemctl enable --now ssh
```

Fedora / RHEL / CentOS / Rocky Linux:
```bash
sudo dnf install openssh-server
sudo systemctl enable --now sshd
```

Arch Linux / Manjaro / EndeavourOS:
```bash
sudo pacman -S openssh
sudo systemctl enable --now sshd
```

openSUSE:
```bash
sudo zypper install openssh
sudo systemctl enable --now sshd
```

Alpine Linux:
```bash
apk add openssh
rc-update add sshd
service sshd start
```

Default port: `22`, username = your Linux username, password = your login password.

> If you have a firewall enabled, allow SSH through:
> ```bash
> # ufw (Ubuntu/Debian)
> sudo ufw allow ssh
> # firewalld (Fedora/RHEL)
> sudo firewall-cmd --permanent --add-service=ssh && sudo firewall-cmd --reload
> ```

**Enable FTP (vsftpd)**

Debian / Ubuntu:
```bash
sudo apt install vsftpd
sudo systemctl enable --now vsftpd
```

Fedora / RHEL:
```bash
sudo dnf install vsftpd
sudo systemctl enable --now vsftpd
```

Arch Linux:
```bash
sudo pacman -S vsftpd
sudo systemctl enable --now vsftpd
```

---

### Termux (Android)

**Find the IP address**
```bash
ip route get 1 | awk '{print $7}'
# or just check Settings → Wi-Fi on your device
```

**Find the username**
```bash
whoami
# usually returns: u0_a<number>
```

**Start an SFTP server in Termux**
```bash
pkg install openssh
sshd
```
- Default port: `8022`
- Password: set one with `passwd` before connecting
- Connect from FileDroid using your device's IP, port `8022`, username from `whoami`, and the password you set

---

### In FileDroid

Once you have the details, tap **+** on the Home screen:
- **Host** — the IP address from above
- **Port** — `22` for SSH/SFTP, `21` for FTP, `8022` for Termux
- **Username** — from `whoami` or your system username
- **Password** — your login password, or leave blank if using a private key

> **Note:** Local network connections require both devices to be on the same Wi-Fi. For remote access over mobile data or across different networks, use the **Relay Tunnel** feature below.

---

## Remote Access via Relay Tunnel

The Relay Tunnel lets two FileDroid instances connect to each other over **any network** — WiFi, mobile data, or a mix of both. No router config, port forwarding, or public IP needed. Works even behind carrier-grade NAT (CGNAT).

### How it works

```
[Phone A]  ──WebSocket──▶  [Relay Server]  ──WebSocket──▶  [Phone B]
 (Host)                                              (Client)
```

1. **Host** starts its FTP/SFTP servers and connects to the relay with a **tunnel ID**
2. **Client** connects to the same relay with the same **tunnel ID**
3. The relay bridges TCP traffic between the two devices through WebSocket connections

### Step 1: Host device — start the tunnel

1. Open **Server Control** (from the Home screen or bottom nav)
2. Make sure your FTP and/or SFTP servers are running
3. Scroll to the **Relay Tunnel** section
4. **Relay URL** is pre-configured — no need to change it
5. Pick a **Tunnel ID** — a unique name both devices will use (e.g. `my-phone`)
6. Check **Enable relay tunnel** and start the servers
7. The server will display a **5-word connection code** (e.g. `apple-bright-cloud-river-star`)

> Keep the server and tunnel running on the host device.

### Step 2: Client device — connect to the tunnel

1. From the Home screen, tap **Relay Tunnel** (or navigate to the tunnel screen)
2. Enter the **5-word connection code** from the host device
3. Tap **Connect**
4. Once connected, the tunnel screen shows the local proxy ports for each protocol:
   - **FTP** → connect to `127.0.0.1` on the shown FTP port
   - **SFTP** → connect to `127.0.0.1` on the shown SFTP port

### Step 3: Create a connection profile

1. Go to **+** (Add Profile) on the Home screen
2. Set **Host** to `127.0.0.1`
3. Set **Port** to the proxy port shown in the tunnel screen (e.g. `2121` for FTP, `2222` for SFTP)
4. Set **Username** and **Password** to the host device's server credentials
5. Save and connect

### Connection code sharing

- **Copy** — tap the copy icon to copy the code to clipboard
- **Share** — tap the share icon to send the code via any app (messaging, email, etc.)

### What works over the tunnel

| Protocol | Works? | Notes |
|---|---|---|
| FTP | Yes | Passive mode, data connection relayed automatically |
| SFTP | Yes | Single connection, works reliably |
| FTPS | Yes | Explicit and implicit TLS supported |

### Troubleshooting

- **"Connection failed"** — make sure the host device has the tunnel enabled and the relay URL is correct
- **"Wrong code"** — double-check the 5-word code, words are case-insensitive but spelling must match
- **Slow transfers** — relay adds latency; for large files, local network (WiFi) is faster when available
- **Host and client on same mobile carrier** — may not work if the carrier blocks peer connections; try connecting from a different network

---

### In FileDroid (local network)

For devices on the **same Wi-Fi**, you can connect directly without the relay tunnel. Use the host device's local IP address (e.g. `192.168.1.x`) instead of `127.0.0.1`.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## License

FileDroid is released under the [MIT License](LICENSE).

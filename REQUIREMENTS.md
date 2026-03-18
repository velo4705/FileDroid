# FileDroid — Requirements

## What FileDroid Is

FileDroid is a dual-panel file manager for Android, similar to FileZilla on desktop.

- **Left panel (Your Device):** Browse your Android device's local storage, including Termux storage
- **Right panel (Remote Device):** Connect to a remote FTP/SFTP/FTPS server and browse its files
- **Transfer:** Upload and download files between the two panels
- **Server mode:** Run an FTP/SFTP server on your device so FileZilla and other clients can connect in
- **SSH Manager:** Outbound SSH terminal sessions to remote hosts

---

## Requirements

### R1 — Local File Browser (Your Device)
1. Browse device storage from a configurable root (default: external storage)
2. Navigate into subdirectories and back up
3. Show file name, size, last-modified date
4. Select one or more files/folders for transfer
5. Offer Termux home directory as a bookmark where accessible
6. Create folders, rename, delete local files
7. Long-press context menu: copy, move, rename, delete, upload

### R2 — Remote File Browser (Remote Device)
1. Connect via saved Connection_Profile (FTP/FTPS/SFTP, host, port, username, password)
2. Connect within 10 seconds or show timeout error
3. Display remote directory listing (name, size, date)
4. Navigate remote directories
5. Create folders, rename, delete remote files
6. Long-press context menu: download, rename, delete
7. Reconnection prompt on dropped connection
8. Password auth + private-key auth (SFTP)

### R3 — File Transfer
1. Upload: local file → current remote directory
2. Download: remote file → current local directory
3. Progress indicator: percentage, speed, ETA
4. Queue multiple transfers, process sequentially
5. Cancel in-progress transfer
6. Error message + retry on failure
7. Support files up to 4 GB
8. Refresh destination panel after transfer completes

### R4 — Connection Profiles
1. Store: label, protocol, host, port, username, password/key, initial remote directory
2. Encrypt all passwords and keys at rest (AES-256, Android Keystore)
3. CRUD + connect actions on profile list
4. Open Remote_Panel at profile's configured initial directory on connect
5. Support anonymous FTP (no credentials)

### R5 — FTP/SFTP Server (Inbound)
1. FTP server on configurable port (default 2121), compatible with FileZilla
2. SFTP server on configurable port (default 2222)
3. Persistent notification with server status and local IP while running
4. Restrict remote clients to configured shared root (Virtual_Filesystem)
5. Block IP after 5 failed logins in 60 seconds (300s ban)
6. Active and passive FTP transfer modes
7. Public-key auth for SFTP
8. Update displayed IP within 5 seconds on network change

### R6 — SSH Manager
1. Saved SSH connection profiles (CRUD)
2. Interactive terminal with ANSI escape code support
3. Multiple concurrent sessions in tabs
4. Auto-reconnect up to 3 times on network drop
5. Password and private-key authentication

### R7 — Security
1. EncryptedSharedPreferences + Android Keystore for all secrets
2. SFTP host keys: min 2048-bit RSA or 256-bit ECDSA, generated on first launch
3. Prompt to set server password on first launch before any server starts
4. Bind servers only to selected local network interface
5. Reject path traversal attempts (../), return permission-denied

---

## Milestone Roadmap

| Milestone | Scope | Status |
|---|---|---|
| M1 | Project foundation, DI, nav, credential store, CI | ✅ Done |
| M2 | Local file browser, file ops, Termux bookmark | Next |
| M3 | FTP/SFTP client, connection profiles, remote browser | |
| M4 | File transfer engine, progress, queue, cancel | |
| M5 | FTP/SFTP server (inbound), background service | |
| M6 | SSH Manager, terminal UI | |
| M7 | Polish, FTPS, release signing, QA | |

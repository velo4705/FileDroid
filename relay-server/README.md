# FileDroid Relay Server

A lightweight WebSocket relay server that bridges TCP traffic between two FileDroid instances, enabling remote file access over mobile data without same-network requirements.

> **Note:** FileDroid ships with a default relay URL (`wss://relay.filedroid.io/ws`). You only need to self-host your own relay server if you want a private/custom instance. Most users don't need to run their own server.

## How It Works

```
[Laptop at home]          [Relay Server]           [Your phone]
      |                        |                        |
  WiFi → FTP/SFTP         wss://relay              Mobile data
      |                        |                        |
      |--- WebSocket -------->|<----- WebSocket --------|
      |    (register           |    (join                |
      |     "my-device")      |     "my-device")        |
      |                        |                        |
      |                        |--- Bridges TCP data -->|
      |<--- Raw FTP/SFTP ---->|<--- Proxy socket -------|
```

The relay server is stateless — all tunnel state lives in memory and is cleaned up when peers disconnect.

## Quick Start

```bash
cd relay-server
npm install
npm start
```

Server listens on port **8080** by default (set `PORT` env var to change).

## Deploy to Free Cloud Hosting

### Render.com (Recommended — Free Tier)

1. Push this `relay-server/` folder to a GitHub repo (or use the main repo)
2. Go to [render.com](https://render.com) → New → Web Service
3. Connect your repo, set:
   - **Root Directory**: `relay-server`
   - **Build Command**: `npm install`
   - **Start Command**: `node server.js`
   - **Environment**: `Node`
4. Add environment variables (optional):
   - `RELAY_USERNAME` — auth username (optional, enables auth if set)
   - `RELAY_PASSWORD` — auth password
   - `PUBLIC_ADDRESS` — your service's public hostname (auto-detected on Render)
5. Deploy — you'll get `wss://your-app.onrender.com/ws`

### Railway.app (Free Tier)

1. `npm i -g @railway/cli`
2. `railway login`
3. `railway init` in `relay-server/`
4. `railway up`
5. `railway domain` to get your URL

### Fly.io (Free Tier)

```bash
cd relay-server
fly launch    # follow prompts
fly deploy
```

### Any Linux VPS

```bash
# Install Node.js 18+
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs

# Clone and start
git clone <your-repo>
cd filedroid/relay-server
npm install
node server.js

# Or use systemd, pm2, etc. for production
```

## Configuration

| Env Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | WebSocket port |
| `PUBLIC_ADDRESS` | `localhost` | Public hostname/IP shown in tunnel responses |
| `RELAY_USERNAME` | *(none)* | If set, enables authentication |
| `RELAY_PASSWORD` | *(none)* | Required when `RELAY_USERNAME` is set |

## API Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/ws` | WebSocket | Main relay endpoint |
| `/health` | GET | Health check (returns tunnel count and uptime) |

## Protocol

See `RelayClient.kt` in the FileDroid app for the full client implementation.

**Text frames (JSON control):**
```jsonc
// Client → Server
{"action":"register","tunnelId":"my-device","username":"","password":""}
{"action":"join","tunnelId":"my-device","username":"","password":""}
{"action":"open_stream","tunnelId":"my-device","streamId":1,"protocol":"ftp"}
{"action":"close_stream","tunnelId":"my-device","streamId":1}

// Server → Client
{"status":"ok","tunnelId":"my-device","address":"relay.example.com:8080"}
{"status":"error","message":"Tunnel not found"}
{"event":"stream_open","streamId":1,"protocol":"ftp"}
{"event":"stream_close","streamId":1}
{"event":"client_joined","clientCount":1}
{"event":"client_left","clientCount":0}
```

**Binary frames (data tunneling):**
```
[4 bytes: stream ID (big-endian uint32)] [N bytes: payload]
```

## Security Notes

- Without `RELAY_USERNAME`/`RELAY_PASSWORD`, anyone who knows your relay URL can create tunnels
- Enable auth in production by setting both env vars
- TLS is terminated at the hosting provider (Render/Railway provide HTTPS/WSS automatically)
- The relay sees raw TCP bytes — use SFTP or FTPS for encrypted file transfers
- Consider using a random tunnel ID (e.g., UUID) instead of simple names

## Resource Usage

- **Memory**: ~20MB idle, ~1MB per active tunnel
- **CPU**: Negligible (just shuffling bytes between WebSocket connections)
- **Bandwidth**: 1:1 with your file transfers (relay is transparent)
- Suitable for free-tier hosting (Render free: 512MB RAM, Railway free: $5 credit/month)

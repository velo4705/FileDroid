#!/usr/bin/env node
/**
 * FileDroid Relay Server
 *
 * Bridges TCP traffic between FileDroid instances (and any TCP client)
 * over WebSocket. Supports two modes:
 *
 * 1. FileDroid ↔ FileDroid: peer-to-peer via streams
 * 2. Any TCP client → relay → FileDroid host: public port forwarding
 *    (e.g. FileZilla connects to relay:port → reaches host's FTP server)
 *
 * Protocol (matches RelayClient.kt):
 *   TEXT frames (JSON control messages):
 *     Client → Server:
 *       {"action":"register","tunnelId":"xxx","ports":[2121,2222]}
 *       {"action":"join","tunnelId":"xxx"}
 *       {"action":"open_stream","tunnelId":"xxx","streamId":123,"protocol":"ftp"}
 *     Server → Client:
 *       {"status":"ok","tunnelId":"xxx","address":"host:port"}
 *       {"status":"ok","ports":{"ftp":3021,"sftp":3022}}
 *       {"status":"error","message":"..."}
 *       {"event":"stream_open","streamId":123}
 *       {"event":"stream_close","streamId":123}
 *
 *   BINARY frames:
 *     [4 bytes: stream ID (big-endian uint32)] [N bytes: payload]
 *
 *   TCP → WebSocket bridging:
 *     When an external TCP client connects to a public port, the relay
 *     opens a stream on the host and bridges the TCP data bidirectionally
 *     using binary frames with the stream ID.
 */

const http = require("http");
const net = require("net");
const { WebSocketServer } = require("ws");

const PORT = parseInt(process.env.PORT || "8080", 10);
const PUBLIC_PORT_START = parseInt(process.env.PUBLIC_PORT_START || "3000", 10);
const PUBLIC_PORT_END = parseInt(process.env.PUBLIC_PORT_END || "3100", 10);
const AUTH_ENABLED = process.env.RELAY_USERNAME != null;

// ─── Tunnel state ────────────────────────────────────────────────────

/**
 * tunnels maps tunnelId → {
 *   host: WebSocket,
 *   clients: Set<WebSocket>,
 *   publicPorts: Map<localPort → publicPort>,
 *   tcpServers: Map<localPort → net.Server>,
 *   nextStreamId: number
 * }
 */
const tunnels = new Map();

// ─── Stream ID counter (global, monotonic) ──────────────────────────

let globalStreamId = 1;

// ─── HTTP server + WebSocket upgrade ─────────────────────────────────

const server = http.createServer((req, res) => {
  if (req.url === "/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({
      status: "ok",
      tunnels: tunnels.size,
      uptime: process.uptime()
    }));
    return;
  }
  res.writeHead(200, { "Content-Type": "text/plain" });
  res.end("FileDroid Relay Server — WebSocket endpoint at ws://this-host/ws");
});

const wss = new WebSocketServer({ server, path: "/ws" });

// ─── Binary frame helpers ────────────────────────────────────────────

function encodeStreamFrame(streamId, payload) {
  const frame = Buffer.alloc(4 + payload.length);
  frame.writeUInt32BE(streamId, 0);
  if (Buffer.isBuffer(payload)) {
    payload.copy(frame, 4);
  } else {
    frame.write(payload, 4, payload.length, "binary");
  }
  return frame;
}

function decodeStreamFrame(data) {
  if (data.length < 4) return null;
  const streamId = data.readUInt32BE(0);
  const payload = data.slice(4);
  return { streamId, payload };
}

// ─── JSON helpers ────────────────────────────────────────────────────

function sendJson(ws, obj) {
  if (ws.readyState === 1) { // WebSocket.OPEN
    ws.send(JSON.stringify(obj));
  }
}

function isJson(str) {
  return typeof str === "string" && str.startsWith("{");
}

// ─── Auth check ──────────────────────────────────────────────────────

function checkAuth(username, password) {
  if (!AUTH_ENABLED) return true;
  return username === process.env.RELAY_USERNAME &&
         password === process.env.RELAY_PASSWORD;
}

// ─── Public TCP port allocation ──────────────────────────────────────

const usedPublicPorts = new Set();

function allocatePort() {
  for (let p = PUBLIC_PORT_START; p <= PUBLIC_PORT_END; p++) {
    if (!usedPublicPorts.has(p)) {
      usedPublicPorts.add(p);
      return p;
    }
  }
  return null; // exhausted
}

function releasePort(port) {
  usedPublicPorts.delete(port);
}

/**
 * Open a TCP server on a public port.
 * Incoming TCP connections → open a stream on the host → bridge bidirectionally.
 */
function openPublicPort(tunnelId, localPort, protocol) {
  const tunnel = tunnels.get(tunnelId);
  if (!tunnel) return null;

  const publicPort = allocatePort();
  if (!publicPort) return null;

  const tcpServer = net.createServer((tcpSocket) => {
    const streamId = globalStreamId++;
    tunnel.streams = tunnel.streams || new Map();
    tunnel.streams.set(streamId, tcpSocket);

    console.log(`[${ts()}] TCP → tunnel ${tunnelId}: ${tcpSocket.remoteAddress}:${tcpSocket.remotePort} → stream #${streamId} (${protocol})`);

    // Notify the host that a new stream opened from a TCP client
    sendJson(tunnel.host, {
      event: "stream_open",
      streamId: streamId,
      protocol: protocol,
      source: `${tcpSocket.remoteAddress}:${tcpSocket.remotePort}`
    });

    // TCP → WebSocket: forward incoming data to host
    tcpSocket.on("data", (data) => {
      if (tunnel.host && tunnel.host.readyState === 1) {
        tunnel.host.send(encodeStreamFrame(streamId, data), { binary: true });
      }
    });

    tcpSocket.on("close", () => {
      tunnel.streams.delete(streamId);
      if (tunnel.host && tunnel.host.readyState === 1) {
        sendJson(tunnel.host, { event: "stream_close", streamId });
      }
      console.log(`[${ts()}] TCP stream #${streamId} closed`);
    });

    tcpSocket.on("error", (err) => {
      console.error(`[${ts()}] TCP stream #${streamId} error: ${err.message}`);
      tcpSocket.destroy();
    });
  });

  tcpServer.on("error", (err) => {
    console.error(`[${ts()}] Public port ${publicPort} error: ${err.message}`);
    releasePort(publicPort);
    tunnel.publicPorts.delete(localPort);
  });

  tcpServer.listen(publicPort, "0.0.0.0", () => {
    console.log(`[${ts()}] Public port ${publicPort} → tunnel ${tunnelId}:${localPort} (${protocol})`);
  });

  tunnel.tcpServers.set(localPort, tcpServer);
  tunnel.publicPorts.set(localPort, publicPort);

  return publicPort;
}

function closePublicPorts(tunnel) {
  if (!tunnel.tcpServers) return;
  for (const [localPort, tcpServer] of tunnel.tcpServers) {
    const publicPort = tunnel.publicPorts.get(localPort);
    tcpServer.close();
    if (publicPort) releasePort(publicPort);
    console.log(`[${ts()}] Closed public port ${publicPort} (was → ${localPort})`);
  }
  tunnel.tcpServers.clear();
  tunnel.publicPorts.clear();
}

// ─── WebSocket handler ───────────────────────────────────────────────

wss.on("connection", (ws, req) => {
  const peerIp = req.headers["x-forwarded-for"] || req.socket.remoteAddress;
  let role = null;     // "host" or "client"
  let tunnelId = null;

  ws.isAlive = true;
  ws.on("pong", () => { ws.isAlive = true; });

  ws.on("message", (data, isBinary) => {
    // ── Text frame: JSON control message ──
    if (!isBinary && isJson(data.toString())) {
      const msg = JSON.parse(data.toString());
      handleControl(ws, msg, peerIp);
      return;
    }

    // ── Binary frame: [4-byte stream ID][payload] ──
    if (isBinary || Buffer.isBuffer(data)) {
      const buf = Buffer.isBuffer(data) ? data : Buffer.from(data);
      const frame = decodeStreamFrame(buf);
      if (!frame) return;

      const tunnel = tunnels.get(tunnelId);
      if (!tunnel) return;

      if (role === "host") {
        // Host sends data — route to either a FileDroid client or a TCP client
        // Check if this stream has an associated TCP socket
        if (tunnel.streams && tunnel.streams.has(frame.streamId)) {
          const tcpSocket = tunnel.streams.get(frame.streamId);
          if (!tcpSocket.destroyed) {
            tcpSocket.write(frame.payload);
          }
        }
        // Also forward to any FileDroid clients
        for (const client of tunnel.clients) {
          if (client.readyState === 1) {
            client.send(encodeStreamFrame(frame.streamId, frame.payload), { binary: true });
          }
        }
      } else if (role === "client") {
        // FileDroid client sends data → forward to the host
        if (tunnel.host && tunnel.host.readyState === 1) {
          tunnel.host.send(encodeStreamFrame(frame.streamId, frame.payload), { binary: true });
        }
      }
    }
  });

  ws.on("close", () => {
    const tunnel = tunnels.get(tunnelId);
    if (!tunnel) return;

    if (role === "host") {
      // Host left — tear down everything
      closePublicPorts(tunnel);
      for (const client of tunnel.clients) {
        sendJson(client, { status: "error", message: "Host disconnected" });
        client.close(1001, "Host disconnected");
      }
      tunnels.delete(tunnelId);
      console.log(`[${ts()}] Host left tunnel ${tunnelId} — tunnel removed`);
    } else if (role === "client") {
      tunnel.clients.delete(ws);
      if (tunnel.host && tunnel.host.readyState === 1) {
        sendJson(tunnel.host, { event: "client_left", clientCount: tunnel.clients.size });
      }
      console.log(`[${ts()}] Client left tunnel ${tunnelId} (${tunnel.clients.size} clients remaining)`);
    }

    if (tunnel && !tunnel.host && tunnel.clients.size === 0) {
      tunnels.delete(tunnelId);
    }
  });

  ws.on("error", (err) => {
    console.error(`[${ts()}] WebSocket error: ${err.message}`);
  });

  // ── Route control messages ──
  function handleControl(ws, msg, peerIp) {
    const action = msg.action;

    if (action === "register") {
      const tid = msg.tunnelId;
      if (!tid) {
        sendJson(ws, { status: "error", message: "tunnelId is required" });
        ws.close(4000, "Missing tunnelId");
        return;
      }

      if (AUTH_ENABLED && !checkAuth(msg.username || "", msg.password || "")) {
        sendJson(ws, { status: "error", message: "Authentication failed" });
        ws.close(4001, "Auth failed");
        return;
      }

      if (tunnels.has(tid)) {
        sendJson(ws, { status: "error", message: "Tunnel ID already in use" });
        ws.close(4002, "Tunnel ID already in use");
        return;
      }

      const tunnel = {
        host: ws,
        clients: new Set(),
        publicPorts: new Map(),
        tcpServers: new Map(),
        streams: new Map(),
        nextStreamId: 1
      };
      tunnels.set(tid, tunnel);
      role = "host";
      tunnelId = tid;

      // Open public ports for FTP/SFTP if requested
      const requestedPorts = msg.ports || [];
      const openedPorts = {};

      for (const entry of requestedPorts) {
        const localPort = typeof entry === "object" ? entry.port : entry;
        const protocol = typeof entry === "object" ? entry.protocol : "unknown";
        const publicPort = openPublicPort(tid, localPort, protocol);
        if (publicPort) {
          openedPorts[protocol] = publicPort;
        } else {
          console.log(`[${ts()}] Failed to open public port for ${protocol}:${localPort}`);
        }
      }

      sendJson(ws, {
        status: "ok",
        tunnelId: tid,
        address: `${getPublicAddress()}:${PORT}`,
        ports: openedPorts
      });

      console.log(`[${ts()}] Host registered tunnel ${tid} from ${peerIp} — public ports: ${JSON.stringify(openedPorts)}`);

    } else if (action === "join") {
      const tid = msg.tunnelId;
      if (!tid) {
        sendJson(ws, { status: "error", message: "tunnelId is required" });
        ws.close(4000, "Missing tunnelId");
        return;
      }

      if (AUTH_ENABLED && !checkAuth(msg.username || "", msg.password || "")) {
        sendJson(ws, { status: "error", message: "Authentication failed" });
        ws.close(4001, "Auth failed");
        return;
      }

      const tunnel = tunnels.get(tid);
      if (!tunnel) {
        sendJson(ws, { status: "error", message: "Tunnel not found" });
        ws.close(4003, "Tunnel not found");
        return;
      }

      tunnel.clients.add(ws);
      role = "client";
      tunnelId = tid;

      // Report public ports to the joining client
      const publicPorts = {};
      if (tunnel.publicPorts) {
        for (const [localPort, publicPort] of tunnel.publicPorts) {
          // Determine protocol from tcpServers key — we don't store protocol directly,
          // but the client doesn't need local ports, just public ones
          publicPorts[`port_${localPort}`] = publicPort;
        }
      }

      sendJson(ws, {
        status: "ok",
        tunnelId: tid,
        address: `${getPublicAddress()}:${PORT}`,
        ports: publicPorts
      });

      if (tunnel.host && tunnel.host.readyState === 1) {
        sendJson(tunnel.host, { event: "client_joined", clientCount: tunnel.clients.size });
      }

      console.log(`[${ts()}] Client joined tunnel ${tid} from ${peerIp} (${tunnel.clients.size} clients)`);

    } else if (action === "open_stream") {
      const tid = msg.tunnelId;
      const streamId = parseInt(msg.streamId, 10);
      const protocol = msg.protocol || "unknown";

      if (isNaN(streamId)) return;

      const tunnel = tunnels.get(tid);
      if (!tunnel) return;

      for (const client of tunnel.clients) {
        if (client.readyState === 1) {
          sendJson(client, { event: "stream_open", streamId, protocol });
        }
      }

      console.log(`[${ts()}] Stream #${streamId} (${protocol}) opened on tunnel ${tid}`);

    } else if (action === "close_stream") {
      const tid = msg.tunnelId;
      const streamId = parseInt(msg.streamId, 10);
      if (isNaN(streamId)) return;

      const tunnel = tunnels.get(tid);
      if (!tunnel) return;

      // Close TCP socket if this stream has one
      if (tunnel.streams && tunnel.streams.has(streamId)) {
        const tcpSocket = tunnel.streams.get(streamId);
        if (!tcpSocket.destroyed) tcpSocket.destroy();
        tunnel.streams.delete(streamId);
      }

      for (const client of tunnel.clients) {
        if (client.readyState === 1) {
          sendJson(client, { event: "stream_close", streamId });
        }
      }

      console.log(`[${ts()}] Stream #${streamId} closed on tunnel ${tid}`);

    } else {
      sendJson(ws, { status: "error", message: `Unknown action: ${action}` });
    }
  }
});

// ─── Heartbeat ───────────────────────────────────────────────────────

const heartbeat = setInterval(() => {
  wss.clients.forEach((ws) => {
    if (!ws.isAlive) {
      ws.terminate();
      return;
    }
    ws.isAlive = false;
    ws.ping();
  });
}, 30_000);

wss.on("close", () => clearInterval(heartbeat));

// ─── Helpers ─────────────────────────────────────────────────────────

function ts() {
  return new Date().toISOString().slice(11, 19);
}

function getPublicAddress() {
  return process.env.PUBLIC_ADDRESS || "localhost";
}

// ─── Start ───────────────────────────────────────────────────────────

server.listen(PORT, "0.0.0.0", () => {
  console.log(`FileDroid Relay Server listening on port ${PORT}`);
  console.log(`  WebSocket endpoint: ws://0.0.0.0:${PORT}/ws`);
  console.log(`  Health check:       http://0.0.0.0:${PORT}/health`);
  console.log(`  Public port range:  ${PUBLIC_PORT_START}-${PUBLIC_PORT_END}`);
  if (AUTH_ENABLED) {
    console.log(`  Auth: ENABLED (username=${process.env.RELAY_USERNAME})`);
  } else {
    console.log(`  Auth: DISABLED (set RELAY_USERNAME and RELAY_PASSWORD to enable)`);
  }
});

// ─── Graceful shutdown ───────────────────────────────────────────────

process.on("SIGTERM", () => {
  console.log("Shutting down...");
  for (const [tid, tunnel] of tunnels) {
    closePublicPorts(tunnel);
    if (tunnel.host) tunnel.host.close(1001, "Server shutting down");
    for (const client of tunnel.clients) {
      client.close(1001, "Server shutting down");
    }
  }
  wss.close();
  server.close(() => process.exit(0));
});

process.on("SIGINT", () => {
  process.emit("SIGTERM");
});

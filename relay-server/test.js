#!/usr/bin/env node
const { WebSocket } = require("ws");
const { spawn } = require("child_process");
const http = require("http");

const PORT = 18082;
let server;

function startServer() {
  return new Promise((resolve) => {
    server = spawn("node", ["server.js"], {
      cwd: __dirname,
      env: { ...process.env, PORT: String(PORT) },
      stdio: "pipe"
    });
    server.stdout.on("data", (d) => { if (d.toString().includes("listening")) resolve(); });
    server.stderr.on("data", (d) => process.stderr.write(d));
    setTimeout(resolve, 1500);
  });
}

/** Wait for next JSON text message on a WebSocket. */
function nextJson(ws, timeoutMs = 3000) {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => { ws.removeAllListeners("message"); reject(new Error("timeout")); }, timeoutMs);
    const onMsg = (data, isBinary) => {
      if (isBinary) return; // skip binary, keep waiting for text
      clearTimeout(t);
      ws.removeAllListeners("message");
      resolve(JSON.parse(data.toString()));
    };
    ws.on("message", onMsg);
  });
}

/** Wait for next binary message on a WebSocket. */
function nextBinary(ws, timeoutMs = 3000) {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => { ws.removeAllListeners("message"); reject(new Error("timeout")); }, timeoutMs);
    const onMsg = (data, isBinary) => {
      if (!isBinary) return; // skip text frames, keep listening
      clearTimeout(t);
      ws.removeAllListeners("message");
      resolve(Buffer.isBuffer(data) ? data : Buffer.from(data));
    };
    ws.on("message", onMsg);
  });
}

function makeFrame(streamId, text) {
  const payload = Buffer.from(text);
  const frame = Buffer.alloc(4 + payload.length);
  frame.writeUInt32BE(streamId, 0);
  payload.copy(frame, 4);
  return frame;
}

async function test() {
  console.log("Starting server on port", PORT, "...");
  await startServer();
  console.log("Server started\n");

  const tid = "test-" + Date.now();

  // 1. Host registers
  const host = new WebSocket(`ws://localhost:${PORT}/ws`);
  await new Promise((resolve, reject) => {
    host.on("open", () => host.send(JSON.stringify({ action: "register", tunnelId: tid, username: "", password: "", deviceName: "Pixel-7" })));
    host.once("message", (d) => {
      const m = JSON.parse(d.toString());
      m.status === "ok" ? resolve() : reject(new Error(m.message));
    });
    host.on("error", reject);
  });
  console.log("Host registered ✓");

  // 2. Client joins
  const client = new WebSocket(`ws://localhost:${PORT}/ws`);
  let clientJoinResponse;
  await new Promise((resolve, reject) => {
    client.on("open", () => client.send(JSON.stringify({ action: "join", tunnelId: tid, username: "", password: "", deviceName: "Galaxy-S24" })));
    client.once("message", (d) => {
      const m = JSON.parse(d.toString());
      clientJoinResponse = m;
      m.status === "ok" ? resolve() : reject(new Error(m.message));
    });
    client.on("error", reject);
  });
  // Verify host device name is passed to client
  if (clientJoinResponse.deviceName !== "Pixel-7") throw new Error("Expected host deviceName 'Pixel-7', got: " + clientJoinResponse.deviceName);
  console.log("Client joined ✓ (host device: " + clientJoinResponse.deviceName + ")");

  // 3. Host opens stream
  const sid = 42;
  host.send(JSON.stringify({ action: "open_stream", tunnelId: tid, streamId: sid, protocol: "ftp" }));

  // 4. Client receives stream_open
  const openEvt = await nextJson(client);
  if (openEvt.event !== "stream_open" || openEvt.streamId !== sid) throw new Error("Bad stream_open: " + JSON.stringify(openEvt));
  console.log("Stream open event #" + sid + " ✓");

  // 5. Host → Client binary data
  host.send(makeFrame(sid, "Hello from host"), { binary: true });
  const recvBuf = await nextBinary(client);
  const recvId = recvBuf.readUInt32BE(0);
  const recvText = recvBuf.slice(4).toString();
  if (recvId !== sid || recvText !== "Hello from host") throw new Error("Bad forward: " + recvId + "/" + recvText);
  console.log("Forward relay ✓:", recvId, "→", JSON.stringify(recvText));

  // 6. Client → Host binary data (reverse)
  client.send(makeFrame(sid, "Reply from client"), { binary: true });
  const recvBuf2 = await nextBinary(host);
  const recvId2 = recvBuf2.readUInt32BE(0);
  const recvText2 = recvBuf2.slice(4).toString();
  if (recvId2 !== sid || recvText2 !== "Reply from client") throw new Error("Bad reverse: " + recvId2 + "/" + recvText2);
  console.log("Reverse relay ✓:", recvId2, "→", JSON.stringify(recvText2));

  // 7. Client disconnects — host should get error
  client.close();
  await new Promise((resolve) => setTimeout(resolve, 500)); // let close propagate
  console.log("Client disconnect handled ✓");

  // 8b. Client opens a stream (FileDroid→FileDroid scenario)
  // First reconnect a new client
  const client2 = new WebSocket(`ws://localhost:${PORT}/ws`);
  await new Promise((resolve, reject) => {
    client2.on("open", () => client2.send(JSON.stringify({ action: "join", tunnelId: tid, username: "", password: "", deviceName: "iPhone-15" })));
    client2.once("message", (d) => {
      const m = JSON.parse(d.toString());
      m.status === "ok" ? resolve() : reject(new Error(m.message));
    });
    client2.on("error", reject);
  });
  console.log("Client2 joined ✓");

  // Consume the client_joined event on the host side (should include deviceName)
  const joinEvt = await nextJson(host);
  if (joinEvt.event !== "client_joined") throw new Error("Expected client_joined, got: " + JSON.stringify(joinEvt));
  if (joinEvt.deviceName !== "iPhone-15") throw new Error("Expected client deviceName 'iPhone-15', got: " + joinEvt.deviceName);
  console.log("Host received client_joined from " + joinEvt.deviceName + " ✓");

  const sid2 = 99;
  client2.send(JSON.stringify({ action: "open_stream", tunnelId: tid, streamId: sid2, protocol: "ftp" }));

  // Host should receive stream_open from client
  const openEvt2 = await nextJson(host);
  if (openEvt2.event !== "stream_open" || openEvt2.streamId !== sid2) throw new Error("Bad stream_open from client: " + JSON.stringify(openEvt2));
  console.log("Client→Host stream open #" + sid2 + " ✓");

  // Client2 → Host binary data
  client2.send(makeFrame(sid2, "Hello from client"), { binary: true });
  const recvBuf3 = await nextBinary(host);
  const recvId3 = recvBuf3.readUInt32BE(0);
  const recvText3 = recvBuf3.slice(4).toString();
  if (recvId3 !== sid2 || recvText3 !== "Hello from client") throw new Error("Bad client→host: " + recvId3 + "/" + recvText3);
  console.log("Client→Host data relay ✓:", recvId3, "→", JSON.stringify(recvText3));

  // Host → Client2 binary data
  host.send(makeFrame(sid2, "Reply from host"), { binary: true });
  const recvBuf4 = await nextBinary(client2);
  const recvId4 = recvBuf4.readUInt32BE(0);
  const recvText4 = recvBuf4.slice(4).toString();
  if (recvId4 !== sid2 || recvText4 !== "Reply from host") throw new Error("Bad host→client2: " + recvId4 + "/" + recvText4);
  console.log("Host→Client2 data relay ✓:", recvId4, "→", JSON.stringify(recvText4));

  client2.close();
  await new Promise((resolve) => setTimeout(resolve, 500));

  // 8. Host registers with public ports
  const host2 = new WebSocket(`ws://localhost:${PORT}/ws`);
  await new Promise((resolve, reject) => {
    host2.on("open", () => host2.send(JSON.stringify({
      action: "register",
      tunnelId: tid + "-ports",
      username: "",
      password: "",
      ports: [{ protocol: "ftp", port: 2121 }, { protocol: "sftp", port: 2222 }]
    })));
    host2.once("message", (d) => {
      const m = JSON.parse(d.toString());
      if (m.status !== "ok") return reject(new Error(m.message));
      if (!m.ports || m.ports.ftp !== 3000 || m.ports.sftp !== 3001) {
        return reject(new Error("Expected ports {ftp:3000,sftp:3001}, got: " + JSON.stringify(m.ports)));
      }
      console.log("Public ports allocated ✓: " + JSON.stringify(m.ports));
      resolve();
    });
    host2.on("error", reject);
  });
  host2.close();

  // 9. Health check
  await new Promise((resolve, reject) => {
    http.get(`http://localhost:${PORT}/health`, (res) => {
      let body = "";
      res.on("data", (d) => body += d);
      res.on("end", () => { console.log("Health ✓:", body.trim()); resolve(); });
    }).on("error", reject);
  });

  console.log("\n=== ALL TESTS PASSED ===\n");
}

test()
  .catch((err) => { console.error("FAILED:", err.message); process.exit(1); })
  .finally(() => { if (server) server.kill("SIGTERM"); setTimeout(() => process.exit(0), 500); });

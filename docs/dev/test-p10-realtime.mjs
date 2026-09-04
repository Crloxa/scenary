// P10 实时通知黑盒：真实 Compose/Nginx WebSocket 首帧认证、事务提交后通知和非法令牌拒绝。
import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const ROOT = fileURLToPath(new URL("../..", import.meta.url));
const API = process.env.SCENARY_API_BASE_URL || "http://localhost:8081/api/v1";
const WS = process.env.SCENARY_WS_URL || API.replace(/^http/, "ws") + "/ws/notifications";
const pass = [];
const fail = [];
const ok = (name, condition, detail = "") => {
  (condition ? pass : fail).push(name);
  console.log((condition ? "PASS" : "FAIL") + " | " + name + (detail ? " | " + detail : ""));
};

const request = async (path, options = {}) => (await fetch(API + path, options)).json();
const headers = token => ({ Authorization: "Bearer " + token });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

function compose(args) {
  const result = spawnSync("docker", ["compose", ...args], {
    cwd: ROOT,
    encoding: "utf8",
  });
  if (result.status !== 0) throw new Error("docker compose command failed");
  return result.stdout.trim();
}

async function register(username, password, nickname) {
  const body = await request("/auth/register", {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ username, password, nickname }),
  });
  if (body.code !== 0) throw new Error("register failed code=" + body.code);
  return body.data;
}

async function uploadReady(token) {
  const image = readFileSync(ROOT + "/docs/dev/fixtures/a.png");
  const form = new FormData();
  form.append("files", new Blob([image], { type: "image/png" }), "p10-realtime.png");
  const uploaded = await request("/media/images", { method: "POST", headers: headers(token), body: form });
  if (uploaded.code !== 0) throw new Error("upload failed code=" + uploaded.code);
  const mediaId = uploaded.data.items[0].mediaId;
  for (let i = 0; i < 30; i += 1) {
    const state = await request("/media/" + mediaId, { headers: headers(token) });
    if (state.data.status === 1) return mediaId;
    if (state.data.status === 2) throw new Error("media processing failed");
    await sleep(250);
  }
  throw new Error("media processing timeout");
}

async function publish(token, mediaId) {
  const body = await request("/notes", {
    method: "POST", headers: { ...headers(token), "content-type": "application/json" },
    body: JSON.stringify({ title: "P10 实时通知验收", content: "WebSocket 黑盒夹具", mediaIds: [mediaId] }),
  });
  if (body.code !== 0) throw new Error("publish failed code=" + body.code);
  return body.data.id;
}

function waitForMessage(socket, predicate, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const onMessage = event => {
      let body;
      try { body = JSON.parse(event.data); } catch { return; }
      if (!predicate(body)) return;
      clearTimeout(timer);
      socket.removeEventListener("message", onMessage);
      resolve(body);
    };
    const timer = setTimeout(() => {
      socket.removeEventListener("message", onMessage);
      reject(new Error("WebSocket message timeout"));
    }, timeoutMs);
    socket.addEventListener("message", onMessage);
  });
}

async function openAuthenticated(token) {
  const socket = new WebSocket(WS);
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("WebSocket open timeout")), 5000);
    socket.addEventListener("open", () => { clearTimeout(timer); resolve(); }, { once: true });
    socket.addEventListener("error", () => { clearTimeout(timer); reject(new Error("WebSocket open failed")); }, { once: true });
  });
  socket.send(JSON.stringify({ type: "AUTH", accessToken: token }));
  await waitForMessage(socket, message => message.type === "READY");
  return socket;
}

async function expectCloseAfterInvalidAuth() {
  const socket = new WebSocket(WS);
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("invalid-auth socket open timeout")), 5000);
    socket.addEventListener("open", () => { clearTimeout(timer); resolve(); }, { once: true });
    socket.addEventListener("error", () => {}, { once: true });
  });
  socket.send(JSON.stringify({ type: "AUTH", accessToken: "not-a-real-token" }));
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("invalid-auth socket was not closed")), 5000);
    socket.addEventListener("close", () => { clearTimeout(timer); resolve(); }, { once: true });
  });
}

try {
  const suffix = Date.now().toString(36).slice(-7);
  const password = "R9" + Date.now().toString(36) + "a!";
  const author = await register("p10ws_a_" + suffix, password, "实时通知作者");
  const actor = await register("p10ws_b_" + suffix, password, "实时通知评论者");
  const noteId = await publish(author.accessToken, await uploadReady(author.accessToken));
  const socket = await openAuthenticated(author.accessToken);
  ok("JWT 首帧认证并收到 READY", true);

  const notification = waitForMessage(socket, message => message.type === "NOTIFICATION");
  const comment = await request("/notes/" + noteId + "/comments", {
    method: "POST", headers: { ...headers(actor.accessToken), "content-type": "application/json" },
    body: JSON.stringify({ content: "实时通知黑盒评论" }),
  });
  const pushed = await notification;
  ok("评论接口成功后收到事务提交通知", comment.code === 0 && pushed.unreadCount >= 1,
    "commentCode=" + comment.code + "; unread=" + pushed.unreadCount);

  socket.close();
  await expectCloseAfterInvalidAuth();
  ok("非法 JWT 首帧被关闭", true);
  const composeConfig = compose(["config"]);
  ok("Compose 保留 WebSocket 升级配置", composeConfig.includes("SCENARY_WEBSOCKET_ALLOWED_ORIGINS"));
} catch (error) {
  ok("P10 实时通知黑盒执行完成", false, error.message);
}

console.log("\n==== PASS=" + pass.length + " FAIL=" + fail.length + " ====");
process.exit(fail.length ? 1 : 0);

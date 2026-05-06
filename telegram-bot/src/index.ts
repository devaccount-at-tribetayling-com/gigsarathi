import { webhookCallback } from "grammy";
import { createServer } from "node:http";
import { createBot } from "./bot.js";

const bot = createBot();

const port = Number(process.env.PORT ?? 3002);
const webhookPath = "/webhook";

// Use Grammy's built-in webhook callback — handles 200 ACK automatically
const handleUpdate = webhookCallback(bot, "http");

const server = createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ status: "ok", service: "telegram-bot" }));
    return;
  }

  if (req.method === "POST" && req.url === webhookPath) {
    await handleUpdate(req, res);
    return;
  }

  res.writeHead(404);
  res.end("Not found");
});

server.listen(port, () => {
  console.log(`[telegram-bot] Listening on http://localhost:${port}`);
  console.log(`[telegram-bot] Webhook endpoint: POST ${webhookPath}`);
});

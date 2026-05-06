import { Hono } from "hono";
import { serve } from "@hono/node-server";
import webhook from "./webhook.js";

const app = new Hono();

app.route("/", webhook);

app.get("/health", (c) => c.json({ status: "ok", service: "whatsapp-bot" }));

const port = Number(process.env.PORT ?? 3001);

serve({ fetch: app.fetch, port }, (info) => {
  console.log(`[whatsapp-bot] Listening on http://localhost:${info.port}`);
});

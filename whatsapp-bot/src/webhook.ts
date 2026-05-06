import { Hono } from "hono";
import { JavaClient } from "./client.js";

const webhook = new Hono();
const javaClient = new JavaClient();

// GET /webhook — Meta webhook verification
webhook.get("/webhook", (c) => {
  const mode = c.req.query("hub.mode");
  const token = c.req.query("hub.verify_token");
  const challenge = c.req.query("hub.challenge");

  const verifyToken = process.env.WA_VERIFY_TOKEN ?? "";

  if (mode === "subscribe" && token === verifyToken) {
    return c.text(challenge ?? "", 200);
  }

  return c.text("Forbidden", 403);
});

// POST /webhook — receive Meta Cloud API messages
webhook.post("/webhook", async (c) => {
  try {
    const body = await c.req.json<Record<string, unknown>>();

    // Extract message details from Meta Cloud API payload (stub)
    const entry = (body.entry as Array<Record<string, unknown>>)?.[0];
    const changes = (entry?.changes as Array<Record<string, unknown>>)?.[0];
    const value = changes?.value as Record<string, unknown> | undefined;
    const messages = value?.messages as Array<Record<string, unknown>>;
    const message = messages?.[0];

    if (message) {
      const userId = (message.from as string) ?? "unknown";
      const messageType = (message.type as string) ?? "text";
      const payload: Record<string, unknown> = {
        ...(message[messageType] as Record<string, unknown>),
      };

      // Fire-and-forget — always ACK 200 to Meta
      javaClient.forward({ platform: "whatsapp", userId, messageType, payload }).catch(
        (err: unknown) => {
          console.error("[whatsapp-bot] Failed to forward message to Java:", err);
        }
      );
    }
  } catch (err) {
    // Log but never propagate — Meta requires 200 ACK
    console.error("[whatsapp-bot] Error processing webhook payload:", err);
  }

  // Always return 200 ACK to Meta (platform requirement)
  return c.json({ status: "ok" }, 200);
});

export default webhook;

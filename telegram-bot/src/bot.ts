import { Bot } from "grammy";
import { JavaClient } from "./client.js";

const javaClient = new JavaClient();

export function createBot(): Bot {
  const token = process.env.TELEGRAM_BOT_TOKEN;
  if (!token) {
    throw new Error("TELEGRAM_BOT_TOKEN is required");
  }

  const bot = new Bot(token);

  // Handle all incoming messages
  bot.on("message", async (ctx) => {
    try {
      const userId = String(ctx.from?.id ?? "unknown");
      const text = ctx.message.text;
      const messageType = text ? "text" : "other";
      const payload: Record<string, unknown> = {
        text: text ?? null,
        chatId: ctx.chat.id,
        chatType: ctx.chat.type,
      };

      // Fire-and-forget — Grammy handles the ACK automatically
      javaClient
        .forward({ platform: "telegram", userId, messageType, payload })
        .catch((err: unknown) => {
          console.error("[telegram-bot] Failed to forward message to Java:", err);
        });
    } catch (err) {
      // Log but never propagate — platform ACK is handled by Grammy framework
      console.error("[telegram-bot] Error processing message:", err);
    }
  });

  return bot;
}

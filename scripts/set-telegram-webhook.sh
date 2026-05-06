#!/bin/bash
set -e

WEBHOOK_URL="${WEBHOOK_URL:?WEBHOOK_URL is required}"
BOT_TOKEN="${TELEGRAM_BOT_TOKEN:?TELEGRAM_BOT_TOKEN is required}"

curl -s -X POST "https://api.telegram.org/bot${BOT_TOKEN}/setWebhook" \
  -H "Content-Type: application/json" \
  -d "{\"url\": \"${WEBHOOK_URL}/webhook\"}" | jq .

echo "Telegram webhook set to: ${WEBHOOK_URL}/webhook"

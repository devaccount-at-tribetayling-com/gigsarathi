#!/usr/bin/env bash
# Gigsarathi AI review wrapper
#
# Usage: ./scripts/codex-review.sh <template> <file-or-glob> [<gate-label>] [--extra-context <string>]
#
# Exit codes:
#   0 = VERDICT: PASS
#   1 = VERDICT: FAIL (blocks commit)
#   2 = infra / malformed output (also blocks)
#
# CLI priority: claude (Claude Code CLI) → codex (Codex CLI).
# No API key env vars required — both CLIs use their own stored credentials.
set -euo pipefail

TEMPLATE="$1"
FILE="$2"
LABEL="${3:-review}"

# Optional --extra-context flag (4th arg) followed by context string (5th arg).
# Used by merge-conflict-scan to inject MERGE ORDER BOOLEANS computed via git cat-file.
EXTRA_CONTEXT=""
if [[ "${4:-}" == "--extra-context" ]]; then
  EXTRA_CONTEXT="${5:-}"
fi

# Detect available CLI — prefer codex, fall back to claude.
if command -v codex >/dev/null 2>&1; then
  AI_CLI=codex
elif command -v claude >/dev/null 2>&1; then
  AI_CLI=claude
else
  echo "ERROR: neither 'codex' nor 'claude' CLI found on PATH." >&2
  echo "  Install Codex:       npm install -g @openai/codex" >&2
  echo "  Install Claude Code: https://claude.ai/code" >&2
  exit 2
fi

LOG_DIR=".omc/logs/codex-reviews"
mkdir -p "$LOG_DIR"
LOG="$LOG_DIR/${LABEL}-${TEMPLATE}.log"

# $FILE may be a space-separated list for multi-file reviews.
# Each path gets its own # FILE: heading block inlined into the prompt.
FILE_PAYLOAD=""
for f in $FILE; do
  [[ -f "$f" ]] || { echo "ERROR: file not found: $f" >&2; exit 2; }
  FILE_PAYLOAD+=$'\n\n'"# FILE: ${f}"$'\n'"$(cat "$f")"
done
PROMPT_BODY="$(cat "scripts/codex-prompts/${TEMPLATE}.md")${FILE_PAYLOAD}"

if [[ -n "$EXTRA_CONTEXT" ]]; then
  PROMPT_BODY="${PROMPT_BODY}"$'\n\n'"# MERGE ORDER BOOLEANS"$'\n'"${EXTRA_CONTEXT}"
fi

# set +e so we can capture PIPESTATUS before re-enabling exit-on-error.
# `|| true` must NOT be used — it zeros PIPESTATUS.
set +e
if [[ "$AI_CLI" == "claude" ]]; then
  claude -p "$PROMPT_BODY" 2>&1 | tee "$LOG"
else
  codex exec --model gpt-5.3-codex --sandbox read-only --skip-git-repo-check "$PROMPT_BODY" \
    2>&1 | tee "$LOG"
fi
cli_exit="${PIPESTATUS[0]}"
set -e

if [[ "$cli_exit" -ne 0 ]]; then
  echo "ERROR: ${AI_CLI} invocation failed (exit ${cli_exit})" >&2
  exit 2
fi

# tr -d '\r' strips Windows CRLF so the string comparison never silently mismatches.
last_line="$(grep -v '^[[:space:]]*$' "$LOG" | tail -1 | tr -d '\r')"

if [[ "$last_line" != "VERDICT: PASS" && "$last_line" != "VERDICT: FAIL" ]]; then
  echo "ERROR: no valid VERDICT in output — last line: ${last_line}" >&2
  exit 2
fi

[[ "$last_line" == "VERDICT: FAIL" ]] && exit 1
exit 0

#!/usr/bin/env bash
# Gigsarathi Codex review wrapper
#
# Usage: ./scripts/codex-review.sh <template> <file-or-glob> [<gate-label>] [--extra-context <string>]
#
# Exit codes:
#   0 = VERDICT: PASS
#   1 = VERDICT: FAIL (blocks merge)
#   2 = infra / malformed output (also blocks; on-call distinguishes from review failure via exit code)
#
# Cost guard: configure a monthly OpenAI usage alert at Phase 0 kickoff.
# If the alert fires, the team lead decides whether to pause non-critical PR activity
# for the remainder of the billing cycle. Do NOT auto-downgrade acceptance-criteria to
# advisory-only — that contradicts the plan's rejection of Option D in the ADR.
set -euo pipefail

TEMPLATE="$1"
FILE="$2"
LABEL="${3:-review}"

# Optional --extra-context flag (4th arg) followed by context string (5th arg).
# Used by merge-conflict-scan to inject MERGE ORDER BOOLEANS computed by git cat-file
# before invoking Codex, so Codex never infers file existence from truncated ls-tree.
EXTRA_CONTEXT=""
if [[ "${4:-}" == "--extra-context" ]]; then
  EXTRA_CONTEXT="${5:-}"
fi

# Fail fast with a clear message if Codex CLI is not installed — prevents silent skips.
command -v codex >/dev/null 2>&1 || {
  echo "ERROR: codex CLI not found on PATH — install via: npm install -g @openai/codex" >&2
  exit 2
}

LOG_DIR=".omc/logs/codex-reviews"
mkdir -p "$LOG_DIR"
# Label+template uniquely identifies every invocation; avoids broken basename when $FILE is multi-path.
LOG="$LOG_DIR/${LABEL}-${TEMPLATE}.log"

# $FILE may be a space-separated list for multi-file reviews (e.g., test-coverage template).
# Invariant: all file paths in this repo are Java package paths (no embedded spaces).
# Each path gets its own # FILE: heading block inlined into the prompt.
FILE_PAYLOAD=""
for f in $FILE; do
  [[ -f "$f" ]] || { echo "ERROR: file not found: $f" >&2; exit 2; }
  FILE_PAYLOAD+=$'\n\n'"# FILE: ${f}"$'\n'"$(cat "$f")"
done
PROMPT_BODY="$(cat "scripts/codex-prompts/${TEMPLATE}.md")${FILE_PAYLOAD}"

# Append extra context block to the prompt (e.g., merge-order booleans for merge-conflict-scan).
if [[ -n "$EXTRA_CONTEXT" ]]; then
  PROMPT_BODY="${PROMPT_BODY}"$'\n\n'"# MERGE ORDER BOOLEANS"$'\n'"${EXTRA_CONTEXT}"
fi

# set +e disables exit-on-error so the pipeline can fail without killing the script.
# This is the correct pattern for capturing ${PIPESTATUS[0]} under set -euo pipefail:
# `|| true` must NOT be used because it replaces PIPESTATUS with true's exit codes (all 0).
set +e
codex exec --model gpt-5.3-codex --sandbox read-only --skip-git-repo-check "$PROMPT_BODY" \
  2>&1 | tee "$LOG"
codex_exit="${PIPESTATUS[0]}"
set -e

if [[ "$codex_exit" -ne 0 ]]; then
  echo "ERROR: codex exec failed (exit ${codex_exit})" >&2
  exit 2
fi

# tr -d '\r' strips Windows-style CRLF that Codex may emit; without it the
# string comparison below silently mismatches ("VERDICT: PASS\r" != "VERDICT: PASS").
last_line="$(grep -v '^[[:space:]]*$' "$LOG" | tail -1 | tr -d '\r')"

if [[ "$last_line" != "VERDICT: PASS" && "$last_line" != "VERDICT: FAIL" ]]; then
  echo "ERROR: no valid VERDICT in review output — last line: ${last_line}" >&2
  exit 2
fi

if [[ "$last_line" == "VERDICT: FAIL" ]]; then
  exit 1
fi

exit 0

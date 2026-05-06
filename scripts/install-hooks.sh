#!/usr/bin/env bash
# Configure git to use the repo's versioned hooks directory.
# Run once per clone: ./scripts/install-hooks.sh
set -euo pipefail
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit
echo "✅ Git hooks installed. Pre-commit Codex review is active."
echo "   Requires: codex CLI (npm install -g @openai/codex) + OPENAI_API_KEY in env."
echo "   To bypass a commit: SKIP_CODEX_REVIEW=1 git commit ..."

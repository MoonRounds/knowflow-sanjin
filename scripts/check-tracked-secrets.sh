#!/bin/sh
set -eu

# High-confidence tracked-secret scan. It reports file names only, never matching content.
# This complements (not replaces) provider-side secret scanning.

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"
SCAN_FILES="$(mktemp -t knowflow-secret-scan.XXXXXX)"
trap 'rm -f "$SCAN_FILES"' EXIT HUP INT TERM

# Include tracked files and unignored candidates that would become tracked on the
# next commit. CI naturally sees the same set as tracked files.
git ls-files -z --cached --others --exclude-standard > "$SCAN_FILES"

echo "========================================="
echo " KnowFlow tracked-secret scan"
echo "========================================="

FORBIDDEN_ENV_FILES="$(
  git ls-files --cached --others --exclude-standard | awk '
    {
      count = split($0, parts, "/")
      base = parts[count]
      if (base == ".env" || (base ~ /^\.env\./ && base != ".env.example")) print $0
    }
  '
)"

if [ -n "$FORBIDDEN_ENV_FILES" ]; then
  echo "ERROR: tracked environment files are forbidden:" >&2
  echo "$FORBIDDEN_ENV_FILES" >&2
  exit 1
fi

scan_pattern() {
  label="$1"
  pattern="$2"
  # 遍历 NUL 分隔的文件列表逐个 grep，避免依赖 ripgrep 是否安装
  matches="$(
    tr '\0' '\n' < "$SCAN_FILES" | while IFS= read -r f; do
      [ -n "$f" ] || continue
      [ -f "$f" ] || continue
      if grep -lE "$pattern" "$f" >/dev/null 2>&1; then
        printf '%s\n' "$f"
      fi
    done | awk '$0 != "scripts/check-tracked-secrets.sh"'
  )"
  if [ -n "$matches" ]; then
    echo "ERROR: possible $label found in tracked files:" >&2
    echo "$matches" >&2
    exit 1
  fi
}

scan_pattern "private key" '-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----'
scan_pattern "AWS access key" '(AKIA|ASIA)[0-9A-Z]{16}'
scan_pattern "GitHub token" 'gh[pousr]_[A-Za-z0-9]{30,255}'
scan_pattern "GitHub fine-grained token" 'github_pat_[A-Za-z0-9_]{40,255}'
scan_pattern "OpenAI project API key" 'sk-(proj|svcacct)-[A-Za-z0-9_-]{32,255}'
scan_pattern "OpenAI legacy API key" 'sk-[A-Za-z0-9]{40,255}'
scan_pattern "Slack token" 'xox[baprs]-[A-Za-z0-9-]{20,255}'
scan_pattern "npm token" 'npm_[A-Za-z0-9]{30,255}'

echo "Tracked-secret scan passed."

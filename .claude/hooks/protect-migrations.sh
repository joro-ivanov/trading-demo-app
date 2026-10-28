#!/usr/bin/env bash
# PreToolUse hook: block edits to Flyway migrations that already exist. Applied migrations are
# checksummed; schema changes must go into a new V<n+1>__*.sql file instead.
input=$(cat)
file=$(printf '%s' "$input" | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)

if [[ "$file" =~ /db/migration/.*/V[0-9]+__[^/]*\.sql$ ]] && [[ -f "$file" ]]; then
  echo "Blocked: $file is an existing Flyway migration. Add a new V<n+1>__<description>.sql instead of editing it." >&2
  exit 2
fi
exit 0

#!/usr/bin/env bash
# Generates a Markdown changelog for the commits between the previous release
# tag and the given tag. Works with Conventional Commits (feat:, fix(scope):,
# feat!: ...) and falls back to "Other" for ordinary messages.
# Usage: scripts/generate-changelog.sh v1.4.0 [owner/repo] > CHANGELOG.md
# Requires full git history and tags (fetch-depth: 0).
set -euo pipefail

tag="${1:?usage: generate-changelog.sh <tag> [owner/repo]}"
repo="${2:-}"

prev="$(git describe --tags --abbrev=0 --match 'v[0-9]*' "${tag}^" 2>/dev/null || true)"
if [ -n "$prev" ]; then range="${prev}..${tag}"; else range="$tag"; fi

title_of() {
  case "$1" in
    feat) echo Features;; fix) echo Fixes;; perf) echo Performance;; refactor) echo Refactoring;;
    docs) echo Documentation;; test) echo Tests;; build) echo Build;; ci) echo CI;;
    chore) echo Chores;; *) echo Other;;
  esac
}
order="feat fix perf refactor docs test build ci chore other"
all=""
for k in $order; do eval "sec_$k="; done

while IFS=$'\x1f' read -r hash subject; do
  [ -z "$hash" ] && continue
  short="${hash:0:7}"
  if [ -n "$repo" ]; then ref="[\`$short\`](https://github.com/$repo/commit/$hash)"; else ref="\`$short\`"; fi

  type=other; msg="$subject"
  if [[ "$subject" =~ ^([a-zA-Z]+)(\([^\)]*\))?(!)?:[[:space:]]*(.+)$ ]]; then
    t="$(tr '[:upper:]' '[:lower:]' <<<"${BASH_REMATCH[1]}")"
    case " $order " in
      *" $t "*)
        if [ "$t" != other ]; then
          type="$t"
          scope="${BASH_REMATCH[2]}"; scope="${scope#(}"; scope="${scope%)}"
          msg="${BASH_REMATCH[4]}"
          [ -n "$scope" ] && msg="**$scope:** $msg"
          [ -n "${BASH_REMATCH[3]}" ] && msg="**BREAKING:** $msg"
        fi;;
    esac
  fi
  msg="$(tr '[:lower:]' '[:upper:]' <<<"${msg:0:1}")${msg:1}"
  eval "sec_$type=\"\${sec_$type}- \$msg (\$ref)\"\$'\\n'"
  all+="- $ref $subject"$'\n'
done < <(git log --no-merges --pretty=format:'%H%x1f%s' "$range")

echo "# Changelog"
echo
echo "## $tag"
echo
if [ -n "$prev" ] && [ -n "$repo" ]; then
  echo "Changes since [$prev](https://github.com/$repo/releases/tag/$prev) — [full diff](https://github.com/$repo/compare/$prev...$tag)"
  echo
fi
for k in $order; do
  eval "body=\$sec_$k"
  [ -n "$body" ] || continue
  echo "### $(title_of "$k")"
  printf '%s\n' "$body"
done
if [ -n "$all" ]; then
  echo "### Commits"
  printf '%s' "$all"
else
  echo "_No changes._"
fi

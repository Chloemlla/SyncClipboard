#!/usr/bin/env bash
set -euo pipefail

# Resolve the latest Lumen Crash SDK main auto-release.
# Prefers public GitHub Releases API; falls back to git ls-remote tags.
# Bad/expired tokens are ignored so public resolution still works.
# Prefers auto tags like 0.1.0-<shortSha> over frozen pure X.Y.Z tags.

OWNER_REPO="${OWNER_REPO:-Chloemlla/Project-Lumen}"
API="https://api.github.com/repos/${OWNER_REPO}/releases?per_page=100"
TOKEN="${GH_TOKEN:-${GITHUB_TOKEN:-}}"

resolve_via_api() {
  local use_auth="$1"
  local headers=(-H "Accept: application/vnd.github+json" -H "User-Agent: lumen-crash-version-resolver")
  if [ "$use_auth" = "1" ] && [ -n "${TOKEN}" ]; then
    headers+=(-H "Authorization: Bearer ${TOKEN}")
  fi

  if ! command -v jq >/dev/null 2>&1; then
    return 1
  fi

  local body
  if ! body="$(curl -fsSL "${headers[@]}" "$API" 2>/dev/null)"; then
    return 1
  fi

  # Prefer auto-release tags (X.Y.Z-sha) by published_at, then fall back to any lumen-crash-v tag.
  jq -r '
      def ver: sub("^lumen-crash-v"; "");
      def is_auto: test("^[0-9]+\\.[0-9]+\\.[0-9]+-[0-9a-fA-F]{7,}$");
      [.[]
        | select(.draft == false)
        | select(.tag_name | startswith("lumen-crash-v"))
        | {version: (.tag_name | ver), published: (.published_at // .created_at), auto: ((.tag_name | ver) | is_auto)}
      ] as $all
      | (
          [$all[] | select(.auto)]
          | sort_by(.published)
          | reverse
          | .[0].version
        ) // (
          $all
          | sort_by(.published)
          | reverse
          | .[0].version
        )
    ' <<<"$body"
}

resolve_via_git_tags() {
  local lines versions auto
  lines="$(git ls-remote --tags "https://github.com/${OWNER_REPO}.git" "refs/tags/lumen-crash-v*" 2>/dev/null || true)"
  versions="$(
    printf '%s\n' "$lines" \
      | awk '{print $2}' \
      | sed -e 's#^refs/tags/##' -e 's#\^{}$##' \
      | grep -E '^lumen-crash-v' \
      | sed 's/^lumen-crash-v//' \
      | sort -u
  )"
  auto="$(printf '%s\n' "$versions" | grep -E '^[0-9]+\.[0-9]+\.[0-9]+-[0-9a-fA-F]{7,}$' || true)"
  if [ -n "$auto" ]; then
    printf '%s\n' "$auto" | sort -r | head -n 1
    return 0
  fi
  printf '%s\n' "$versions" | sort -r | head -n 1
}

VERSION=""
if [ -n "${TOKEN}" ]; then
  VERSION="$(resolve_via_api 1 || true)"
fi
if [ -z "${VERSION}" ] || [ "${VERSION}" = "null" ]; then
  VERSION="$(resolve_via_api 0 || true)"
fi
if [ -z "${VERSION}" ] || [ "${VERSION}" = "null" ]; then
  VERSION="$(resolve_via_git_tags || true)"
fi

if [ -z "${VERSION}" ] || [ "${VERSION}" = "null" ]; then
  echo "No lumen-crash release found" >&2
  exit 1
fi

echo "$VERSION"
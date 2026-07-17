#!/usr/bin/env bash
set -euo pipefail

# Download a Lumen Crash SDK GitHub Release into a local Maven-style repo tree.
# Uses public release assets; no GitHub Packages auth required.
#
# Usage:
#   ./scripts/sync-lumen-crash-release-maven.sh [version] [output-dir]

OWNER_REPO="${OWNER_REPO:-Chloemlla/Project-Lumen}"
VERSION="${1:-}"
OUT_DIR="${2:-.m2-lumen-crash}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -z "$VERSION" ]; then
  if [ -f "${SCRIPT_DIR}/resolve-lumen-crash-latest.sh" ]; then
    VERSION="$(bash "${SCRIPT_DIR}/resolve-lumen-crash-latest.sh")"
  else
    echo "Version argument required (or provide resolve-lumen-crash-latest.sh)." >&2
    exit 1
  fi
fi

TAG="lumen-crash-v${VERSION}"
API="https://api.github.com/repos/${OWNER_REPO}/releases/tags/${TAG}"
TOKEN="${GH_TOKEN:-${GITHUB_TOKEN:-}}"

fetch_release_json() {
  local use_auth="$1"
  local headers=(-H "Accept: application/vnd.github+json" -H "User-Agent: lumen-crash-release-sync")
  if [ "$use_auth" = "1" ] && [ -n "${TOKEN}" ]; then
    headers+=(-H "Authorization: Bearer ${TOKEN}")
  fi
  curl -fsSL "${headers[@]}" "$API"
}

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

if command -v jq >/dev/null 2>&1; then
  if [ -n "${TOKEN}" ]; then
    fetch_release_json 1 > "$TMP_DIR/release.json" 2>/dev/null || true
  fi
  if [ ! -s "$TMP_DIR/release.json" ]; then
    fetch_release_json 0 > "$TMP_DIR/release.json" 2>/dev/null || true
  fi
fi

BUNDLE_DIR="${OUT_DIR}/com/chloemlla/lumen/lumen-crash/${VERSION}"
CORE_DIR="${OUT_DIR}/com/chloemlla/lumen/lumen-crash-core/${VERSION}"
mkdir -p "$BUNDLE_DIR" "$CORE_DIR" "$TMP_DIR/assets"
DOWNLOADED=0

if [ -s "$TMP_DIR/release.json" ] && command -v jq >/dev/null 2>&1; then
  mapfile -t ASSET_LINES < <(
    jq -r '.assets[] | select(.name|test("\\.(aar|pom|module|jar)$")) | "\(.name)\t\(.browser_download_url)"' \
      "$TMP_DIR/release.json"
  )
  for line in "${ASSET_LINES[@]:-}"; do
    [ -z "${line:-}" ] && continue
    name="${line%%$'\t'*}"
    url="${line#*$'\t'}"
    dest="$TMP_DIR/assets/$name"
    echo "Downloading $name"
    curl -fsSL -L "$url" -o "$dest"
    case "$name" in
      lumen-crash-core-*) cp "$dest" "$CORE_DIR/" ;;
      *) cp "$dest" "$BUNDLE_DIR/" ;;
    esac
    DOWNLOADED=$((DOWNLOADED + 1))
  done
fi

if [ "$DOWNLOADED" -eq 0 ]; then
  # Fallback without Releases API: conventional Maven publish asset names on the release tag.
  BASE="https://github.com/${OWNER_REPO}/releases/download/${TAG}"
  for name in \
    "lumen-crash-${VERSION}.aar" \
    "lumen-crash-${VERSION}.pom" \
    "lumen-crash-${VERSION}.module" \
    "lumen-crash-${VERSION}-sources.jar" \
    "lumen-crash-core-${VERSION}.aar" \
    "lumen-crash-core-${VERSION}.pom" \
    "lumen-crash-core-${VERSION}.module" \
    "lumen-crash-core-${VERSION}-sources.jar"
  do
    dest="$TMP_DIR/assets/$name"
    if curl -fsSL -L "$BASE/$name" -o "$dest"; then
      echo "Downloading $name (fallback)"
      case "$name" in
        lumen-crash-core-*) cp "$dest" "$CORE_DIR/" ;;
        *) cp "$dest" "$BUNDLE_DIR/" ;;
      esac
      DOWNLOADED=$((DOWNLOADED + 1))
    else
      echo "Skip missing asset $name"
    fi
  done
fi

if [ "$DOWNLOADED" -eq 0 ]; then
  echo "No Maven assets found for tag ${TAG}" >&2
  exit 1
fi

echo "Synced release ${VERSION} into ${OUT_DIR} (${DOWNLOADED} files)"
echo "Gradle repo example:"
echo "  maven { url = uri(\"file://\$(pwd)/${OUT_DIR}\") }"
echo "  implementation(\"com.chloemlla.lumen:lumen-crash:${VERSION}\")"
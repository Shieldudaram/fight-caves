#!/usr/bin/env bash
set -euo pipefail

FETCH="true"
if [[ "${1:-}" == "--no-fetch" ]]; then
  FETCH="false"
fi

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [[ -z "${ROOT}" ]]; then
  echo "ERROR: Not inside a git repository." >&2
  exit 1
fi
cd "${ROOT}"

if [[ "${FETCH}" == "true" ]]; then
  git fetch origin --prune >/dev/null
fi

DEV_SHA="$(git rev-parse origin/dev)"
MAIN_SHA="$(git rev-parse origin/main)"

COUNTS="$(git rev-list --left-right --count origin/dev...origin/main)"
LEFT_COUNT="${COUNTS%%$'\t'*}"
RIGHT_COUNT="${COUNTS##*$'\t'}"

DIFF_OUTPUT="$(git diff --name-status origin/dev..origin/main)"
NON_MERGE_MAIN="$(git rev-list --right-only --no-merges --oneline origin/dev...origin/main)"
MERGE_MAIN="$(git rev-list --right-only --merges --oneline origin/dev...origin/main)"

echo "Pointers:"
echo "  origin/dev  = ${DEV_SHA}"
echo "  origin/main = ${MAIN_SHA}"
echo "Ahead/behind (origin/dev...origin/main): left=${LEFT_COUNT} right=${RIGHT_COUNT}"
echo

if [[ -z "${DIFF_OUTPUT}" ]]; then
  echo "Content diff (origin/dev..origin/main): empty"
else
  echo "Content diff (origin/dev..origin/main):"
  echo "${DIFF_OUTPUT}"
fi
echo

if [[ -z "${NON_MERGE_MAIN}" ]]; then
  echo "Main-only non-merge commits: none"
else
  echo "Main-only non-merge commits:"
  echo "${NON_MERGE_MAIN}"
fi
echo

if [[ -z "${MERGE_MAIN}" ]]; then
  echo "Main-only merge commits: none"
else
  echo "Main-only merge commits:"
  echo "${MERGE_MAIN}"
fi
echo

if [[ -z "${DIFF_OUTPUT}" && -z "${NON_MERGE_MAIN}" ]]; then
  echo "Decision: HEALTHY (content-equivalent; merge-commit skew is expected)."
  exit 0
fi

if [[ "${LEFT_COUNT}" -gt 0 && -z "${NON_MERGE_MAIN}" ]]; then
  echo "Decision: dev has unreleased work. Open/merge PR dev -> main."
  exit 1
fi

if [[ -n "${NON_MERGE_MAIN}" && "${LEFT_COUNT}" -eq 0 ]]; then
  echo "Decision: main has non-merge commits not in dev. Open back-merge PR main -> dev."
  exit 1
fi

if [[ "${LEFT_COUNT}" -gt 0 && -n "${NON_MERGE_MAIN}" ]]; then
  echo "Decision: divergent branches with real drift."
  echo "Action: merge main -> dev first, then promote dev -> main."
  exit 1
fi

echo "Decision: mixed state detected. Review output and apply main->dev then dev->main sequence."
exit 1

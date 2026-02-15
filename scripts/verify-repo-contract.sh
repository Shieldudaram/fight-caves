#!/usr/bin/env bash
set -euo pipefail

EXPECTED_REPO="${EXPECTED_REPO:-Shieldudaram/fight-caves}"
EXPECTED_DEFAULT_BRANCH="${EXPECTED_DEFAULT_BRANCH:-dev}"
EXPECTED_ORIGIN_PATTERN='^(https://([^@/]+@)?github\.com/Shieldudaram/fight-caves(\.git)?|git@github\.com:Shieldudaram/fight-caves\.git|ssh://git@github\.com/Shieldudaram/fight-caves\.git)$'

EXPECTED_DEV_CONTEXTS=(ciCheck scripts-boundary)
EXPECTED_MAIN_CONTEXTS=(ciCheck scripts-boundary main-release-path)

REQUIRED_WORKFLOWS=(
  ".github/workflows/ci.yml"
  ".github/workflows/repo-boundaries.yml"
)

if command -v gh >/dev/null 2>&1; then
  GH_BIN="$(command -v gh)"
elif [[ -x "/opt/homebrew/bin/gh" ]]; then
  GH_BIN="/opt/homebrew/bin/gh"
elif [[ -x "/usr/local/bin/gh" ]]; then
  GH_BIN="/usr/local/bin/gh"
else
  echo "ERROR: GitHub CLI (gh) not found in PATH." >&2
  exit 1
fi

fail() {
  echo "ERROR: $1" >&2
  exit 1
}

normalize_list() {
  tr ' ' '\n' | sed '/^$/d' | sort -u | paste -sd, -
}

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [[ -z "${REPO_ROOT}" ]]; then
  fail "Not inside a git repository."
fi
cd "${REPO_ROOT}"

origin_url="$(git remote get-url origin 2>/dev/null || true)"
if [[ -z "${origin_url}" ]]; then
  fail "Remote 'origin' is missing."
fi
if [[ ! "${origin_url}" =~ ${EXPECTED_ORIGIN_PATTERN} ]]; then
  fail "origin URL mismatch. got='${origin_url}' expected repo='${EXPECTED_REPO}'."
fi

dev_upstream="$(git for-each-ref --format='%(upstream:short)' refs/heads/dev)"
main_upstream="$(git for-each-ref --format='%(upstream:short)' refs/heads/main)"
[[ "${dev_upstream}" == "origin/dev" ]] || fail "Local dev branch is not tracking origin/dev."
[[ "${main_upstream}" == "origin/main" ]] || fail "Local main branch is not tracking origin/main."

repo_name="$("${GH_BIN}" repo view "${EXPECTED_REPO}" --json nameWithOwner --jq '.nameWithOwner' || true)"
[[ "${repo_name}" == "${EXPECTED_REPO}" ]] || fail "Cannot resolve expected repo '${EXPECTED_REPO}'. Ensure gh is authenticated or GH_TOKEN is set."

default_branch="$("${GH_BIN}" repo view "${EXPECTED_REPO}" --json defaultBranchRef --jq '.defaultBranchRef.name')"
[[ "${default_branch}" == "${EXPECTED_DEFAULT_BRANCH}" ]] || fail "Default branch mismatch. expected='${EXPECTED_DEFAULT_BRANCH}' got='${default_branch}'."

for file in "${REQUIRED_WORKFLOWS[@]}"; do
  [[ -f "${file}" ]] || fail "Required workflow missing: ${file}"
done

grep -Eq '^name:\s*ciCheck\s*$' .github/workflows/ci.yml || fail "ci.yml workflow name must be 'ciCheck'."
grep -Eq '^[[:space:]]+ciCheck:\s*$' .github/workflows/ci.yml || fail "ci.yml must contain job id 'ciCheck'."

grep -Eq '^[[:space:]]+scripts-boundary:\s*$' .github/workflows/repo-boundaries.yml || fail "repo-boundaries.yml must contain job id 'scripts-boundary'."
grep -Eq '^[[:space:]]+main-release-path:\s*$' .github/workflows/repo-boundaries.yml || fail "repo-boundaries.yml must contain job id 'main-release-path'."
grep -Eq 'github\.base_ref == '\''main'\''' .github/workflows/repo-boundaries.yml || fail "main-release-path gate condition for PR base main is missing."
grep -Eq 'github\.head_ref.+!=.+dev' .github/workflows/repo-boundaries.yml || fail "main-release-path gate must reject non-dev sources."

expected_dev="$(printf '%s\n' "${EXPECTED_DEV_CONTEXTS[@]}" | normalize_list)"
expected_main="$(printf '%s\n' "${EXPECTED_MAIN_CONTEXTS[@]}" | normalize_list)"

actual_dev="$("${GH_BIN}" api "repos/${EXPECTED_REPO}/branches/dev/protection" --jq '.required_status_checks.contexts[]' | normalize_list || true)"
actual_main="$("${GH_BIN}" api "repos/${EXPECTED_REPO}/branches/main/protection" --jq '.required_status_checks.contexts[]' | normalize_list || true)"

[[ "${actual_dev}" == "${expected_dev}" ]] || fail "dev protection contexts mismatch. expected='${expected_dev}' got='${actual_dev}'."
[[ "${actual_main}" == "${expected_main}" ]] || fail "main protection contexts mismatch. expected='${expected_main}' got='${actual_main}'."

echo "OK: Fight Caves repo contract is compliant for ${EXPECTED_REPO}."

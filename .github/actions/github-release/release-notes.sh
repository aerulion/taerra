#!/usr/bin/env bash
#
# release-notes.sh <tag-prefix> <tag>
#
# Renders one stream's release notes from the commit log, on stdout. Needs GH_TOKEN, GITHUB_REPOSITORY,
# GITHUB_SERVER_URL and a checkout with history and tags.

set -euo pipefail

prefix="${1:?a tag prefix is required}"
tag="${2:?a tag is required}"

# The pathspecs below are relative.
cd "$(git rev-parse --show-toplevel)"

# Must stay in sync with .github/actions/classify-changes.
case "${prefix}" in
  build-) paths=( '.' ':(exclude)mod' ':(exclude)*.md' ) ;;
  mod-) paths=( 'mod' 'gradle' 'build-logic' 'settings.gradle.kts' 'build.gradle.kts' 'gradle.properties' 'gradlew' 'gradlew.bat' ':(exclude)*.md' ) ;;
  *)
    echo "::error::Unknown tag prefix '${prefix}'." >&2
    exit 1
    ;;
esac

# Ancestry, not tag dates: dates tie, and a tie picks the oldest. --exclude so a re-run is not its own base.
previous="$(git describe --tags --abbrev=0 --match "${prefix}*" --exclude "${tag}" HEAD 2>/dev/null || true)"
previous_commit=''
if [ -n "${previous}" ]; then
  previous_commit="$(git rev-parse "${previous}^{commit}")"
fi

changelog_link() {
  if [ -n "${previous}" ]; then
    printf '**Full Changelog**: %s/%s/compare/%s...%s\n' \
      "${GITHUB_SERVER_URL}" "${GITHUB_REPOSITORY}" "${previous}" "${tag}"
  else
    printf '**Full Changelog**: %s/%s/commits/%s\n' "${GITHUB_SERVER_URL}" "${GITHUB_REPOSITORY}" "${tag}"
  fi
}

# The first release of a stream would otherwise enumerate the whole history, merged subhistories included.
if [ -z "${previous_commit}" ]; then
  printf 'First tracked release of this stream.\n\n'
  changelog_link
  exit 0
fi

head_commit="$(git rev-parse HEAD)"
commits="$(git log --no-merges --format='%H%x09%an%x09%s' "${previous_commit}..HEAD" -- "${paths[@]}")"

# A release body is capped at 125,000 characters.
maximum=300
total="$(grep -c . <<< "${commits}" || true)"
omitted=0
if [ "${total}" -gt "${maximum}" ]; then
  omitted=$((total - maximum))
  commits="$(head -n "${maximum}" <<< "${commits}")"
fi

# Credit by GitHub handle; a commit the compare cannot reach falls back to the git author name.
logins="$(gh api --paginate \
  "repos/${GITHUB_REPOSITORY}/compare/${previous_commit}...${head_commit}" \
  --jq '.commits[] | [.sha, (.author.login // "")] | @tsv' 2>/dev/null || true)"

login_for() {
  local sha="$1" line
  line="$(grep -m1 "^${sha}	" <<< "${logins}" || true)"
  printf '%s' "${line#*	}"
}

# ':sparkles: feat(scope)!: Added a thing' -> ':sparkles:', 'feat', 'Added a thing'.
emoji=''
type=''
summary=''
parse_subject() {
  emoji=''
  type=''
  summary="$1"
  if [[ "${summary}" =~ ^(:[a-z0-9_+-]+:)[[:space:]]+(.*)$ ]]; then
    emoji="${BASH_REMATCH[1]}"
    summary="${BASH_REMATCH[2]}"
  fi
  if [[ "${summary}" =~ ^([a-z]+)(\([^\)]*\))?!?:[[:space:]]+(.*)$ ]]; then
    type="${BASH_REMATCH[1]}"
    summary="${BASH_REMATCH[3]}"
  fi
}

# The closed set of types the commit-msg hook allows; anything else lands under "other".
section_of() {
  case "$1" in
    feat | fix | perf | refactor | docs | test | build | ci | deps | chore | revert) printf '%s' "$1" ;;
    *) printf 'other' ;;
  esac
}

heading_of() {
  case "$1" in
    feat) printf ':sparkles: Features' ;;
    fix) printf ':bug: Fixes' ;;
    perf) printf ':zap: Performance' ;;
    refactor) printf ':recycle: Refactoring' ;;
    docs) printf ':memo: Documentation' ;;
    test) printf ':white_check_mark: Tests' ;;
    build) printf ':construction_worker: Build' ;;
    ci) printf ':green_heart: CI' ;;
    deps) printf ':arrow_up: Dependencies' ;;
    chore) printf ':wrench: Chores' ;;
    revert) printf ':rewind: Reverts' ;;
    *) printf ':package: Other' ;;
  esac
}

entries=''
credits=''
count=0
while IFS=$'\t' read -r sha name subject; do
  if [ -z "${sha}" ]; then
    continue
  fi
  count=$((count + 1))
  parse_subject "${subject}"
  login="$(login_for "${sha}")"
  if [ -n "${login}" ]; then
    credit="@${login}"
  else
    credit="${name}"
  fi
  line="* "
  if [ -n "${emoji}" ]; then
    line="${line}${emoji} "
  fi
  line="${line}${summary} by ${credit} in ${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/commit/${sha}"
  entries="${entries}$(section_of "${type}")	${line}"$'\n'
  credits="${credits}${credit}"$'\n'
done <<< "${commits}"

if [ "${count}" -eq 0 ]; then
  printf 'No changes to this part of the repository.\n\n'
else
  printf '## What'"'"'s Changed\n'
  for section in feat fix perf refactor docs test build ci deps chore revert other; do
    body="$(grep "^${section}	" <<< "${entries}" | cut -f2- || true)"
    if [ -n "${body}" ]; then
      printf '\n### %s\n%s\n' "$(heading_of "${section}")" "${body}"
    fi
  done

  if [ "${omitted}" -gt 0 ]; then
    printf '\n_... and %s further commits._\n' "${omitted}"
  fi

  contributors="$(grep -v '^$' <<< "${credits}" | sort -u | sed 's/^/* /')"
  if [ -n "${contributors}" ]; then
    printf '\n### :busts_in_silhouette: Contributors\n%s\n' "${contributors}"
  fi
  printf '\n'
fi

changelog_link

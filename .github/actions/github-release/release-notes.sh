#!/usr/bin/env bash

set -euo pipefail

# Usage: release-notes.sh <prefix> <tag> [previous]
# Writes the notes for HEAD since [previous], a tag or commit, or else since the newest <prefix> tag HEAD descends from.
# With AUDIENCE=players only features, fixes and performance are listed, without any link into the repository: the
# notes Modrinth and Discord show, while the repository is private.

prefix="${1:?a tag prefix is required}"
tag="${2:?a tag is required}"
previous="${3:-}"
audience="${AUDIENCE:-contributors}"

cd "$(git rev-parse --show-toplevel)"

case "${prefix}" in
  plugin-) paths=( '.' ':(exclude)mod' ':(exclude)branding' ':(exclude)site' ':(exclude).github/actions/build-site' ':(exclude)*.md' ) ;;
  mod-) paths=( 'mod' 'gradle' 'build-logic' 'settings.gradle.kts' 'build.gradle.kts' 'gradle.properties' 'gradlew' 'gradlew.bat' ':(exclude)*.md' ) ;;
  *)
    echo "::error::Unknown tag prefix '${prefix}'." >&2
    exit 1
    ;;
esac

if [ -z "${previous}" ]; then
  previous="$(git describe --tags --abbrev=0 --match "${prefix}*" --exclude "${tag}" HEAD 2>/dev/null || true)"
fi
previous_commit=''
if [ -n "${previous}" ]; then
  previous_commit="$(git rev-parse "${previous}^{commit}")"
fi

changelog_link() {
  if [ "${audience}" = 'players' ]; then
    return
  fi
  if [ -n "${previous}" ]; then
    printf '**Full Changelog**: %s/%s/compare/%s...%s\n' \
      "${GITHUB_SERVER_URL}" "${GITHUB_REPOSITORY}" "${previous}" "${tag}"
  else
    printf '**Full Changelog**: %s/%s/commits/%s\n' "${GITHUB_SERVER_URL}" "${GITHUB_REPOSITORY}" "${tag}"
  fi
}

if [ -z "${previous_commit}" ]; then
  printf 'First tracked release of this stream.\n\n'
  changelog_link
  exit 0
fi

head_commit="$(git rev-parse HEAD)"
commits="$(git log --no-merges --format='%H%x09%an%x09%s' "${previous_commit}..HEAD" -- "${paths[@]}")"

maximum=300
total="$(grep -c . <<< "${commits}" || true)"
omitted=0
if [ "${total}" -gt "${maximum}" ]; then
  omitted=$((total - maximum))
  commits="$(head -n "${maximum}" <<< "${commits}")"
fi

logins="$(gh api --paginate \
  "repos/${GITHUB_REPOSITORY}/compare/${previous_commit}...${head_commit}" \
  --jq '.commits[] | [.sha, (.author.login // "")] | @tsv' 2>/dev/null || true)"

login_for() {
  local sha="$1" line
  line="$(grep -m1 "^${sha}	" <<< "${logins}" || true)"
  printf '%s' "${line#*	}"
}

type=''
summary=''
parse_subject() {
  type=''
  summary="$1"
  if [[ "${summary}" =~ ^:[a-z0-9_+-]+:[[:space:]]+(.*)$ ]]; then
    summary="${BASH_REMATCH[1]}"
  fi
  if [[ "${summary}" =~ ^([a-z]+)(\([^\)]*\))?!?:[[:space:]]+(.*)$ ]]; then
    type="${BASH_REMATCH[1]}"
    summary="${BASH_REMATCH[3]}"
  fi
}

section_of() {
  case "$1" in
    feat | fix | perf | refactor | docs | test | build | ci | deps | chore | revert) printf '%s' "$1" ;;
    *) printf 'other' ;;
  esac
}

heading_of() {
  case "$1" in
    feat) printf '✨ Features' ;;
    fix) printf '🐛 Fixes' ;;
    perf) printf '⚡ Performance' ;;
    refactor) printf '♻️ Refactoring' ;;
    docs) printf '📝 Documentation' ;;
    test) printf '✅ Tests' ;;
    build) printf '👷 Build' ;;
    ci) printf '💚 CI' ;;
    deps) printf '⬆️ Dependencies' ;;
    chore) printf '🔧 Chores' ;;
    revert) printf '⏪ Reverts' ;;
    *) printf '📦 Other' ;;
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
  # Unicode, not the commit's shortcode: Modrinth and Discord embeds show shortcodes as text. The heading carries it.
  line="* ${summary}"
  if [ "${audience}" != 'players' ]; then
    line="${line} by ${credit} in ${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/commit/${sha}"
  fi
  entries="${entries}$(section_of "${type}")	${line}"$'\n'
  credits="${credits}${credit}"$'\n'
done <<< "${commits}"

sections='feat fix perf refactor docs test build ci deps chore revert other'
if [ "${audience}" = 'players' ]; then
  sections='feat fix perf'
  if ! grep -qE '^(feat|fix|perf)	' <<< "${entries}"; then
    count=0
    printf 'Internal improvements only.\n\n'
  fi
fi

if [ "${count}" -eq 0 ]; then
  if [ "${audience}" != 'players' ]; then
    printf 'No changes to this part of the repository.\n\n'
  fi
else
  printf '## What'"'"'s Changed\n'
  for section in ${sections}; do
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
    printf '\n### 👥 Contributors\n%s\n' "${contributors}"
  fi
  printf '\n'
fi

changelog_link

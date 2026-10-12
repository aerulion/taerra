#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

repo="${GITHUB_REPOSITORY:-$(gh repo view --json nameWithOwner --jq .nameWithOwner)}"
rm -rf downloads
mkdir -p downloads/records

tags="$(gh api --paginate "repos/${repo}/releases?per_page=100" \
  --jq '.[] | select(any(.assets[]; .name == "modrinth.json")) | .tag_name')"

for tag in ${tags}; do
  record="downloads/records/${tag}.json"
  gh release download "${tag}" --repo "${repo}" --pattern modrinth.json --output "${record}"
  file="$(jq -r .file "${record}")"
  gh release download "${tag}" --repo "${repo}" --pattern "${file}" --dir downloads
  actual="$(shasum -a 512 "downloads/${file}" | cut -d ' ' -f 1)"
  if [ "${actual}" != "$(jq -r .sha512 "${record}")" ]; then
    echo "::error::${file} in ${tag} does not match the hash Modrinth has for it."
    exit 1
  fi
done

listed=false
if curl --silent --fail --output /dev/null --max-time 30 --user-agent 'aerulion/taerra website (taerra.dev)' \
  https://api.modrinth.com/v2/project/taerra; then
  listed=true
fi

if [ -z "${tags}" ]; then
  echo '[]' > downloads/records.json
else
  jq -s . downloads/records/*.json > downloads/records.json
fi
jq --argjson listed "${listed}" '{modrinth: $listed, builds: (sort_by(.published) | reverse)}' \
  downloads/records.json > downloads/index.json
rm -r downloads/records downloads/records.json
echo "Mirrored $(jq '.builds | length' downloads/index.json) builds, Modrinth page listed: ${listed}."

#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

rm -rf downloads
mkdir -p downloads

curl --silent --show-error --fail-with-body --retry 3 --max-time 60 \
  --user-agent 'aerulion/taerra website (taerra.dev)' https://api.modrinth.com/v2/project/taerra/version \
  | jq '{builds: [.[] | {
      part: (if .loaders | index("paper") then "plugin" else "mod" end),
      build: (.version_number | split("-")[0] | tonumber),
      version: .version_number,
      name: .name,
      channel: .version_type,
      minecraft: .game_versions,
      published: .date_published,
      changelog: .changelog,
      modrinth: .id
    }] | sort_by(.published) | reverse}' > downloads/index.json
echo "Listed $(jq '.builds | length' downloads/index.json) builds from Modrinth."

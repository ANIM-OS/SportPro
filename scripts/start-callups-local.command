#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
emulator_dir="$repo_dir/build/callups-emulator"
data_dir="$emulator_dir/data"
mkdir -p "$emulator_dir"
cd "$emulator_dir"

args=(emulators:start --project demo-sportpro --config "$repo_dir/firebase.local.json" --only auth,firestore)
if [[ -f "$data_dir/firebase-export-metadata.json" ]]; then
  args+=(--import "$data_dir")
fi
args+=(--export-on-exit "$data_dir")

exec npx --yes firebase-tools@15.33.0 "${args[@]}"

#!/bin/zsh
set -eu
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export PATH="$JAVA_HOME/bin:$PATH"
# Keep emulator logs in build/, outside the tracked source files.
mkdir -p "$repo_dir/build/callups-emulator"
cd "$repo_dir/build/callups-emulator"
if ! command -v node >/dev/null; then
  runtime_node="$HOME/Library/Caches/Google/AndroidStudio2026.2.1/acp-agents/.runtimes/node/24.13.0/bin"
  export PATH="$runtime_node:$PATH"
fi
exec npx --yes firebase-tools@15.33.0 emulators:start --project demo-sportpro --config "$repo_dir/firebase.local.json" --only auth,firestore --import "$repo_dir/build/callups-emulator/data" --export-on-exit "$repo_dir/build/callups-emulator/data"

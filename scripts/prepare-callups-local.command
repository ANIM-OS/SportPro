#!/bin/zsh
set -eu
cd "$(dirname "$0")/.."
if ! command -v node >/dev/null; then
  export PATH="$HOME/Library/Caches/Google/AndroidStudio2026.2.1/acp-agents/.runtimes/node/24.13.0/bin:$PATH"
fi
adb_bin="$HOME/Library/Android/sdk/platform-tools/adb"
"$adb_bin" reverse tcp:9099 tcp:9099
"$adb_bin" reverse tcp:8080 tcp:8080
node tests/callups/lineup-check.mjs
printf '\nListo. Selecciona la variante local en Android Studio y pulsa Run.\n'
read -r 'reply?Pulsa Enter para cerrar.'

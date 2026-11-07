#!/bin/zsh
set -eu
cd "$(dirname "$0")/.."
if ! command -v node >/dev/null; then
  export PATH="$HOME/Library/Caches/Google/AndroidStudio2026.2.1/acp-agents/.runtimes/node/24.13.0/bin:$PATH"
fi
node tests/callups/lineup-check.mjs
printf '\nPruebas finalizadas en Firebase Emulator. SportPro usa el Firebase compartido; estos datos no aparecen en la app.\n'
read -r 'reply?Pulsa Enter para cerrar.'

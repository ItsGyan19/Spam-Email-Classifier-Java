#!/usr/bin/env bash
# Spam Email Classifier CLI launcher
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

if [ $# -eq 0 ]; then
    mvn -q compile exec:java -Dexec.args="--help"
else
    mvn -q compile exec:java -Dexec.args="$*"
fi

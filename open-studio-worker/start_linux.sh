#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
command -v python3 >/dev/null 2>&1 || { echo 'Python 3 is required.'; exit 1; }
echo 'Starting Documentary Studio V7 Open Worker...'
exec python3 server.py --host 0.0.0.0 --port 8190

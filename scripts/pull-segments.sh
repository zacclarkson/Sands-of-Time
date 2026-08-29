#!/usr/bin/env bash
#
# Pull saved SoT segment templates off the dev server into the repo so they can be
# version-controlled and bundled into the plugin jar.
#
# It copies every <name>.json + schematics/<name>.schem from the server's plugin data
# folder into src/main/resources/bundled_segments/, then regenerates manifest.txt from
# the .json files present. Commit the result; onEnable auto-installs them on any server.
#
# Usage:
#   scripts/pull-segments.sh
#
# Config (env vars, with defaults for the homelab sot-test server):
#   SOT_SERVER      ssh target            (default: zac@192.168.1.250; Tailscale: 100.125.118.2)
#   SOT_REMOTE_DIR  remote SoT data dir   (default: ~/servers/sot-test/data/plugins/SoT)
#
set -euo pipefail

SOT_SERVER="${SOT_SERVER:-zac@192.168.1.250}"
SOT_REMOTE_DIR="${SOT_REMOTE_DIR:-~/servers/sot-test/data/plugins/SoT}"

# Resolve repo root from this script's location so it works from any CWD.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEST="$REPO_ROOT/src/main/resources/bundled_segments"

mkdir -p "$DEST/schematics"

echo "Pulling segments from $SOT_SERVER:$SOT_REMOTE_DIR ..."
scp "$SOT_SERVER:$SOT_REMOTE_DIR/"*.json                "$DEST/"
scp "$SOT_SERVER:$SOT_REMOTE_DIR/schematics/"*.schem    "$DEST/schematics/"

# Regenerate the manifest from the .json files now present (base names, no extension).
( cd "$DEST" && ls *.json | sed 's/\.json$//' > manifest.txt )

echo "Done. bundled_segments now contains:"
( cd "$DEST" && printf '  %s\n' *.json schematics/*.schem )
echo "manifest.txt:"
sed 's/^/  /' "$DEST/manifest.txt"

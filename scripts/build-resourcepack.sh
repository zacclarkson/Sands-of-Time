#!/usr/bin/env bash
#
# Build the slim Sands of Time resource pack zip that gets served to clients.
#
# It zips the contents of resourcepack/ (so pack.mcmeta sits at the zip ROOT, as Minecraft
# requires) while EXCLUDING the sot-source-textures/ art master and any README.md — those are
# repo-only working files, not shipped assets. The output is target/sot-resourcepack.zip.
#
# The dev-server CD job (.github/workflows/resourcepack-deploy.yml) runs this and copies the zip
# onto the `pack` nginx sidecar (deploy/sot-test/compose.yml). No SHA1 is computed on purpose: the
# server relays a fixed URL and clients re-download on their next join, so a hot file-swap needs no
# server restart.
#
# Usage:
#   scripts/build-resourcepack.sh            # -> target/sot-resourcepack.zip
#   OUT=/path/to/sot.zip scripts/build-resourcepack.sh
#
set -euo pipefail

# Resolve repo root from this script's location so it works from any cwd.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACK_DIR="$REPO_ROOT/resourcepack"
OUT="${OUT:-$REPO_ROOT/target/sot-resourcepack.zip}"

if [[ ! -f "$PACK_DIR/pack.mcmeta" ]]; then
  echo "error: $PACK_DIR/pack.mcmeta not found — is this the repo root?" >&2
  exit 1
fi

mkdir -p "$(dirname "$OUT")"
rm -f "$OUT"

# Zip from inside the pack dir so paths are relative to it (pack.mcmeta at the root).
# Prefer Info-ZIP `zip`; fall back to Python's zipfile where `zip` isn't installed (e.g. Git Bash
# on Windows). Both apply the same excludes.
if command -v zip >/dev/null 2>&1; then
  ( cd "$PACK_DIR" && zip -r -q "$OUT" . \
      -x 'sot-source-textures/*' \
      -x '*/README.md' \
      -x 'README.md' )
else
  PY="$(command -v python3 || command -v python || true)"
  if [[ -z "$PY" ]]; then
    echo "error: need either 'zip' or 'python3' on PATH to build the pack" >&2
    exit 1
  fi
  "$PY" - "$PACK_DIR" "$OUT" <<'PYEOF'
import os, sys, zipfile
pack_dir, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zf:
    for root, dirs, files in os.walk(pack_dir):
        rel_root = os.path.relpath(root, pack_dir)
        if rel_root == ".":
            rel_root = ""
        # Exclude the art master entirely.
        if rel_root.split(os.sep)[0] == "sot-source-textures":
            dirs[:] = []
            continue
        for name in sorted(files):
            if name == "README.md":
                continue
            arcname = "/".join(filter(None, [rel_root.replace(os.sep, "/"), name]))
            zf.write(os.path.join(root, name), arcname)
PYEOF
fi

echo "Built $OUT"
# Print a SHA1 for reference/debugging only; the delivery path does not use it.
if command -v sha1sum >/dev/null 2>&1; then
  echo "sha1: $(sha1sum "$OUT" | awk '{print $1}')"
fi

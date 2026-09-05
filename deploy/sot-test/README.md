# Test/dev server (`sot-test`)

An always-on Paper 26.2 + WorldEdit server, running on a Docker host, that runs the current build of
the SoT plugin so you can log in from a real Minecraft client and test the UX (builder tools and, via
`/sot`, a full game round).

- **Connect:** `<SERVER_HOST>:25700` from your Minecraft client. This is a raw game port (not HTTP),
  so expose it only on a trusted network (LAN/VPN), not the public internet.
- **Requires:** Paper 26.2, Java 25, WorldEdit 7.4.5+ (auto-installed). All provided by the
  `itzg/minecraft-server:java25` image + `MODRINTH_PROJECTS: worldedit,plugmanx` (plus
  `MODRINTH_ALLOWED_VERSION_TYPE: beta`, since PlugManX's Paper-26.2 build is on the beta channel).
  **PlugManX** is used to hot-reload just the SoT plugin without a full restart.

`compose.yml` here is the version-controlled reference; deploy a copy to a working directory on the
host (referred to below as `<server-dir>`, e.g. `~/servers/sot-test/`).

## First-time setup (on the Docker host)

```bash
ssh <user>@<SERVER_HOST>
mkdir -p <server-dir>/data/plugins
# copy compose.yml (from this dir) to <server-dir>/compose.yml, then edit OPS:
#   OPS: "<your-minecraft-username>"
cd <server-dir> && docker compose up -d
docker compose logs -f sot-test   # wait for WorldEdit load + "Sands of Time Enabled Successfully"
```

Then drop the built plugin jar in and hot-reload it (no restart, no one kicked):

```bash
# from your dev machine, after `mvn package`:
scp target/SoT-1.0-SNAPSHOT.jar <user>@<SERVER_HOST>:<server-dir>/data/plugins/SoT.jar
# PlugManX reloads only the SoT plugin from the jar just copied:
ssh <user>@<SERVER_HOST> 'docker exec sot-test rcon-cli plugman reload SoT'
```

Reloading SoT re-runs its `onEnable`, which reloads segment templates from disk too — so a
`plugman reload SoT` is enough after saving a new segment; a full `docker restart sot-test` is only
needed for server/JVM/other-plugin changes (or if PlugManX itself is being installed).

## Resource pack (custom key/coin textures)

The server serves the SoT resource pack (`resourcepack/`, built into a slim zip) so clients see the
custom coloured **vault-key** and **coin** textures. Two pieces:

- A small **`pack` nginx sidecar** in `compose.yml` serves `<server-dir>/pack/sot.zip` over HTTP on
  port `25701`.
- The **plugin offers the pack** to each player (`resource-pack.url` in `data/plugins/SoT/config.yml`)
  together with the zip's SHA-1, which it downloads and hashes when it enables. The hash is what makes
  a client re-download a changed pack, and because it lives in the plugin a `plugman reload SoT` is
  enough to publish a new one — no server restart.

**Deliberately not `RESOURCE_PACK` / `RESOURCE_PACK_SHA1` in `compose.yml`.** Those land in
`server.properties`, which is read once at startup. Without a SHA1 Paper warns that clients will only
re-download "if you change the name of the pack" (they cache by URL), and with one every texture change
would need a container recreate — which kicks players, the opposite of the plugin hot-reload principle.

**One-time setup:**

1. Seed the zip and start the sidecar:
   ```bash
   # from your dev machine:
   scripts/build-resourcepack.sh                       # -> target/sot-resourcepack.zip
   scp target/sot-resourcepack.zip <user>@<SERVER_HOST>:<server-dir>/pack/sot.zip
   ssh <user>@<SERVER_HOST> 'cd <server-dir> && docker compose up -d'   # starts sot-pack
   ```
   (`<server-dir>/pack/` is created on the host; it is not in git — see `.gitignore`.)
2. In `<server-dir>/data/plugins/SoT/config.yml`, set `resource-pack.url` to the address **clients**
   use (the same one they connect to, e.g. the Tailscale IP), keeping port `25701`:
   `http://<SERVER_HOST>:25701/sot.zip`. The plugin only relays this URL — clients download it
   themselves — so it must **not** be the `pack` service name. Leave `sha1` blank (the plugin hashes
   the download); `required: true` kicks players who decline.
3. `docker exec sot-test rcon-cli plugman reload SoT`. The log should show
   `Resource pack http://...:25701/sot.zip (sha1 ...)`, and everyone online gets the prompt.

See "Auto-deploy from CI" below for the resource-pack CD, which does steps 1 and 3 on every change.

## First game (a HUB segment is bundled)

The plugin **ships a bundled `hub` segment** (see `src/main/resources/bundled_segments/`), which
`onEnable` auto-installs into `data/plugins/SoT/` on a fresh server — so `/sot start` works out of the
box: `/sot setup` → `/sot start` teleports you to the dungeon hub and starts the sand timer. `/sot end`
tears it down; `/sot setup <numTeams>` spreads multiple online players across teams.

To build your **own** hub (or more segments) instead:

1. `/sotbuilder` → `/sotmode <mode> [arg]` to place markers → WorldEdit-select the region →
   `/sotsavesegment hub HUB` (writes `data/plugins/SoT/hub.json` + `schematics/hub.schem`).
2. `/sotreloadsegments` (or `docker exec sot-test rcon-cli plugman reload SoT`) to load it without a
   restart.
3. To version-control it, run `scripts/pull-segments.sh` from your dev machine — it scp's the saved
   `*.json` + `schematics/*.schem` into `bundled_segments/` and regenerates the manifest; commit the
   result and it ships in the next build.

## Auto-deploy from CI (optional)

`.github/workflows/maven-publish.yml` has a `deploy` job that, on push to `master`, copies the freshly
built jar to `<server-dir>/data/plugins/SoT.jar` and **hot-reloads only the SoT plugin** via
`docker exec sot-test rcon-cli plugman reload SoT` — no container restart, so connected players stay
online. It is **gated** and stays skipped until both of these are done:

1. **Register a repo-scoped self-hosted runner** on the host, with labels `self-hosted,linux`, against
   this repository (see GitHub's self-hosted runner docs). The runner's user needs access to
   `<server-dir>` and to `docker`.
2. **Enable the job:** set an Actions **repository variable** `DEPLOY_ENABLED = true`
   (`gh variable set DEPLOY_ENABLED --body true`). Without it the deploy job is skipped so CI never
   hangs waiting for a runner.

**One-time bootstrap:** the reload step needs PlugManX already running on the server. After adding
`MODRINTH_PROJECTS: worldedit,plugmanx` + `MODRINTH_ALLOWED_VERSION_TYPE: beta` to `compose.yml`,
recreate the container **once** (`docker compose up -d`) so itzg downloads PlugManX into
`data/plugins/`. Every deploy after that is restart-free. If PlugManX is missing, the deploy's reload
step fails loudly rather than silently skipping the reload.

Manual fallback is the `scp` + `plugman reload` (or `docker restart`) shown above.

### Resource-pack CD

`.github/workflows/resourcepack-deploy.yml` is a **separate** gated job that runs on push to `master`
**only when `resourcepack/**` changes** (plus `workflow_dispatch` for on-demand runs). It rebuilds the
slim zip with `scripts/build-resourcepack.sh`, copies it to `<server-dir>/pack/sot.zip`, then
`plugman reload SoT` over RCON so the plugin re-hashes the zip and offers the new pack to everyone
online. It **never restarts the MC container** — no kick; players just get the pack prompt.
It reuses the same gating (`self-hosted,linux` runner + `DEPLOY_ENABLED=true`) as the jar deploy above;
a manual `workflow_dispatch` run is always allowed so you can seed or repair the pack.

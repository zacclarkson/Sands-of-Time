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
custom coloured **vault-key** and **coin** textures. Delivery is a small **`pack` nginx sidecar** in
`compose.yml`: it serves `<server-dir>/pack/sot.zip` over HTTP on port `25701`, and the `sot-test`
service points `RESOURCE_PACK` at that URL with `RESOURCE_PACK_ENFORCE: TRUE`.

**One-time setup:**

1. In `compose.yml`, set `RESOURCE_PACK` to your host address (the same one clients connect to, e.g.
   the Tailscale IP), keeping port `25701`: `http://<SERVER_HOST>:25701/sot.zip`. The MC server only
   relays this URL — clients download it themselves — so it must **not** be the `pack` service name.
2. Seed the zip and start the sidecar:
   ```bash
   # from your dev machine:
   scripts/build-resourcepack.sh                       # -> target/sot-resourcepack.zip
   scp target/sot-resourcepack.zip <user>@<SERVER_HOST>:<server-dir>/pack/sot.zip
   ssh <user>@<SERVER_HOST> 'cd <server-dir> && docker compose up -d'   # starts sot-pack + applies env
   ```
   (`<server-dir>/pack/` is created on the host; it is not in git — see `.gitignore`.)

**Deliberately no `RESOURCE_PACK_SHA1`.** The CD job hot-swaps `sot.zip` without restarting the MC
container, so clients simply re-download the current pack on their next join. Setting a SHA1 would
force a container recreate (and kick players) on every texture change — the opposite of the plugin
hot-reload principle. See "Auto-deploy from CI" below for the resource-pack CD.

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
slim zip with `scripts/build-resourcepack.sh` and copies it to `<server-dir>/pack/sot.zip`. It **never
touches the MC container** — no restart, no kick; clients pick up the new textures on their next join.
It reuses the same gating (`self-hosted,linux` runner + `DEPLOY_ENABLED=true`) as the jar deploy above;
a manual `workflow_dispatch` run is always allowed so you can seed or repair the pack.

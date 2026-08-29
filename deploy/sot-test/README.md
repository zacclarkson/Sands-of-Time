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

# Test/dev server (`sot-test`)

An always-on Paper 1.21.1 + WorldEdit server, running on a Docker host, that runs the current build of
the SoT plugin so you can log in from a real Minecraft client and test the UX (builder tools and, via
`/sot`, a full game round).

- **Connect:** `<SERVER_HOST>:25700` from your Minecraft client. This is a raw game port (not HTTP),
  so expose it only on a trusted network (LAN/VPN), not the public internet.
- **Requires:** Paper 1.21.1, Java 21, WorldEdit 7.3.x (auto-installed). All provided by the
  `itzg/minecraft-server:java21` image + `MODRINTH_PROJECTS: worldedit`.

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

Then drop the built plugin jar in and restart:

```bash
# from your dev machine, after `mvn package`:
scp target/SoT-1.0-SNAPSHOT.jar <user>@<SERVER_HOST>:<server-dir>/data/plugins/SoT.jar
ssh <user>@<SERVER_HOST> 'docker restart sot-test'
```

## First game (build a HUB segment — required for /sot start)

The plugin ships no segment templates, and dungeon generation needs at least one `HUB`. Once:

1. Join the server, then: `/sotbuilder` → `/sotmode <mode> [arg]` to place markers → WorldEdit-select
   the region → `/sotsavesegment hub HUB` (writes `data/plugins/SoT/hub.json` +
   `schematics/hub.schem`).
2. `docker restart sot-test` (there is no live reload command).
3. `/sot setup` → `/sot start` → you are teleported to the dungeon hub and the sand timer starts.
   `/sot end` tears it down. `/sot setup <numTeams>` spreads multiple online players across teams.

## Auto-deploy from CI (optional)

`.github/workflows/maven-publish.yml` has a `deploy` job that, on push to `master`, copies the freshly
built jar to `<server-dir>/data/plugins/SoT.jar` and restarts the container. It is **gated** and stays
skipped until both of these are done:

1. **Register a repo-scoped self-hosted runner** on the host, with labels `self-hosted,linux`, against
   this repository (see GitHub's self-hosted runner docs). The runner's user needs access to
   `<server-dir>` and to `docker`.
2. **Enable the job:** set an Actions **repository variable** `DEPLOY_ENABLED = true`
   (`gh variable set DEPLOY_ENABLED --body true`). Without it the deploy job is skipped so CI never
   hangs waiting for a runner.

Manual fallback is the `scp` + `docker restart` shown above.

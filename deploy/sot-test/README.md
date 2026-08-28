# Homelab test/dev server (`sot-test`)

An always-on Paper 1.21.1 + WorldEdit server on the debian box that runs the current build of the
SoT plugin, so you can log in from a real Minecraft client and test the UX (builder tools and, via
`/sot`, a full game round).

- **Connect:** `100.125.118.2:25700` (Tailscale) or `192.168.1.250:25700` (LAN). Game port only —
  not behind Cloudflare/Authentik.
- **Requires:** Paper 1.21.1, Java 21, WorldEdit 7.3.x (auto-installed). All provided by the
  `itzg/minecraft-server:java21` image + `MODRINTH_PROJECTS: worldedit`.

`compose.yml` here is the version-controlled reference; the live copy lives at
`~/servers/sot-test/compose.yml` on the box.

## First-time setup (on the debian box)

```bash
ssh zac@192.168.1.250
mkdir -p ~/servers/sot-test/data/plugins
# copy compose.yml (from this dir) to ~/servers/sot-test/compose.yml, then edit OPS:
#   OPS: "<your-minecraft-username>"
cd ~/servers/sot-test && docker compose up -d
docker compose logs -f sot-test   # wait for WorldEdit load + "Sands of Time Enabled Successfully"
```

Then drop the built plugin jar in and restart:

```bash
# from your dev machine, after `mvn package`:
scp target/SoT-1.0-SNAPSHOT.jar zac@192.168.1.250:~/servers/sot-test/data/plugins/SoT.jar
ssh zac@192.168.1.250 'docker restart sot-test'
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
built jar to `~/servers/sot-test/data/plugins/SoT.jar` and restarts the container. It is **gated** and
stays skipped until both of these are done:

1. **Register a repo-scoped self-hosted runner** on the box (see the notes-maker CI/CD section of the
   debianserver `CLAUDE.md` for the exact `config.sh`/`svc.sh` steps — clone `~/actions-runner` to a
   new dir, clean the `.runner*`/`.credentials*` files, register against
   `https://github.com/zacclarkson/Sands-of-Time` with labels `self-hosted,linux`, then
   `sudo ./svc.sh install zac && sudo ./svc.sh start`).
2. **Enable the job:** set an Actions **repository variable** `DEPLOY_ENABLED = true`
   (`gh variable set DEPLOY_ENABLED --body true`). Without it the deploy job is skipped so CI never
   hangs waiting for a runner.

Manual fallback is the `scp` + `docker restart` shown above.

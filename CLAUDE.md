# CLAUDE.md

Guidance for Claude Code (and contributors) working in this repository.

**Sands of Time** is a Paper Minecraft plugin — an MCC-style team game where players explore a
generated dungeon, collect and bank coins, and race a sand timer. See `readme.md` (setup) and
`GAME_RULES.md` (design/rules) for the game itself.

## Requirements

- **Java 25.** Paper 26.2 is compiled against Java 25, so the build targets release 25 and the
  plugin needs a Java 25+ server at runtime. Point `JAVA_HOME` at a JDK 25 (or newer) before
  running Maven.
- **Paper 26.2** at runtime (`paper-api` is `provided` scope). Note the calendar versioning: the
  1.21 line was succeeded by 26.1 and then 26.2, so 26.2 is newer than 1.21.x, not older.
- **WorldEdit 7.4.5+** at runtime. `plugin.yml` has `depend: [WorldEdit]` (a hard depend) and
  `worldedit-bukkit` is `provided` scope, so WorldEdit (or FAWE) must be installed separately on any
  server — the plugin will not enable without it.

## Build & test

- `mvn -B verify` — compile, run the unit tests, and produce the shaded jar
  `target/SoT-1.0-SNAPSHOT.jar` (gson is relocated under `com.clarkson.sot.libs.gson`). CI runs this
  on Temurin 21.
- `mvn -B test` — just the unit tests.
- `mvn install` — additionally copies the jar to the local server plugins dir set by the
  `server.plugins.dir` property in `pom.xml` (adjust it to your own path).

### Unit tests (`src/test/java`)

JUnit 5 + Mockito, with **MockBukkit** for anything needing a live server/scheduler/events. Pure-logic
classes use plain Mockito; we deliberately do **not** `MockBukkit.load(SoT.class)` (onEnable is heavy
and WorldEdit is provided-scope).

**Version coupling:** MockBukkit is pinned to a specific `paper-api` line, and to a JUnit version.
`mockbukkit-v26.2:4.116.1` tracks `paper-api` **26.2** and declares `junit-jupiter-api` **6.1.3**.
Bumping any one of the three usually forces the others — keep them in lockstep. `byte-buddy` is also
pinned explicitly, because MockBukkit needs 1.18.x for Java 25 bytecode while Mockito still declares
1.17.7 and Maven would otherwise mediate by declaration order.

### Integration harness (`integration-test/`)

A heavier, on-demand tier: a real Paper server (Docker, `itzg/minecraft-server`) running the built
plugin, driven by a headless Mineflayer bot. WorldEdit is installed via `MODRINTH_PROJECTS`. Run it
manually (see `integration-test/README.md`); it is **not** part of CI.

## Commands

- **Builder tools** (perm `sot.admin.builder` / `sot.admin.savesegment`): `/sotbuilder` (gives the
  BLAZE_ROD tool), `/sotmode <mode> [arg]` (switch placement mode), `/sotsavesegment <name> <type>`
  (save the WorldEdit selection + placed markers as a segment template).
- **Game control** (perm `sot.admin.control`): `/sot setup [numTeams] | start | end | set <lobby|trapped>`,
  wired to `GameManager.setupGame/startGame/endGame` and the location setters.

## Architecture notes & gotchas

- **`GameManager` owns the single set of gameplay managers.** Its constructor builds
  `PlayerStateManager`, `TeamManager`, `ScoreManager`, `BankingManager`, `SandManager`,
  `VaultManager`, `FloorItemManager`, `DoorManager`, and `DungeonGenerator`. `SoT.onEnable()` must
  register **those** instances (via `gameManager.getXManager()` getters) as event listeners — do
  **not** build a second parallel set (that was bug #65: listeners bound to instances that didn't
  hold the live game state).
- **A HUB segment is bundled and auto-installed.** Dungeon generation needs at least one `HUB` segment
  on disk (`plugins/SoT/<name>.json` + `schematics/<name>.schem`). Templates listed in
  `src/main/resources/bundled_segments/manifest.txt` are shipped in the jar and copied into the data
  folder by `SoT.installBundledSegments()` on enable (**skip-if-present**, so in-game edits are never
  clobbered) — so a fresh server has a working hub out of the box. To add/update the bundled set: build
  in-game + `/sotsavesegment <name> HUB`, then `scripts/pull-segments.sh` to pull the files off the dev
  server into `bundled_segments/` (regenerating the manifest) and commit. Binary `.schem` files are
  kept byte-clean by a `.gitattributes` (`*.schem binary`) and by excluding `bundled_segments/**` from
  Maven resource filtering (`pom.xml`). After saving a new segment on a running server, `/sotreloadsegments`
  (refused while a game is RUNNING) loads it without a restart; templates are otherwise read only at
  startup. Until a HUB exists, the plugin enables fine but `/sot start` aborts.
- **The safe exit is a segment marker.** The escape point comes from a `SAFE_EXIT` marker on a
  segment template; a marker on the HUB segment wins over one on any other segment. Templates saved
  before that marker existed carry none, so `GameManager.getTeamSafeExitLocation` falls back to the
  hub and `EscapeListener` falls back to accepting any `END_PORTAL_FRAME` within 30 blocks of the
  hub. Generation logs a warning once when no template defines an exit.
- **Game locations come from `config.yml`.** `locations.lobby` and `locations.trapped` are stored as
  plain `world/x/y/z/yaw/pitch` scalars and read by `SoTConfig` (deliberately *not* Bukkit's
  `config.getLocation()`, whose serialized form needs a `==: org.bukkit.Location` marker and is not
  hand-editable). Both ship unset. When one is unset the plugin still enables — falling back to the
  main world's spawn and logging a warning — but `/sot setup` and `/sot start` refuse to run, so a
  round is never generated somewhere nobody chose. `/sot set <lobby|trapped>` captures the sender's
  position, writes it back to `config.yml` and applies it live; moving the lobby is rejected while a
  game is running, since `startGame` derives the dungeon world and origin from it.

## Dev server

`deploy/sot-test/` holds a reference Docker Compose for an always-on Paper 26.2 + WorldEdit server
you can log into to test the UX. Fill in the placeholders (host, your Minecraft username for `OPS`)
for your own environment. The build jar is deployed to `data/plugins/SoT.jar`.

`.github/workflows/maven-publish.yml` can auto-deploy the jar to that server on push to `master`, but
the deploy job is **gated** — it stays skipped until a self-hosted runner is registered and the repo
variable `DEPLOY_ENABLED=true` is set (see `deploy/sot-test/README.md`).

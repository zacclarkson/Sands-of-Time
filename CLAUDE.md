# CLAUDE.md

Guidance for Claude Code (and contributors) working in this repository.

**Sands of Time** is a Paper Minecraft plugin — an MCC-style team game where players explore a
generated dungeon, collect and bank coins, and race a sand timer. See `readme.md` (setup) and
`GAME_RULES.md` (design/rules) for the game itself.

## Requirements

- **Java 21.** The build and the unit tests fail on newer JDKs (a JDK 26 default crashes
  `maven-compiler-plugin` at test-compile). Point `JAVA_HOME` at a JDK 21 before running Maven.
- **Paper 1.21.1** at runtime (`paper-api` is `provided` scope).
- **WorldEdit 7.3.x** at runtime. `plugin.yml` has `depend: [WorldEdit]` (a hard depend) and
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

**Version coupling:** MockBukkit is pinned to a specific `paper-api` patch. `mockbukkit-v1.21:4.0.0`
(JUnit 5) requires `paper-api` **1.21.1**. Bumping one usually forces the other — keep them in
lockstep.

### Integration harness (`integration-test/`)

A heavier, on-demand tier: a real Paper server (Docker, `itzg/minecraft-server`) running the built
plugin, driven by a headless Mineflayer bot. WorldEdit is installed via `MODRINTH_PROJECTS`. Run it
manually (see `integration-test/README.md`); it is **not** part of CI.

## Commands

- **Builder tools** (perm `sot.admin.builder` / `sot.admin.savesegment`): `/sotbuilder` (gives the
  BLAZE_ROD tool), `/sotmode <mode> [arg]` (switch placement mode), `/sotsavesegment <name> <type>`
  (save the WorldEdit selection + placed markers as a segment template).
- **Game control** (perm `sot.admin.control`): `/sot setup [numTeams] | start | end`, wired to
  `GameManager.setupGame/startGame/endGame`.

## Architecture notes & gotchas

- **`GameManager` owns the single set of gameplay managers.** Its constructor builds
  `PlayerStateManager`, `TeamManager`, `ScoreManager`, `BankingManager`, `SandManager`,
  `VaultManager`, `FloorItemManager`, `DoorManager`, and `DungeonGenerator`. `SoT.onEnable()` must
  register **those** instances (via `gameManager.getXManager()` getters) as event listeners — do
  **not** build a second parallel set (that was bug #65: listeners bound to instances that didn't
  hold the live game state).
- **No segment templates are bundled.** Dungeon generation needs at least one `HUB` segment on disk
  (`plugins/SoT/<name>.json` + `schematics/<name>.schem`). Build one in-game with the builder tools +
  `/sotsavesegment <name> HUB`, then **restart** the server — there is no live reload. Until a HUB
  exists, the plugin enables fine but `/sot start` aborts.
- **Placeholder locations.** `onEnable` passes placeholder lobby/trapped locations (TODO: load from
  `config.yml`), which affects visual-timer placement and the trapped-player destination.

## Dev server

`deploy/sot-test/` holds a reference Docker Compose for an always-on Paper 1.21.1 + WorldEdit server
you can log into to test the UX. Fill in the placeholders (host, your Minecraft username for `OPS`)
for your own environment. The build jar is deployed to `data/plugins/SoT.jar`.

`.github/workflows/maven-publish.yml` can auto-deploy the jar to that server on push to `master`, but
the deploy job is **gated** — it stays skipped until a self-hosted runner is registered and the repo
variable `DEPLOY_ENABLED=true` is set (see `deploy/sot-test/README.md`).

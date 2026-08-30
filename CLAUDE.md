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
- **Game control** (perm `sot.admin.control`): `/sot setup [numTeams] | start | end | reset | set <lobby|trapped>`,
  wired to `GameManager.setupGame/startGame/endGame/resetGame` and the location setters.

## Architecture notes & gotchas

- **`GameManager` owns the single set of gameplay managers.** Its constructor builds
  `PlayerStateManager`, `TeamManager`, `ScoreManager`, `BankingManager`, `SandManager`,
  `VaultManager`, `FloorItemManager`, `DoorManager`, and `DungeonGenerator`. `SoT.onEnable()` must
  register **those** instances (via `gameManager.getXManager()` getters) as event listeners — do
  **not** build a second parallel set (that was bug #65: listeners bound to instances that didn't
  hold the live game state).
- **The live scoreboard is a task, not a listener.** `GameManager` owns `GameScoreboardManager`
  and starts it in `startGame` / stops it in `endGameInternal`, so — unlike the managers listed
  above — there is nothing for `SoT.onEnable()` to register. It refreshes every second from the live
  managers and holds no game state of its own; `ScoreboardLayout` builds the text and is where the
  unit tests live. It shows banked/unbanked coins and the standings but **never the sand timer** —
  reading the hub's sand column and calling the time out is the team's job, and a test guards that.
  Sidebar rows keep a permanent invisible score entry each and only their scoreboard-team prefix is
  rewritten, because rewriting the entries themselves would make the whole sidebar flicker once a
  second.
- **Coin pickups are batched into one action bar.** `ScoreManager` no longer sends a message per
  coin — `CoinPickupNotifier` (in `ui`) keeps a per-player running total for a 3-second window from
  the first coin of a burst, so picking up 5 then 7 reads `+12 coins (x2)` instead of the `+7` that
  used to overwrite the `+5` before anyone could read it. Expiry is time-based and checked on the
  next pickup, so there is no scheduled task; the clock is constructor-injected for the unit tests.
  Banking or dying (`clearPlayerUnbankedScore`) and game end (`clearAllUnbankedScores`) end the
  batch, so a fresh burst never inherits a stale total.
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
- **Segment doors are generated, not derived at paste time.** `DungeonGenerator` records each
  connection its DFS actually makes as a `Doorway` (blueprint-relative cell + direction), plus the
  entry points it attached no neighbour to. Both lists ride the blueprint into `Dungeon` as absolute
  `EntryPoint`s, and `DoorManager.initializeDoorsForInstance` builds a `SegmentDoor` at every
  doorway and seals the leftovers as plain wall. Deriving doors by walking every placed segment's
  entry points instead (what it used to do) put a locked door on openings that led nowhere — the
  bundled hub alone declares nine. **`Door.buildClosed()` is what makes a door exist**: templates
  carve their doorways as open 3x4 holes, so registering the `SegmentDoor` object without writing
  blocks left the passage walkable and the lock location as air, which never fires
  `RIGHT_CLICK_BLOCK`. The door body is `DARK_OAK_PLANKS` with an `OXIDIZED_CUT_COPPER` keyhole one
  block above the entry marker; opening clears layers top-first so the wall sinks into the floor.
  Vault marker blocks stay out of `DoorManager` — `VaultManager` owns those clicks (bug #65's
  sibling), and a test pins it.
- **Rusty keys spawn at `ITEM_SPAWN` markers.** `DungeonManager.populateFloorItems` rolls
  `RUSTY_KEY_SPAWN_CHANCE` (20%) per item spawn and calls `FloorItemManager.spawnRustyKey`,
  otherwise falling through to the loot table. Nothing called `spawnRustyKey` at all before, so
  every segment door was permanently locked. Placement is by chance rather than one-per-room, so a
  branch can come up with no key and stay shut — raise the constant if that bites; the doorway and
  key counts are both logged.
- **The safe exit is a segment marker.** The escape point comes from a `SAFE_EXIT` marker on a
  segment template; a marker on the HUB segment wins over one on any other segment. Templates saved
  before that marker existed carry none, so `GameManager.getTeamSafeExitLocation` falls back to the
  hub and `EscapeListener` falls back to accepting any `END_PORTAL_FRAME` within 30 blocks of the
  hub. Generation logs a warning once *per `/sot setup`* when no template defines an exit (see the
  retry-logging note below). The marker only decides *where you escape from*: escaping teleports the
  player to the **lobby**, not to the exit block — the round is over for them, `ESCAPED_SAFE` bars
  them from escaping again (`EscapeListener`) or spending sand (`SandManager`), and the dungeon is
  torn down moments later.
- **`ENDED` is terminal; `/sot reset` is the only way back to `SETUP`.** A round ends at
  `GameState.ENDED` and nothing rearms it automatically, so the final standings stay readable —
  `GameManager.resetGame()` (guarded by the pure `canResetFrom`, which refuses `COUNTDOWN`/`RUNNING`/
  `PAUSED`) is what makes consecutive games possible without a server restart. All teardown lives in
  one idempotent `tearDownRound()`, called by `endGameInternal` (**last**, after the status read that
  `returnsToLobbyAtGameEnd` depends on and after `displayFinalScores`), by `resetGame`, and by
  `setupGame`. Never clear `teamDungeonManagers` directly — go through `cleanupDungeonInstances()`,
  since a dropped `DungeonManager` strands its pasted blocks and the next round's paste uses
  `ignoreAirBlocks` and cannot clear them. `teamManager.clearAssignments()` belongs to `resetGame`
  only: `/sot setup` assigns players *before* calling `setupGame`, so clearing there would wipe the
  assignments it is about to read. A missing HUB template is deliberately **not** a game state
  (it used to latch `ENDED` at boot); `setupGame`/`startGame` check `hasHubTemplate()` instead, so
  `/sotreloadsegments` fixes it live.
- **Generation retries must not multiply the log.** `DungeonGenerator.generateDungeonLayout` retries
  `attemptGeneration` up to 20 times, so any warning inside an attempt is a candidate for being
  printed 20 times per `/sot setup`. Conditions that describe the *templates on disk* — no
  `SAFE_EXIT` marker, no `BLUE` key spawn, a duplicate vault/key marker — go through
  `warnOncePerGeneration(key, message)`, whose key set is cleared at the top of each
  `generateDungeonLayout` call, so they warn once per call and again on the next one. The
  vault/key validation failures are genuinely per-attempt (a layout can fail on one attempt and pass
  on the next), so they log at `fine` and are tallied instead; if every attempt fails,
  `generateDungeonLayout` follows the `severe` failure line with one summary naming each unmet
  requirement and how many attempts it was missing from. Per-attempt progress chatter is `fine` too —
  only the success line and the failure summary reach the console.
- **Countdown tasks are epoch-guarded.** The ticker only re-checks the state once a second, so
  `roundEpoch` (bumped on every start and end) is what stops a round aborted mid-countdown from
  having its stale task finish the *next* round's countdown early.
- **The coin bank is an ender chest built at the `BANK` marker.** Banking is what turns collected
  coins into score, so without it a round ends 0-0. The chain mirrors `TIMER`:
  `Segment.getBankOffset()` (JSON key `bankLocationOffset`) -> `DungeonGenerator.selectBankRelativeLocation`
  (a HUB's marker wins outright) -> `DungeonBlueprint.getBankRelativeLocation` ->
  `Dungeon.isBankAt` -> `GameManager.isTeamBankAt`. Three things matter here. **(a)** The block is
  what makes the bank exist: `DungeonManager.placeBankBlock()` writes the `ENDER_CHEST` (facing the
  hub) *after* `pasteSegmentSchematics()`, since a paste would paint over it — the same lesson as
  `Door.buildClosed()`. Teardown needs nothing, because `cleanupInstance()` clears the whole
  blueprint region. **(b)** Like the sand deposit and unlike the safe exit, the match is an *exact*
  block match: the builder tool records the marker at the air cell the chest occupies, so there is
  no +/-1 Y fudge. **(c)** `BankingManager.onPlayerInteract` must ignore the off-hand pass
  (`event.getHand() != EquipmentSlot.HAND`) — `PlayerInteractEvent` fires once per hand, and the
  second pass would overwrite the confirmation with "You have no coins to bank!" — and must cancel
  the event, or the vanilla ender chest inventory opens over the bank. `BankingManager` holds no
  per-team state (the cell is looked up through `GameManager` each click), so there is nothing to
  clear between rounds; it also cancels `BlockBreakEvent` on the bank cell, since an ender chest
  mined without silk touch drops 8 obsidian and takes the team's bank out of the hub. A hub with no
  `BANK` marker simply plays with no bank, warned once per `/sot setup` via `warnOncePerGeneration`.
- **Sand is an item; only a deposit point converts it to time.** Breaking a dungeon sand block hands the
  player a plain `Material.SAND` item and adds *no* time — `SandManager.onBlockPlace` does that, when the
  sand is placed on one of the team's `TIMER_DEPOSIT` marker cells. The chain mirrors `PLAYER_SPAWN`:
  `Segment.getSandTimerOffsets()` (JSON key `sandTimerLocations`) → `DungeonGenerator.selectSandTimerRelativeLocations`
  (a HUB's markers win outright) → `DungeonBlueprint` → `Dungeon.isSandTimerDepositAt` →
  `GameManager.isTeamSandTimerDepositAt`. Three things are easy to get wrong here. **(a)** Unlike the safe
  exit, a deposit needs no ±1 Y tolerance: the builder tool records the marker at the *air cell* next to
  the clicked face, which is exactly the cell a placed block occupies, so it is an exact block match
  (the safe exit compares a clicked *solid* block against an air-cell marker, hence its fudge).
  **(b)** The placement is *cancelled* rather than placed and cleared a tick later — sand has gravity, and
  a block that becomes a falling entity before the cleanup runs would land elsewhere as a duplicate;
  cancelling also refunds the sand atomically, which is what makes an at-cap refusal loss-free
  (`TeamTimer.addSeconds` clamps, so an accepted deposit at 150s would destroy the sand for nothing).
  **(c)** The inventory is the *only* store of carried sand — there is deliberately no counter map to
  drift out of sync. That is why escaping wipes the inventory (`GameManager.handlePlayerLeave`) and why
  `tearDownRound()` strips sand from every team member in its per-team pass, before
  `activeTeamsInGame.clear()` — that map is the only source of member lists: players who are trapped, dead, or still exploring never pass through the
  escape path, and their sand would otherwise buy free time next round. Breaking a block of the team's own
  visual timer column is refused for the same reason — the column is sand, and a mined block is restored
  by the next `syncVisualState()`, which the resulting deposit itself triggers.
- **Dying drops carried sand on the floor.** Nearly all of that is vanilla: `DeathListener` never
  touches `event.getDrops()`, so a death that drops the inventory scatters the sand with everything
  else and it lands, merges and despawns by the server's own rules. `SandManager.dropCarriedSandOnDeath`
  is only the backstop for a death that *keeps* the inventory (the `keepInventory` gamerule, or another
  plugin) — it pulls the sand out and drops it at the death location itself, because sand is the round's
  currency for both timer seconds and revives and losing it on death is a rule of the game, not a server
  setting. It is called from `DeathListener` **before** `handlePlayerDeath`, which queues the death-cage
  teleport. Unbanked coins are different and deliberately so: they are a number in `ScoreManager`, so
  `applyDeathPenalty` clears them and there is nothing on the floor to recover. Nothing extra is needed to
  keep death drops out of the next round — `DungeonManager.cleanupInstance()` already removes every
  non-player entity inside the dungeon bounds at teardown.
- **The end-of-round teleport must skip trapped players.** `handleTeamTimerEnd` only *queues* the
  trapped teleport (`runTask` = next tick) and then calls `checkGameEndCondition` synchronously, so
  when the last team expires `endGameInternal` runs in that same tick and queues its lobby teleport
  **behind** the trapped one. `GameManager.returnsToLobbyAtGameEnd` is the guard that keeps the
  lobby teleport off `TRAPPED_TIMER_OUT` players; without it a single-team round (the `/sot setup`
  default) never shows the trapped box at all. The status has to be read *outside* the scheduled
  task, since `clearAllStates()` runs later in the same tick.
- **The visual sand column lives in the hub, never at the lobby.** Its base comes from a `TIMER`
  marker on a segment template (HUB wins), via `DungeonGenerator.selectTimerBaseRelativeLocation` →
  `DungeonBlueprint.getTimerBaseRelativeLocation` → `DungeonManager.getTimerBaseLocation`.
  `SoTTeam` therefore starts with **no** `VisualSandTimerDisplay`: `GameManager.startGame` builds it
  via `SoTTeam.relocateVisualTimer` once the team's dungeon is pasted, and a hub without a `TIMER`
  marker just plays with no column (logged as a warning) — there is deliberately no lobby fallback.
  `VisualSandTimerDisplay` is additionally gated on an `armed` flag set only by
  `startVisualUpdates()`, so no sand can be placed before the column is anchored (that gate is what
  stopped a 15-block pillar appearing at the lobby spawn at `/sot setup`). The bundled hub's marker
  sits at segment-relative `(21, 1, 18)`, the pedestal under the sand column baked into `hub.schem`.
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

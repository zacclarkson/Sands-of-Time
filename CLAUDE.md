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
  on Temurin 25 — it has to, since the build targets release 25 and MockBukkit ships Java 25 bytecode.
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

## Incidental findings

Work here turns up unrelated defects fairly often; most of the architecture notes below are scar
tissue from exactly that. When you find one while doing something else, **open a GitHub issue and
carry on with the task in hand.** Do not widen the current change to fix it, and do not leave it only
in a commit message or a PR comment — both are lost once the PR merges.

File one for a real defect with a correctness or user-visible consequence: a wrong calculation, a
stub returning null that callers trust, a silent failure an operator cannot diagnose. Style nits and
speculative refactors are not worth an issue. Say what is wrong, what it breaks, and where — file and
line — so it is actionable without rediscovering it.

## Commands

- **Builder tools** (perm `sot.admin.builder` / `sot.admin.savesegment`): `/sotbuilder` (gives the
  BLAZE_ROD tool), `/sotmode <mode> [arg]` (switch placement mode), `/sotsavesegment <name> <type>`
  (save the WorldEdit selection + placed markers as a segment template).
- **Game control** (perm `sot.admin.control`): `/sot setup [numTeams] | start | end | reset | set <lobby|trapped> | seed [<value>|random]`,
  wired to `GameManager.setupGame/startGame/endGame/resetGame`, the location setters and
  `setDungeonSeed`.

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
  folder by `BundledSegmentInstaller` on enable (**skip-if-present**, so in-game edits are never
  clobbered) — so a fresh server has a working hub out of the box. Skip-if-present has a cost: a
  corrected bundled template reaches *no* server that has run the plugin before. So the installer never
  skips silently — it byte-compares each half against the jar's copy and, when they differ, logs which
  files differ and that deleting both is what takes the bundled version (an identical copy is only a
  `fine`). It also treats a template's `.json` and `.schem` as **one unit**: they come from a single
  `/sotsavesegment` and nothing downstream cross-checks them, so installing one bundled half beside one
  stale local half is how a template ends up with declared bounds its geometry disagrees with. A
  half-present pair is therefore completed from the jar as a unit, with the surviving file moved aside
  to `<name>.<ext>.bak` and a warning naming it. That is also the load-time check
  `StructureLoader.warnIfDeclaredSizeIsTooSmall` exists for: it reads the schematic's dimension header
  through `SchematicDimensions` (straight off the gzipped NBT, since a `ClipboardFormats` read needs a
  live WorldEdit platform and this runs at load) and warns when the declared `size` is *smaller* than
  the schematic on any axis. Only smaller — declaring more is deliberate and load-bearing (see the
  visual-timer note on `hub.json`'s `size.y = 17`). To add/update the bundled set: build
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
- **Gates and vault doors are template geometry, not generated.** Unlike doorways — which ride the
  blueprint only because the DFS *discards* which connections it made — every `GATE`/`LEVER`/`VAULT_DOOR`
  marker on a placed template is used verbatim, so none of it goes through `DungeonBlueprint` or `Dungeon`
  (whose constructor is already 16 arguments). `DungeonManager.resolveGateGroups`/`resolveVaultDoors` walk
  `getPlacedSegmentsInWorld()` *after* the paste and hand `DoorManager.initializeGatesForInstance` absolute
  `GateGroup`s (one lever plus **that segment's** gates — the pairing is the feature) and
  `VaultDoorPlacement`s. Relative→absolute goes through `SegmentGeometry`, which rotates a bound with
  `SegmentRotation.rotateBound` **before** adding the origin: one 90° step maps `(x,z) -> (z, sizeX-1-x)`,
  so rotating can swap which corner is the minimum, and `PlacedSegment.getAbsoluteLocation` deliberately
  does not rotate. Gates are `IRON_BARS` (see-through on purpose — the mechanic is deciding whether to open
  one) and the lever is a **real `Material.LEVER` block written at instantiation**: the builder marker is an
  air cell holding a `BlockDisplay`, and air never fires `RIGHT_CLICK_BLOCK` — the same trap
  `Door.buildClosed()` exists for. The **first** pull is deliberately *not* cancelled, so vanilla flips the
  lever on and the world itself records the state; every later click *is* cancelled so it cannot flip back
  over an open gate. Vault doors have no keyhole and no key: `VaultManager` calls
  `DoorManager.openVaultDoors(teamId, colour)` from its own marker handler — the marker click stays
  VaultManager's (bug #65), the wall stays DoorManager's — and they live in `vaultDoorsByTeamAndColor`,
  never in `doorsByTeamAndLockLocation`, so `getDoorAt` still resolves nothing at a vault or a gate.
  `VaultDoor.isCorrectKey` survives only for `KeyItemTaggingTest`. The **vault marker sinks with the
  wall**: `VaultManager.revealVault` hands the clicked block to `openVaultDoors`, and `VaultDoor`
  merges it into `getBlocksSorted` so it clears with the layer it sits in (the marker is only attached
  at open time, so `buildClosed()` never paints over it). Nothing spawns coins at the marker any more —
  the reward is what the segment puts *behind* the door, which is the only place a wall can reveal
  anything; scattering it around the marker put it in front of the wall and left the marker standing
  as glass. Gates and levers need no protection code of their own: `BreakableBlocks.BREAKABLE` is a
  whitelist of `SAND` and `SPAWNER`, so `IRON_BARS` and `LEVER` are already refused during a round. **A vault door must live in the same
  segment as its vault marker**: `SaveSegmentCommand` sets `containedVault` from the `VAULT_DOOR` marker, so
  a segment claiming a vault with no `VAULT_MARKER` makes the DFS count that colour as placed while
  `consolidateFeatureLocations` emits no marker, and all 20 generation attempts then fail on the missing
  marker. The save command now refuses that combination and the generator warns once for templates already
  on disk.
- **A branch's vault colour is resolved at generation, not saved on the template.** A
  `BRANCH_SIGNIFIER` marker records only *where* a coloured wall marking goes; the colour cannot be
  template data, because the same corridor sits on the red branch in one layout and the gold branch
  in the next. `DungeonGenerator.generatePathRecursive` therefore **returns** the vault colours in
  the subtree it just built and records them in `branchColoursByDoorway`, keyed on the doorway cell
  — the same cell `findUnusedOpenings` keys on, and the cell *both* segments of a connection share
  (`calculatePlacementOrigin`), which is what lets a marking inside the child resolve against the
  branch it stands on. `resolveBranchSignifiers` then pairs each placeholder with the **nearest entry
  point of its own template** and takes that branch's colour; the pairing is computed in template
  space, so it survives rotation (index i of `getEntryPoints()` is index i of
  `getRotatedEntryPoints()`). Two cases deliberately emit *nothing* rather than a wrong colour: a
  placeholder beside an opening the DFS never attached a neighbour to (the hub's ~6 non-vault exits)
  and one whose branch holds no vault. Only the **first** segment of a colour contributes, matching
  the first-wins rule `consolidateFeatureLocations` applies to the markers — a later duplicate is not
  the vault that reaches the blueprint, so its branch must not advertise the colour. Where a branch
  holds several vaults the shallowest wins, since that is the one the player meets first. The
  resolved `BranchSignifier`s ride `DungeonBlueprint` (not `Dungeon`, whose constructor is already
  16 arguments — nothing at runtime queries "is there a marking here", and
  `BreakableBlocks.BREAKABLE` already refuses the block during a round), and
  `DungeonManager.placeBranchSignifiers` writes `VaultColor.getConcreteMaterial()` at each cell
  **after** the paste, for the same reason `placeBankBlock` does. The bundled hub declares no
  `BRANCH_SIGNIFIER` markers, so a stock server sees no colour markings; generation warns once per
  `/sot setup` when no template declares any.
- **Rusty keys spawn at `ITEM_SPAWN` markers.** `DungeonManager.populateFloorItems` rolls
  `RUSTY_KEY_SPAWN_CHANCE` (20%) per item spawn and calls `FloorItemManager.spawnRustyKey`,
  otherwise falling through to the loot table. Nothing called `spawnRustyKey` at all before, so
  every segment door was permanently locked. Placement is by chance rather than one-per-room, so a
  branch can come up with no key and stay shut — raise the constant if that bites; the doorway and
  key counts are both logged.
- **A `MOB_SPAWNER` marker becomes a real, breakable block.** The markers were placeable and saved
  (`mobSpawnerLocations`) long before anything read them, so no mob ever appeared. They now ride the
  same channel coins and items take — `Segment.getMobSpawnerLocations()` →
  `DungeonGenerator.consolidateFeatureLocations` → `DungeonBlueprint` → `Dungeon` →
  `DungeonManager.armMobSpawners` → `MobManager.armSpawner`, which writes a `Material.SPAWNER` block
  at each marker. `DungeonManager` resolves the depth, because it is the only holder of
  `placedSegmentsInWorld`. **The block is the encounter**: `MobManager`'s repeating task starts it
  when a member of the **owning team** comes within 10 blocks and it then produces a wave every
  8 seconds for as long as someone is in range — it never stops on its own. Breaking it **with a
  pickaxe** is the only way to end it, and pays the same coins as an ordinary stack at that depth
  (`DungeonManager.coinBaseValueForDepth`, shared so the two cannot drift), dropped through
  `FloorItemManager` so it inherits the usual visual, pickup and cleanup. A bare-handed or
  wrong-tool break is cancelled rather than silently unrewarded. The 10-block radius is deliberately
  wider than a player's ~5-block reach, so the fight always starts before the block can be attacked;
  `MAX_LIVE_MOBS_PER_SPAWNER` (6) is what stops an ignored spawner filling the dungeon. The vanilla
  spawner logic inside the placed block is neutralised (`setSpawnCount(0)` + a huge delay), or it
  would produce untagged mobs alongside ours. `mobsForDepth` is a pure static (like `spawnsRustyKey`)
  widening pool and wave size with depth: 1 mob below depth 3, 2 below 6, 3 beyond. Every spawned mob
  is PDC-tagged (`sot_dungeon_mob` + team UUID), which is what separates a designed encounter from
  the mobs vanilla spawns in any dark room — only tagged mobs are tracked, removed by
  `clearTeamState`, and counted toward `SoTPlayerData.monstersKilled`. That counter is why
  `GameManager` now owns a `SoTPlayerManager` (previously dead code, constructed nowhere); it is
  keyed by UUID rather than `Player` identity, since a reconnect hands out a fresh instance. The
  spawn task is owned by `GameManager` like `GameScoreboardManager` — started in `beginPlay`, stopped
  via `clearAllTeamStates` in `tearDownRound` — and its clock is injected so the cadence is testable
  without a scheduler. Mob hardening (`setRemoveWhenFarAway`, `setShouldBurnInDay`), the block
  configuration and the wave-spread passability check are all wrapped in best-effort try/catch: an
  implementation that does not support them should downgrade the encounter, not abort the spawn — and
  MockBukkit throws on all three. **The bundled hub declares no `MOB_SPAWNER` markers**, so a stock
  server sees no mobs until a segment carrying them is saved.
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
- **Dungeon generation is seeded, and the reseed is per call, not per attempt.** `dungeon.seed` in
  `config.yml` (read by `SoTConfig.readSeed`, applied via `GameManager.setDungeonSeed`, editable live
  with `/sot seed`) fixes the layout; blank means each round rolls its own.
  `DungeonGenerator.generateDungeonLayout` reseeds `random` once at the **top of the call** and logs
  the seed either way, so an unseeded round can be replayed from `getLastUsedSeed()`. Reseeding
  inside the 20-attempt retry loop instead would be a real bug: the attempts share one RNG stream on
  purpose — each retry consuming fresh draws is exactly what lets a layout that failed validation
  succeed on the next try — so a per-attempt reseed would make all 20 attempts byte-identical and
  turn one validation failure into twenty copies of itself. Two supporting pieces are easy to miss.
  **(a)** `StructureLoader` sorts `listFiles()` by name, because `File.listFiles` has no defined order
  and the seed indexes into `availableSegments` (and `findHubTemplate` takes the first HUB); unsorted,
  the same seed silently produced a different dungeon on a different machine. **(b)** Population is
  seeded too, via *salted sub-seeds* off `GameManager.getRoundSeed()` rather than a continuation of
  the generator's stream — `DungeonManager.populationRandom` for the rusty-key/sand rolls (identical
  for every team, so team dungeons match item-for-item) and `VaultManager.vaultRewardSeed(seed, color)`
  for reward scatter, which must be a pure function of seed and colour since it is drawn when a
  player opens a vault and would otherwise depend on which team got there first. `FloorItemManager`'s
  loot RNG is therefore a *parameter* of `spawnGenericItem`, not a field: one manager serves every
  team, so a shared instance RNG would interleave their draws beyond any seed's reach. The
  `UUID.randomUUID()` calls next to all this stay random — they are identity tokens for tracked
  entities and never affect what spawns.
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
  clear between rounds, and no `BlockBreakEvent` handler either — an ender chest mined without silk
  touch drops 8 obsidian and takes the team's bank out of the hub, but `ENDER_CHEST` is absent from
  the `BreakableBlocks` whitelist, so `BlockProtectionListener` already refuses the break at `LOW`.
  A bank-specific guard on top of that double-messaged the player (chat line over the listener's
  action bar) and cancelled for an admin in Creative, whom the listener deliberately waves through —
  the same contradiction that removed `SandManager`'s timer-column guard. `BankingManagerTest`
  registers the listener so the property stays pinned. A hub with no `BANK` marker simply plays with
  no bank, warned once per `/sot setup` via `warnOncePerGeneration`.
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
  escape path, and their sand would otherwise buy free time next round. Breaking a block of a visual
  timer column is refused for the same reason — the column is sand, and a mined block is restored by
  the next `syncVisualState()`, which the resulting deposit itself triggers — but that refusal lives
  in `BlockProtectionListener`, **not** here. `SandManager.onBlockBreak` used to re-check the
  breaker's own column; the listener subsumes it (every team's column, the whole live round) and the
  duplicate contradicted the Creative bypass, so it was removed along with the team lookup that only
  existed to serve it.
- **Sacrifice points are segment markers, and the chest is what makes one exist.** `SAND_SACRIFICE`
  and `DEATH_CAGE` markers were captured, serialized and loaded but never read — `DungeonManager`
  invented four cages at `absHubLocation ± {3,0,3}` instead. That anchor is the hub's *origin corner*
  (the hub is placed at `BlockVector3.ZERO`), so on the 42x15x37 bundled hub two of the four landed at
  negative coordinates, outside the dungeon. Both markers now flow through the usual chain:
  `Segment.getDeathCageOffsets()` / `getSandSacrificeLocations()` →
  `DungeonGenerator.selectDeathCageRelativeLocations` / `selectSandSacrificeRelativeLocations` →
  `reconcileSacrificePoints` → `DungeonBlueprint` → `DungeonManager` zips them into `DeathCage`s.
  The generator guarantees the two blueprint lists are **the same length and index-aligned**, which is
  what lets the zip run without null handling (`DeathCage`'s only constructor takes a `@NotNull` point).
  Pairing is positional — the Nth marker frees the Nth cage — and any cage the template leaves
  unpaired gets a point derived beside it. **`placeSacrificePoints()` is what makes a point exist**:
  the marker records an *air* cell, so registering the `DeathCage` without writing a block leaves
  nothing to right-click, exactly the trap `Door.buildClosed()` documents. The chest is forced to
  `Chest.Type.SINGLE`, since two adjacent points would otherwise pair into a double chest and move the
  block a click lands on. The derivation exists because `installBundledSegments()` is skip-if-present:
  a re-saved `hub.json` never reaches a server that already has one, so without it every existing
  server would stay unrevivable until someone re-saved the hub by hand.
- **The sacrifice derivation moves every cage by one shared step.** `reconcileSacrificePoints` offsets
  unpaired cages 2 blocks along a single cardinal direction — the dominant component of (hub centre −
  cage centroid) — rather than resolving a direction per cage. Per-cage "toward the centre" breaks on
  exactly the layout hubs use: the bundled hub's four cages sit in a row along X at z=4, and resolving
  each independently flips two of them onto the X axis and leaves the row incoherent. One shared
  translation keeps the chests in front of the row and, because translating distinct cells by a single
  vector cannot collide them, *guarantees* no two cages share a chest. Ties and a cage sitting exactly
  on the centre resolve to +Z so the result is deterministic; tests pin all of it.
- **A sand trade point is the same chest as a sacrifice point, and the marker is the only difference.**
  `SAND_TRADE` markers ride the usual chain — `Segment.getSandTradeLocations()` →
  `DungeonGenerator.selectSandTradeRelativeLocations` → `DungeonBlueprint` → `Dungeon` →
  `DungeonManager.placeSandTradePoints` — and the chest, forced to `Chest.Type.SINGLE`, is again what
  makes the point exist (the marker records an air cell). Two things set it apart from every other
  selector here. **(a)** It is deliberately **not hub-wins**: a trade point pays more the deeper it
  sits, so gathering only the HUB's markers whenever the hub carried one would throw away every trade
  point in the dungeon and leave only depth-0 ones. **(b)** The payout is resolved at *click* time,
  not at setup — `SandManager.attemptSandTrade` asks `GameManager.getTeamDepthAt` →
  `DungeonManager.getDepthAt`, because `placedSegmentsInWorld` (the only thing that knows a location's
  depth) lives on the manager and the `Dungeon` is built before the chests are placed.
  `TRADE_COINS_PER_SAND` (25, five coin stacks at the hub) goes through
  `ScoreManager.awardDepthScaledCoins`, the extracted coin-pickup path, so a trade and a pickup share
  the 100%–120% multiplier *and* the batched `CoinPickupNotifier` message; the coins land **unbanked**,
  which is what keeps the trade a gamble. `SandManager.onPlayerInteract` handles both chests because
  they are the same block — it checks sacrifice first (a caged teammate is the meaning with a
  deadline), and the `EquipmentSlot.HAND` guard still sits *after* `setCancelled` or the off-hand pass
  opens the chest and a single click spends two sand. Nothing protects the chests specially:
  `BreakableBlocks.BREAKABLE` whitelists `SAND` and `SPAWNER`, so a `CHEST` is already refused.
- **Reviving costs the dead player's death count, paid a sand at a time.** The price and the
  part-payment live on `DeathCage`, which is already 1:1 with a player for the round, so they are torn
  down with the dungeon and reset per round with nothing having to clear them (`SoTPlayerData.timesDied`
  is *not* usable for this — `SoTPlayerManager` is never instantiated anywhere in `src/main`).
  `recordDeath()` raises the price and discards leftover part-payment, so a player revived at cost 2 who
  dies again does not inherit what was already paid. `SandManager.attemptRevive` consumes exactly one
  sand per click and only revives on the click that completes the total, which is what lets several
  teammates chip in. Sand paid into a revive that never completes is spent, not refunded. The interact
  handler gates on `CHEST` and **cancels before the team lookup** — a sacrifice point is a real chest, so
  returning early for a player with no team would let them open it.
- **The floating sand above a chest is the activity signal, and is event-driven.** `SacrificeIndicatorManager`
  spawns a `BlockDisplay` of sand plus a `TextDisplay` arrow and count above a chest only while its cage
  holds someone awaiting revive; a chest with nothing above it has nobody to free. Like
  `GameScoreboardManager` it is owned by `GameManager` and is **not** a listener, so there is nothing for
  `SoT.onEnable()` to register — but unlike the scoreboard it runs no task, because the numbers only
  change on death, payment, revive and timer-out. Its displays are `setPersistent(false)` and sit inside
  the dungeon bounds, so `cleanupInstance()` removes them with every other non-player entity; `clearAll()`
  in `tearDownRound()` is there to empty the manager's own map rather than to do the removing.
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
- **During a round, players may break only sand and spawners — and never the timer column.**
  `BlockProtectionListener` (in `events`) cancels every other block break, and every block placement
  except depositing sand on the team's own `TIMER_DEPOSIT` cell (which has to reach
  `SandManager.onBlockPlace` at `NORMAL`, so it is let through — gated on `RUNNING` to mirror that
  handler exactly), while `GameManager.isRoundLive` is true (COUNTDOWN/RUNNING/PAUSED, so the hub's baked sand shaft is
  covered before the round even starts). `BreakableBlocks.BREAKABLE` is the whitelist and the
  documented home for the not-yet-implemented money blocks. **The `LOW` priority is load-bearing:**
  `SandManager.onBlockBreak` sits at `NORMAL, ignoreCancelled = true`, so cancelling at `LOW` makes
  Bukkit skip it and a denied break pays out nothing — raise the priority and the sand-timer exploit
  comes back (mining a column block credited +10s, and `TeamTimer.addSeconds` → `syncVisualState` →
  `addSandToTop` put the block straight back, pinning the timer at its maximum forever). The column
  guard runs *before* the `isParticipant` check so nobody, operators included, can mine a team's
  clock, and `VisualSandTimerDisplay.isColumnBlock` is deliberately **not** gated on `armed` —
  the unarmed window is exactly the countdown, when the baked shaft is minable. Creative/Spectator
  bypasses everything; there is no bypass permission.
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
  **The hub must declare itself tall enough to hold the column it anchors.** The column occupies
  relative Y `timerLocationOffset.y + 1` through `+ VisualTimerLayout.COLUMN_HEIGHT_BLOCKS`, but the
  blueprint bounds come from the template's declared `size` — rotated, via `PlacedSegment.getRotatedSize()`
  (`DungeonGenerator.calculateRelativeMaxBounds`; the unrotated size under-covered any non-square segment
  placed at 90/270 and left its overhang standing between rounds) —
  and those bounds are exactly what `DungeonManager.cleanupInstance()` air-fills between rounds.
  `hub.json` therefore declares `size.y = 17` while `hub.schem` is only 15 tall — the two extra
  layers are air and `ignoreAirBlocks` means they cost nothing to paste. Under-declare it and the top of
  every team's column is left standing for the next round, which cannot clear it either. This is easy
  to lose: re-saving the hub in game rewrites `size` from the WorldEdit selection, so **select at least
  17 blocks of height** or the gap reopens. `StructureLoaderHubFeaturesTest` pins it, deriving the bound
  from the constants, and `StructureLoader.warnIfDeclaredSizeIsTooSmall` catches the general case at
  load — but only a size *below* the schematic's, since this over-declaration is the point.
- **A location belongs to a team by *region*, not by segment.** `GameManager.getTeamIdForLocation` was a
  stub returning null for every location — public, `@Nullable`, with a javadoc describing an
  implementation that did not exist, so any caller trusting it silently got "no team" with no
  exception, no log and no test failure (bug #98). It now walks `teamDungeonManagers` and returns the
  team whose `DungeonManager.containsLocation` matches. That test is the instance's `absoluteBounds`
  — the blueprint's relative bounds translated by the team's origin, computed once at construction and
  now the single source `cleanupInstance()` reads too, so the region a lookup claims and the region
  teardown clears cannot drift. Two consequences are worth knowing. **(a)** It is region-level: a
  location in the air *between* two of a team's rooms still resolves to that team, because the whole
  cuboid is theirs and is cleared as theirs. Use `DungeonManager.getSegmentAtLocation` when a segment
  actually has to be there. **(b)** `TEAM_DUNGEON_SPACING` (5000 on X) is what makes it unambiguous —
  the regions cannot overlap, so no tie-break is needed and the scan is six coordinate comparisons per
  team, cheap enough for an event handler; a per-segment scan and the origin-distance pre-filter it
  would need are both unnecessary. The world check lives in `regionContains`, not in `Area.contains`,
  which compares coordinates only — every world shares a coordinate space. `isVisualTimerBlock` still
  scans every team rather than resolving one, and deliberately: the operator who teleported into
  someone else's hub is the case worth covering, and the answer would not change.
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

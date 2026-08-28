# Sands of Time (Minecraft Plugin)

## Introduction

Welcome to the **Sands of Time** plugin! This project aims to recreate the thrilling, high-stakes *Sands of Time* game mode from the **MC Championship (MCC)** event within Minecraft.

Teams of players enter their own procedurally generated dungeons, racing against their individual team timers to explore, overcome challenges, collect hidden gold, and unlock valuable vaults. Managing the timer by collecting and using sand is crucial for survival. Players must decide whether to risk delving deeper for greater rewards or bank their earnings safely, all while coordinating with their team to revive fallen members and escape before their time runs out!

---

## Features

This plugin implements (or plans to implement) the following core features of the Sands of Time game mode:

### Procedural Dungeon Generation
- Dungeons are created dynamically using a configurable segment-based system.
- Allows for diverse layouts with different room types, corridors, and challenges.
- **(Uses WorldEdit schematics).**

### Per-Team Timers
- Each team manages its own independent timer:
  - **Logical Timer**: Tracks remaining time in seconds.
  - **Visual Timer**: A physical structure of sand blocks depletes visually, synchronized with the team's remaining time.

### Sand Mechanics
- **Collection**: Players find and collect sand within the dungeon.
- **Timer Extension**: Use collected sand at the central timer mechanism to add seconds to the team's clock (e.g., 1 sand = 10 seconds).
- **Revival**: Fallen teammates can be revived by sacrificing a specific amount of team sand.
- **Sand Sacrifices**: Specific points in the dungeon may allow teams to sacrifice sand for rewards or to overcome obstacles *(Planned)*.

### Coin Collection & Scoring
- Players collect coins scattered throughout the dungeon.
- Coin value may increase based on dungeon depth *(Planned)*.
- Players lose a percentage of unbanked coins upon death.
- Players lose **all** unbanked coins if trapped when their timer expires.

### Banking & Exiting
- **Sphinx Banking**: Players can bank collected coins mid-game at a central Sphinx location, incurring a percentage tax *(Planned)*.
- **Safe Exit**: Players can leave the dungeon permanently through a designated exit, securing all their currently held coins without tax.

### Vaults & Keys
- Four colored vaults: **Blue**, **Red**, **Green**, **Gold**.
- Corresponding colored keys are required to open vaults.
- Specific placement rules for certain keys/vaults (e.g., Blue Key under timer, Green Vault near hub).
- Vaults contain significant coin rewards.

### Team Management
- Supports the standard 10 MCC teams (Red Rabbits to Pink Parrots).

### Death & Consequences
- Players who die are temporarily trapped and must be revived by teammates using sand.

### Game Lifecycle Management
- Commands to set up, start, and end game instances via a central `GameManager`.

---

## Gameplay Overview

1. **Setup**: Admins assign players to teams and configure game parameters (locations for hub, exit, cage).
2. **Start**: The game begins, the dungeon is generated, and teams are teleported to the central hub. Each team's timer starts counting down.
3. **Explore & Collect**: Players venture into their team's unique dungeon instance, collecting sand and coins. They face mobs, parkour, and puzzles.
4. **Manage Time**: Players return to the hub periodically to deposit sand into their team's timer mechanism, adding precious seconds.
5. **Bank or Escape**:
   * Bank coins at the Sphinx (taxed).
   * Leave permanently via the safe exit (no tax). *Leaving means they cannot return.*
6. **Vaults**: Find colored keys and locate the corresponding vaults to unlock substantial coin bonuses.
7. **Death**: Dead players are trapped. Teammates must use sand to revive them. The dead player loses a portion of their held coins.
8. **Timer End**: If a team's timer runs out, any members still inside are trapped, and all unbanked coins are lost.
9. **Game End**: The game concludes after a set duration or when manually stopped. Final scores are tallied based on banked/escaped coins.

---

## Commands

> *`(Note: These commands reflect the planned structure using GameManager. Update as implemented.)`*

* `/sot setup [numTeams]`
  Initializes a game instance, spreading the online players round-robin across `numTeams` teams (default 1).

* `/sot start`
  Starts the currently configured game instance.

* `/sot end`
  Forcefully ends the current game instance.

* `/sot set <lobby|trapped>`
  (Admin) Stores your current position as one of the two universal game locations, writes it to `config.yml` and applies it immediately. Hub, Exit, and Cage are instance-specific and are not set this way.

* `/team assign <Player> <TeamID>`
  (Admin - if using internal `TeamManager`) Assigns a player to a team definition.

* `/sotsavesegment <name> <type> <schematic_filename> [totalCoins]`
  (Admin) Saves the current WorldEdit selection as a new segment template. Requires marker entities/blocks within selection for entry/spawn points.

* `/sotgetcointool <value>`
  (Admin) Gives a tool to place visual coin displays with the specified value.

* `/sotgetitemtool`
  (Admin) Gives a tool to place item spawn markers (Torch + hidden entity).

---

## Setup & Installation

### Prerequisites

* A Minecraft server running **Paper (or a compatible fork)** version **26.2** or newer.
* **WorldEdit** 7.4.5 or newer (the first release line covering 26.2).
* **Java 25** or newer installed on the server.

### Build

* Clone the repository.
* Ensure your local environment (JDK, Maven) is set up for Java 25.
* Build the project using Apache Maven: `mvn clean package`

### Install

* Place the generated `.jar` file (e.g., `SoT-1.0-SNAPSHOT.jar` from the `target/` directory) into your server’s `plugins` folder.
* Place the required dependency plugin `WorldEdit.jar` in the plugins folder.

### Configuration

* **Segment Schematics & Metadata**:
  * Build segments in-game.
  * Use marker tools (`/sotgetcointool`, `/sotgetitemtool`, etc. - *Need implementation for entry points, vaults, keys*) to place markers within the segment build.
  * Take the master builder tool with `/sotbuilder` and switch marker type with `/sotmode <mode>`. `SAFE_EXIT` marks the block players interact with to escape - place one in your HUB segment, clicking its top face.
  * Select the segment region with WorldEdit.
  * Use `/sotsavesegment <name> <type> <filename.schem>` to save both the schematic to `plugins/SoT/schematics/` and the metadata JSON to `plugins/SoT/`.
  * The plugin will load these templates on startup from the `plugins/SoT/` directory. Default templates can be bundled in `src/main/resources/default_segments/` and copied out on first run.
* **Game Locations**:
  * Use `/sot set lobby` while standing at the desired main world anchor point (e.g., pre-game lobby).
  * Use `/sot set trapped` while standing at the location where players trapped by the timer should be sent.
  * (Hub, Safe Exit, Death Cage are now generated per-instance based on segment metadata and are not set globally). The safe exit comes from the `SAFE_EXIT` marker; a dungeon with no such marker falls back to accepting any End Portal Frame near the hub.
  * Both live in `plugins/SoT/config.yml` and can be edited by hand instead, followed by a restart:

    ```yaml
    locations:
      lobby:
        world: world
        x: 0.5
        y: 100.0
        z: 0.5
        yaw: 0.0
        pitch: 0.0
      trapped:
        world: world
        x: 10.5
        y: 100.0
        z: 10.5
        yaw: 0.0
        pitch: 0.0
    ```

  * Both ship unset. The plugin still enables without them (falling back to the main world's spawn
    and logging a warning) so that `/sot set` is reachable, but `/sot setup` and `/sot start` refuse
    to run until both are configured.
* **Visual Timers**:
  * The visual timers are placed relative to the `lobby` location set above. Ensure the surrounding area is suitable.

### Start Server

* Start your Minecraft server. The plugin should load. Check console for errors.

---

## Dependencies

* **Paper API**: 26.2 required (uses Java 25). Paper moved to calendar versioning, so 26.2 is
  newer than the old 1.21 line.
* **WorldEdit**: Required for dungeon schematic pasting and segment saving. 7.4.5 or newer, to
  match MC 26.2.
* **Adventure API**: Provided by modern Paper versions.

---

## Architecture Overview (Key Components)

- **GameManager**: Central coordinator for game state, lifecycle, and interaction between managers. Holds universal locations (lobby, trapped).
- **DungeonGenerator**: Generates the relative `DungeonBlueprint` using loaded `Segment` templates and DFS.
- **DungeonBlueprint**: Represents the relative layout and feature locations of a generated dungeon.
- **DungeonManager**: Manages a specific team's dungeon instance. Instantiates the `DungeonBlueprint` at an absolute world origin, pastes schematics, places features, creates the `Dungeon` data object.
- **Dungeon**: Holds the data for a specific, live dungeon instance (absolute locations of hub, vaults, keys, spawns for that instance).
- **TeamManager**: Manages team definitions and player assignments.
- **SoTTeam**: Represents an active team in a game instance, holding game state like score and its `TeamTimer`.
- **TeamTimer**: Manages the logical countdown for a team.
- **VisualSandTimerDisplay**: Manages the physical sand block visuals for each team's timer (placed relative to lobby).
- **PlayerStateManager**: Tracks the status of each player (alive, dead, escaped, trapped).
- **SandManager**: Manages sand collection and usage logic (timer extension, revives). *(Needs Implementation)*
- **VaultManager**: Handles vault/key placement (via `DungeonManager`/`Dungeon`) and interaction logic within instances. Manages vault open state per team.
- **ScoreManager**: Manages coin collection, value scaling (based on `PlacedSegment` depth), scoring rules, and penalties. *(Needs Implementation for scaling/banking)*
- **BankingManager**: Handles Sphinx banking interaction. *(Needs Implementation)*
- **StructureLoader**: Utility class used by `DungeonGenerator` to load `Segment` templates from JSON files.
- **StructureSaver**: Utility class used by `/sotsavesegment` command to save segment schematics and JSON metadata.
- **FloorItem Interface & Implementations (CoinStack, Key, etc.)**: Define items found in the dungeon. *(Needs implementation for Key, FloorLoot)*
- **ToolListener & Commands**: Handle builder tools and game setup/control commands.

---

## License

This project is licensed under the **MIT License**. See the `LICENSE` file for details.

---

## Author

Created by **Zac Clarkson**.


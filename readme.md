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
   - Bank coins at the Sphinx (taxed).
   - Leave permanently via the safe exit (no tax). *Leaving means they cannot return.*
6. **Vaults**: Find colored keys and locate the corresponding vaults to unlock substantial coin bonuses.
7. **Death**: Dead players are trapped. Teammates must use sand to revive them. The dead player loses a portion of their held coins.
8. **Timer End**: If a team's timer runs out, any members still inside are trapped, and all unbanked coins are lost.
9. **Game End**: The game concludes after a set duration or when manually stopped. Final scores are tallied based on banked/escaped coins.

---

## Commands

> *(Note: These commands reflect the planned structure using `GameManager`. Update as implemented.)*

- `/sot setup <TeamID1> <TeamID2> ...`  
  Initializes a game instance with the specified teams. Requires prior player assignment via `/team` commands (if using internal `TeamManager`) or external system.

- `/sot start`  
  Starts the currently configured game instance.

- `/sot end`  
  Forcefully ends the current game instance.

- `/sot set <hub|exit|cage>`  
  (Admin) Sets the crucial locations for the game.

- `/team assign <Player> <TeamID>`  
  (Admin - if using internal `TeamManager`) Assigns a player to a team definition.

---

## Setup & Installation

### Prerequisites
- A Minecraft server running **Paper (or a compatible fork)** version 1.20+ (ensure Adventure API support).
- **WorldEdit** plugin installed.

### Build
- Clone the repository.
- Build the project using Apache Maven: mvn clean package

### Install
- Place the generated `.jar` file (e.g., `SoT-1.0-SNAPSHOT.jar`) into your server’s `plugins` folder.
- Place the required dependency plugin `WorldEdit.jar` in the plugins folder.

### Configuration
- **Segment Schematics**:  
Create your dungeon segment `.schem` files using WorldEdit and place them in the `plugins/SoT/schematics/` directory.

- **Segment Metadata**:  
Create corresponding `.json` metadata files for each schematic in the `plugins/SoT/` directory.  
These JSON files define entry points, spawn locations, vault/key info, segment type, etc.  
*(Refer to `Segment.java` and `StructureLoader.java` for expected structure.)*

- **Game Locations**:  
Use admin commands (`/sot set hub`, `/sot set exit`, `/sot set cage`) or a configuration file *(Planned)* to define:
- Central hub location
- Safe exit location
- Death cage location for the game world

- **Visual Timers**:  
Ensure the areas designated for each team's visual sand timer are clear and suitable.

### Start Server
- Start your Minecraft server. The plugin should load.

---

## Dependencies

- **Paper API**: 1.20+ recommended (for Adventure API support).
- **WorldEdit**: Required for dungeon schematic pasting. Version 7.2+ recommended.
- **Adventure API**: Provided by modern Paper versions.

---

## Architecture Overview (Key Components)

- **GameManager**: Central coordinator for game state, lifecycle, and interaction between managers.
- **DungeonManager**: Handles procedural generation based on segment templates.
- **TeamManager**: Manages team definitions and player assignments.
- **SoTTeam**: Represents an active team in a game instance, holding game state like sand count, score, and its own timer.
- **PlayerStateManager**: Tracks the status of each player (alive, dead, escaped, trapped).
- **SandManager**: Manages sand collection and usage logic (timer, revives, sacrifices).
- **VaultManager**: Handles vault/key placement and interaction logic.
- **ScoreManager**: Manages coin values, scoring rules, and penalties.
- **VisualSandTimerDisplay**: Manages the physical sand block visuals for each team's timer.
- **GameListener**: Central Bukkit event listener delegating actions to the GameManager.

---

## License

This project is licensed under the **MIT License**. See the `LICENSE` file for details.

---

## Author

Created by **Zac Clarkson**.

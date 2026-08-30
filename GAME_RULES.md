# Sands of Time — Complete Game Rules

Sands of Time is a team-based dungeon exploration minigame for Minecraft, inspired by MCC. Teams race against a shared sand timer to explore a procedurally generated dungeon, collect coins, open vaults, and escape before time runs out.

---

## Table of Contents

1. [Overview](#overview)
2. [Teams](#teams)
3. [The Hub](#the-hub)
4. [The Timer & Sand](#the-timer--sand)
5. [Dungeon Structure](#dungeon-structure)
6. [Coins & Scoring](#coins--scoring)
7. [Banking at the Sphinx](#banking-at-the-sphinx)
8. [Vault System](#vault-system)
9. [Keys](#keys)
10. [Doors & Gates](#doors--gates)
11. [Death & Corpse Run](#death--corpse-run)
12. [Trapping (Timer Expiry)](#trapping-timer-expiry)
13. [Escaping](#escaping)
14. [Floor Items & Loot](#floor-items--loot)
15. [Puzzle Rooms](#puzzle-rooms)
16. [Mobs](#mobs)
17. [Win Condition](#win-condition)
18. [Technical Constants Reference](#technical-constants-reference)

---

## Overview

- Multiple teams play simultaneously, each in their own independent dungeon instance
- A central sand timer counts down for each team — when it hits zero, anyone still inside is trapped
- Players explore dungeon branches, collect coins, find keys, open vaults, and bank their earnings
- The core tension: go deeper for more valuable coins, but risk losing everything if the timer runs out or you die

---

## Teams

There are 10 standard teams, following the MCC naming convention:

| # | Team Name | Color |
|---|-----------|-------|
| 1 | Red Rabbits | Red |
| 2 | Orange Ocelots | Orange |
| 3 | Yellow Yaks | Yellow |
| 4 | Lime Llamas | Lime |
| 5 | Green Geckos | Green |
| 6 | Cyan Coyotes | Cyan |
| 7 | Aqua Axolotls | Aqua |
| 8 | Blue Bats | Blue |
| 9 | Purple Pandas | Purple |
| 10 | Pink Parrots | Pink |

Each team operates independently with their own:

- Dungeon instance (spaced 5,000 blocks apart)
- Sand timer
- Score (banked + unbanked)
- Vault progress

---

## The Hub

The hub is the central starting area for each team's dungeon. It serves as a safe zone and contains critical facilities.

### Hub Exits

The hub has approximately **10 exits** leading into the dungeon. These exits fall into two categories:

**4 Vault Exits** — each marked with a colored indicator on the wall showing which vault color lies down that branch:

- Blue vault branch
- Green vault branch
- Red vault branch
- Gold vault branch

**~6 Other Exits** — lead to an assortment of **puzzle rooms** and **challenge rooms**. One of these puzzle rooms contains the **Red vault key**.

### Hub Facilities

- **Sand timer** — a physical column of sand blocks that drains as time passes (each block = 10 seconds visually)
- **Sphinx** — the banking NPC/location where players deposit coins
- **Death cage** — where dead players respawn, awaiting rescue
- **Timer deposit points** — the cells beside the sand column where carried sand is spent on the timer
- **Sand sacrifice points** — one per player on the team, used to free teammates from the death cage
- **Safe exit** — the block players interact with to leave the dungeon permanently
- **Blue key spawn** — the blue vault key is always available in the hub (every hub must have a blue key spawn location)
- **Green vault** — located near the hub for easy access (but its key must be found in the dungeon)
- **Puzzle rooms** — accessible from the hub, including one containing the Red vault key (see [Puzzle Rooms](#puzzle-rooms))

---

## The Timer & Sand

### Timer

- Each team starts with **150 seconds** (2 minutes 30 seconds)
- The timer counts down once the game begins
- Maximum timer value is capped at 150 seconds (cannot exceed starting amount)
- Timer ticks once per second (every 20 game ticks)

### Visual Sand Timer

- A vertical column of sand blocks in the hub represents the timer
- Each sand block in the column represents **10 seconds**
- As the timer ticks down, sand drains from the bottom
- 150 seconds = 15 blocks of sand

### Sand Items

- Sand spawns throughout the dungeon as **normal sand blocks** placed in the world
- Players must **break the sand with a shovel** to pick it up (like mining normal sand)
- Breaking sand puts a **sand item in your inventory**. It adds **no time on its own** — carrying it
  back is the whole point
- Sand is also used to **free teammates** from the death cage (see [Death & Corpse Run](#death--corpse-run))
- Sand spawn locations have a **40% chance** of actually spawning sand per location
- Finding and returning sand to the timer is essential for survival

### Depositing Sand

- The hub has **timer deposit points** beside the sand column — the cells marked by the builder's
  `TIMER_DEPOSIT` tool
- **Place a sand block on a deposit point** to spend it: the block never stands, and the team timer
  gains **10 seconds**
- A deposit is **refused** while the timer is at its 150-second cap, and the sand stays in your
  inventory rather than being spent for nothing
- You **cannot mine your own team's timer column** for sand
- Escaping the dungeon **wipes your inventory**, so undeposited sand is lost; any sand still carried
  when the round ends is cleared too
- **Dying drops your sand** on the floor where you fell, like any other item — a teammate, or you
  after being revived, can pick it back up (see [Death & Corpse Run](#death--corpse-run))

### Sand Sacrifice

- Dedicated sacrifice points exist in the hub — **one per player** on the team
- A teammate must sacrifice sand at these points to free a player from the death cage
- Cost: **1 sand** per revive

---

## Dungeon Structure

### Generation

- Dungeons are procedurally generated using a **depth-first search (DFS)** algorithm
- Each team gets their own copy of the same dungeon layout (blueprint)
- Maximum dungeon depth: **`MAX_DEPTH` segments** from the hub (currently 12; the deepest vault, gold, maxes at depth 10)
- Maximum total segments per dungeon: **120**
- Generation attempts up to **20 retries** if validation fails

#### Seeding

- Generation is **seeded**, so a given seed always produces the same dungeon — the same rooms, vaults
  and keys, and the same loot and sand on the floor. Every team is populated from that one seed too,
  so team dungeons are identical rather than merely the same shape
- By default each round rolls its own seed. The seed used is always logged at generation, so a layout
  worth keeping can be captured and replayed afterwards
- Set a fixed seed with `dungeon.seed` in `config.yml`, or in-game with `/sot seed <value>`; a whole
  number is used directly and anything else is hashed, so `mcc-finals` is a valid seed. `/sot seed`
  on its own reports both the configured seed and the one the last round generated from

### Segment Types

Segments are the building blocks of the dungeon:

| Type | Description |
| --- | --- |
| **HUB** | Central starting area with all key facilities |
| **CORRIDOR** | Connecting passages between rooms |
| **SMALL_ROOM** | Compact chambers with encounters or loot |
| **LARGE_ROOM** | Spacious areas for combat or exploration |
| **VAULT** | Rooms containing a colored vault |
| **STAIRS** | Vertical transition segments |
| **PUZZLE** | Challenge rooms requiring puzzle-solving |
| **START** | Initial segment |
| **END** | Terminal segment |

### Segment Features
Each segment template can contain any combination of:
- **Entry points** — 3-wide x 4-tall openings that connect segments (cardinal directions: N/S/E/W). In the builder tool the marker is a single block with a **directional arrow that must point OUT of the segment** (toward where the neighbouring segment attaches). Two segments connect only when their entry points face **opposite** directions — the generator matches a neighbour via `direction.getOpposite()` (`DungeonGenerator.generatePathRecursive`), placing them so the two entries meet at the shared boundary.
- **Coin spawns** — locations where coins appear, with configurable base values
- **Sand spawns** — locations where sand items may appear
- **Item spawns** — locations for generic loot items
- **Mob spawners** — locations for hostile mob spawning
- **Sand sacrifice points** — where sand can be sacrificed
- **Vault markers** — activation block for a vault
- **Key spawns** — where vault keys are placed
- **Gates** — openings blocked until a lever is pulled
- **Vault doors** — openings blocked until the matching vault is opened
- **Levers** — interact to open all gates in the segment
- **Color marking placeholder** — every segment needs a placeholder location for a color marking on the wall, used to indicate which vault branch the segment belongs to
- **Safe exit** — a **2D area (like a door) built as a nether portal** that players walk through to escape. Placed in the builder as a two-click bound (`SAFE_EXIT`), normally in the HUB; one per dungeon, and a HUB marker takes priority over one on any other segment. Vanilla Nether travel is suppressed so the portal never teleports anyone out (`NetherPortalListener`).

The HUB segment additionally defines these hub-only features (placed in the builder, wired to the live dungeon in a later pass):
- **Bank** — a single interact point (`BANK` marker) marking where the banking Sphinx / bank spot lives
- **Death cages** — 1–4 points (`DEATH_CAGE` markers), one per player, where dead players are held and respawn; each cage's revive/sacrifice point is auto-derived at runtime
- **Timer deposits** — interact points (`TIMER_DEPOSIT` markers) where players place collected sand onto the timer to add time
- **Timer column** — a single `TIMER` marker at the base of the visual sand-timer column; the draining sand timer stands in the hub at this marker (per team). A HUB with no TIMER marker simply gets no visual column that round (the timer still counts down normally) — the column is never placed anywhere but the hub.

### Depth & Difficulty

- The hub is at depth 0
- Each connected segment increments depth by 1
- Deeper segments contain slightly more valuable coins
- Harder vaults (Red, Gold) are placed at greater depths
- **Depth multiplier**: coins are worth between **100%** (at the hub) and **120%** (at maximum depth) of their base value — a gentle scaling that rewards exploration without making shallow coins worthless

---

## Coins & Scoring

### Coin Types
Coins spawn throughout the dungeon as visual displays on the ground. They come in three visual sizes based on their base value:

| Size | Base Value | Custom Model ID |
|------|-----------|-----------------|
| Small | < 20 | 1001 |
| Medium | 20–49 | 1002 |
| Large | ≥ 50 | 1003 |

### Collection
- Players collect coins by walking within **1.5 blocks** of them
- Collected coins are added to the player's **unbanked score**
- Coin value is scaled by the segment's depth multiplier (100%–120% range)
- Coins use gold nuggets as their base material with custom model data

### Unbanked vs. Banked
- **Unbanked coins**: Coins you've collected but haven't deposited at the Sphinx. These are at risk.
- **Banked coins**: Coins deposited at the Sphinx. These are safe (minus the tax).
- Only banked coins count toward the final team score.

### Penalties

- **Death**: Drop **all** items, undeposited sand included, at the death location (no percentage penalty — everything dropped can be recovered if you get back in time). Unbanked coins are cleared rather than dropped
- **Trapped (timer expires)**: Lose **ALL** unbanked coins — total wipeout

### Live Scoreboard

While a round is running every player sees a **sidebar** listing their own unbanked coins, their
team's banked coins, and the standings — every team ordered by banked coins, ties broken by name,
with the viewer's own team highlighted. The sidebar holds 15 lines: three viewer lines, a heading,
and a row for each team.

The **sand timer never appears on the sidebar**. Teams read their remaining time off the sand column
in the hub and call it out to each other — putting a countdown on every screen would take that job
away from the team.

The sidebar refreshes once per second and disappears when the round ends, at which point the final
scores are broadcast in chat.

---

## Banking at the Sphinx

- The Sphinx is located in the hub
- Players must physically return to the hub and interact with the Sphinx to bank coins
- Banking applies a **20% tax** — you keep 80% of what you deposit
- Formula: `banked_amount = coins_to_bank × 0.80`
- The taxed 20% is lost (destroyed, not redistributed)
- This creates a constant risk/reward decision: bank frequently (lose more to tax but secure coins) vs. bank rarely (keep more but risk losing everything)

---

## Vault System

There are **4 vault colors**, each with a unique challenge profile. Vaults contain high-value rewards.

### Vault Overview

Depths must fit within the generator's `MAX_DEPTH` (currently 12), so the ranges below are the values
the generator actually uses (`DungeonGenerator.VAULT_DEPTH_RANGES`). They are tunable.

| Color | Key Location | Vault Location | Difficulty | Depth Range (Vault) |
|-------|-------------|----------------|------------|---------------------|
| **Green** | Must find in dungeon | Near the hub (easy access) | Easy | Depth 2–4 |
| **Blue** | Free — blue key spawns in the hub | Must find in dungeon | Easiest | Depth 3–6 |
| **Red** | In the hub's key room | Must find in dungeon | Medium | Depth 5–8 |
| **Gold** | Must find in dungeon | Must find deep in dungeon | Hardest | Depth 7–10 |

Vaults and keys are placed **opportunistically**: any not-yet-placed vault/key drops onto whichever
branch first reaches its depth range via a connecting doorway (no dedicated colored branch). The red key
no longer requires a puzzle-room segment (puzzle rooms aren't implemented yet) — a normal key room works,
and it will naturally prefer a puzzle room once those exist.

### Vault Mechanics
- Each vault has a **colored marker block** that serves as the activation point
- Right-click the marker block with the matching colored key to open the vault
- The key is **consumed** on use
- Opening a vault changes the marker block to glass and plays sound effects
- Only one of each vault color exists per dungeon

### Vault Block Materials

| Color | Block Material |
|-------|---------------|
| Blue | Blue Concrete |
| Green | Lime Concrete |
| Red | Red Concrete |
| Gold | Gold Block |

### Vault Door
- Each vault may have an associated vault door (a wall of colored blocks)
- The vault door opens when the vault is opened
- Vault doors animate by removing blocks top-to-bottom with piston sounds
- Once opened, vault doors cannot be closed again

---

## Keys

### Vault Keys

- Physical items (tripwire hooks) with persistent data tags identifying their color
- Each key matches one vault color (Blue, Green, Red, Gold)
- Keys are consumed when used to open a vault (right-click the keyhole block)

### Key Placement Rules

| Key Color | Where to Find | Notes |
|-----------|---------------|-------|
| **Blue** | Spawns in the hub | Every hub **must** have a blue key spawn location |
| **Green** | Found in dungeon segments | Can spawn on any of the 3 deeper branches (not the green branch — it's too shallow) |
| **Red** | In a puzzle room accessible from the hub | Always in the hub's puzzle room area |
| **Gold** | Found deep in the dungeon | Can spawn on any of the 3 deeper branches (not the green branch) |

### Key Spawn on Branches

- There are 4 vault branches (Blue, Green, Red, Gold)
- The **Green branch** is too shallow for key spawns
- Keys for Green and Gold can spawn on **any of the other 3 branches** (Blue, Red, Gold)
- This means players must explore multiple branches to find all keys

### Rusty Keys

- Used to open standard **segment doors** (not vault doors)
- Separate from colored vault keys
- Right-click the door's keyhole block with a rusty key to open
- Found throughout the dungeon: each `ITEM_SPAWN` marker has a **20% chance** of yielding a rusty
  key instead of rolling the loot table
- Placement is by chance, not one guaranteed key per room, so a branch can come up short and stay
  shut for the round

---

## Doors & Gates

### Segment Doors (Rusty Doors)

- Block walls between connected segments that block passage
- One door per **connection the generator actually made** — a segment's entry points that no
  neighbour was attached to are sealed with plain wall instead, so no door opens onto nothing
- Built from **dark oak planks**, filling the 3-wide x 4-tall opening around the entry point marker
- Each door has a **keyhole block** — a block of **oxidized cut copper** one block above the entry
  point marker, at eye level in the middle of the door
- Opened with **rusty keys**: right-click the keyhole with a rusty key to open
- The key is consumed on use

### Gates

- Block walls **local to a segment** that restrict access to areas **within that segment only**
- Gates do **not** block access to other segments — they only gate off optional areas within their own segment
- This creates a **choice mechanic**: e.g., "There is a set of coins here but it's guarded by ravagers — do you open the gate?"
- Opened by pulling the segment's **lever**
- A segment can have multiple gates, all opened by one lever
- Every segment with gates **must** have exactly one lever
- Rendered as gray stained glass in the builder tool

### Vault Doors

- Colored block walls associated with a vault
- Each vault door has a **keyhole block** — right-click with the matching colored key to open
- The key is consumed on use
- Cannot be closed once opened
- Rendered as purple stained glass in the builder tool

### Door Animation

- When a door is opened it **sinks downward**: its topmost layer of blocks is cleared first, then
  the next, so the wall visibly drops into the floor
- Layers clear in sequence until the whole opening is air, leaving the passage clear for the player
- The keyhole block clears with the layer it sits in
- Animation tick delay: 3 ticks per layer (configurable, minimum 1)
- Sounds: piston contract/extend during animation, iron door open/close on completion
- Vault doors play additional sounds: end portal frame fill + player level up

---

## Death & Corpse Run

When a player dies while the timer is still active:

1. **All items drop** at the death location as a corpse/loot pile — **undeposited sand included**.
   Sand is the round's currency for both timer seconds and revives, so carrying it through the
   dungeon is a real risk; this holds even on a server running `keepInventory`, because it is a rule
   of the game rather than a server setting
2. **Unbanked coins are cleared outright** — they are a score, not an item, so there is nothing on
   the floor to run back for. Banked coins are safe. (The rest of the pile has no percentage
   penalty: it is all recoverable if you get back in time)
3. Player **respawns in the death cage** at the hub
4. Player is now in the **DEAD_AWAITING_REVIVE** state — they cannot leave the cage on their own
5. A **teammate must sacrifice 1 sand** at a sacrifice point to free the dead player from the cage
6. Once freed, the player is back at the hub and can **run back to their death location** to recover all dropped items — sand recovered this way deposits for time exactly as if it had never been dropped
7. If no teammate comes to revive, the player stays trapped in the cage until the timer expires, and whatever they dropped is never recovered

### Death Risk Factors

- Dying deep in the dungeon is extremely punishing — the corpse run back eats valuable timer seconds
- The sand cost to revive means the team loses 10 seconds of timer per death
- Unbanked coins are gone the moment you die — only the items and sand on the floor can be won back
- Sand left lying at the death location is time the team never gets — and it despawns like any other
  dropped item, so a corpse run that takes too long loses it outright
- While your items sit at the death location, other hazards (mobs, time pressure) make recovery dangerous

---

## Trapping (Timer Expiry)

When a team's timer reaches zero:

1. **All players still in the dungeon** (status: ALIVE_IN_DUNGEON or DEAD_AWAITING_REVIVE) are teleported to a public trapped location
2. Their status changes to **TRAPPED_TIMER_OUT**
3. **ALL unbanked coins are lost** — complete wipeout of anything not banked at the Sphinx
4. The team's run is over — only banked coins count toward their final score

### When the Timer is Low
- Players face the critical decision: keep exploring for more coins, or run back to the hub to bank and escape
- Players can sacrifice sand to buy more time, but sand is also needed for revives
- Escaping safely locks in your unbanked coins (they still need to be banked, but you're safe)

---

## Escaping

- The **safe exit** is a nether portal, marked in a segment template as a 2D `SAFE_EXIT` bound; the intended mechanic is to **walk through** it to escape (walk-through detection is a follow-up runtime pass — until then the existing right-click-to-escape on the exit block still applies, using a representative cell of the bound)
- The exit location is per-instance: each team's copy of the dungeon has its own
- A dungeon whose segment templates carry no safe-exit marker falls back to the older behaviour, where any End Portal Frame within 30 blocks of the hub works
- Escaping changes the player's status to **ESCAPED_SAFE**
- Escaped players keep their unbanked coins (still need to be banked before escaping for them to count as team score)
- Once escaped, a player cannot re-enter the dungeon

---

## Floor Items & Loot

### Floor Item Types
Items spawn on the dungeon floor as visual displays that players walk over to collect:

| Type | Visual | Pickup Behavior |
|------|--------|-----------------|
| **Coin Stacks** | Gold nugget ItemDisplay (small/medium/large models) | Adds to unbanked score |
| **Generic Loot** | ItemDisplay flat on ground (0.7x scale, rotated 90°) | Added to player inventory |
| **Sand Piles** | (Planned) | Adds sand to team's supply |

### Pickup Mechanics
- Pickup radius: **1.5 blocks**
- Only triggers when the player moves to a new block
- Only items belonging to the player's team can be picked up
- Plays `ENTITY_ITEM_PICKUP` sound on collection (0.7 volume, 1.2 pitch)
- If inventory is full, overflow items drop at the player's feet

---

## Puzzle Rooms

Puzzle rooms are accessible from the hub exits (not on the vault branches). They offer challenges that reward players with valuable items or keys.

### Guess-the-Word Puzzle

- The primary puzzle type is a **word-guessing** game
- Players are presented with a **five-letter word** where some letters are already placed
- Only **one valid five-letter word** is possible given the placed letters and the available letter choices
- Letters are implemented using **player heads with custom skins** — each head displays a letter on its face
- Players must figure out the correct word and place the right letter heads to complete it
- One of these puzzle rooms contains the **Red vault key** as its reward

---

## Mobs

- Mob spawner locations are defined in segment templates
- Segments can contain hostile mobs that threaten players
- Mobs add danger to exploration, especially in deeper segments
- Getting killed by mobs triggers the full death and corpse run mechanic

---

## Win Condition

1. The game ends when **all team timers have expired** (all teams are either escaped or trapped)
2. Each team's final score = their **total banked coins**
3. The team with the highest banked score wins

---

## Technical Constants Reference

### Timer
| Constant | Value |
|----------|-------|
| Default start time | 150 seconds |
| Maximum timer | 150 seconds |
| Timer tick interval | 20 ticks (1 second) |
| Seconds per sand block | 10 |
| Visual: seconds per display block | 10 |
| Visual update interval | 20 ticks (1 second) |

### Scoring
| Constant | Value |
|----------|-------|
| Banking tax | 20% |
| Death penalty | Drop all items (sand included); unbanked coins cleared |
| Timer expiry penalty | 100% (all unbanked) |
| Live scoreboard refresh interval | 20 ticks (1 second) |
| Depth multiplier range | 100% (hub) to 120% (max depth) |

### Sand
| Constant | Value |
|----------|-------|
| Timer seconds per sand | 10 |
| Revive cost | 1 sand |
| Sand spawn chance | 40% per location |
| Sand collection method | Break with shovel (normal sand blocks) |
| Sacrifice points per team | 1 per player on the team |

### Coins
| Constant | Value |
|----------|-------|
| Small coin model ID | 1001 (value < 20) |
| Medium coin model ID | 1002 (value 20–49) |
| Large coin model ID | 1003 (value ≥ 50) |
| Pickup radius | 1.5 blocks |

### Dungeon Generation
| Constant | Value |
|----------|-------|
| Max depth | 10 |
| Max total segments | 50 |
| Generation retries | 5 |
| Team instance spacing | 5,000 blocks |
| Dungeon base offset | (10000, 100, 10000) |

### Vault Depth Ranges (`DungeonGenerator.VAULT_DEPTH_RANGES`, all < `MAX_DEPTH`)
| Vault Color | Min Depth | Max Depth |
|-------------|-----------|-----------|
| Green | 2 | 4 |
| Blue | 3 | 6 |
| Red | 5 | 8 |
| Gold | 7 | 10 |

### Key Depth Ranges (`DungeonGenerator.KEY_DEPTH_RANGES`; blue key is hub metadata)
| Key Color | Min Depth | Max Depth |
|-----------|-----------|-----------|
| Red | 2 | 6 |
| Green | 3 | 7 |
| Gold | 5 | 9 |

### Key Placement

| Key Color | Location                     | Notes                                             |
|-----------|------------------------------|---------------------------------------------------|
| Blue      | Hub                          | Always spawns in hub                              |
| Green     | Opportunistic, within range  | Placed on whichever branch reaches its depth      |
| Red       | Opportunistic, within range  | Prefers a puzzle room if one exists, else any     |
| Gold      | Opportunistic, within range  | Placed on whichever branch reaches its depth      |

### Vault/Key Spawn Probabilities
| Condition | Probability |
|-----------|------------|
| Normal (within range) | 20% |
| Near max depth (MAX - 1) | 50% |
| At max depth | 100% (forced) |

### Door Animation
| Constant | Value |
|----------|-------|
| Animation tick delay | 3 ticks |
| Min tick delay | 1 tick |

### Entry Point Frame

| Constant        | Value                          |
|-----------------|--------------------------------|
| Width           | 3 blocks                       |
| Height          | 4 blocks                       |
| Marker position | Bottom center                  |
| Marker facing   | Outward (away from segment)    |

### Player States
| Status | Description |
|--------|-------------|
| ALIVE_IN_DUNGEON | Actively exploring |
| DEAD_AWAITING_REVIVE | Dead, in cage, waiting for sand sacrifice |
| ESCAPED_SAFE | Successfully exited the dungeon |
| TRAPPED_TIMER_OUT | Timer expired while still inside |
| NOT_IN_GAME | Not participating |

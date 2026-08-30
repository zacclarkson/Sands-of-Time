package com.clarkson.sot.events;

/**
 * All modes the segment builder tool can operate in.
 * Point modes place a single marker on right-click.
 * Bound modes require two right-clicks (first corner, then second corner).
 */
public enum BuilderMode {

    ENTRY_POINT("Entry Point",
            "Right-click a wall face to place; the arrow must point OUT of the segment (toward the neighbour). Right-click the marker to rotate.",
            false),
    VAULT_DOOR("Vault Door",
            "Click two air blocks to define the vault door opening. Requires /sotmode VAULT_DOOR <color>.",
            true),
    VAULT_MARKER("Vault Marker",
            "Right-click to mark the vault activation block. Requires /sotmode VAULT_MARKER <color>.",
            false),
    KEY_SPAWN("Key Spawn",
            "Right-click to mark where the key item spawns. Requires /sotmode KEY_SPAWN <color>.",
            false),
    GATE("Gate",
            "Click two air blocks to define the gate opening.",
            true),
    LEVER("Lever",
            "Right-click to mark the lever that opens all gates in this segment.",
            false),
    SAND_SPAWN("Sand Spawn",
            "Right-click to mark a sand spawn position.",
            false),
    SAND_SACRIFICE("Sand Sacrifice",
            "Right-click to mark a sand sacrifice block position.",
            false),
    SAND_TRADE("Sand Trade",
            "Right-click to mark a sand trade point out in the dungeon (a chest that buys coins for sand). Place these in branch segments, not the HUB.",
            false),
    SAFE_EXIT("Safe Exit",
            "Click two air blocks to define the safe-exit nether-portal opening (2D, like a door). Build the portal blocks inside it. One per dungeon, normally in the HUB.",
            true),
    COIN_SPAWN("Coin Spawn",
            "Right-click to mark a coin spawn. Use /sotmode COIN_SPAWN <value> to set value.",
            false),
    ITEM_SPAWN("Item Spawn",
            "Right-click to mark an item spawn position.",
            false),
    MOB_SPAWNER("Mob Spawner",
            "Right-click to mark a mob spawner position.",
            false),
    BANK("Bank",
            "Right-click to mark where players bank coins (an ender chest is built here). One per HUB.",
            false),
    DEATH_CAGE("Death Cage",
            "Right-click to mark a death/respawn cage. Place 1-4 (one per player). A lodestone 'Revive' preview appears 2 blocks toward your facing — aim before you click.",
            false),
    TIMER_DEPOSIT("Timer Deposit",
            "Right-click to mark an interact point where players place collected sand onto the timer.",
            false),
    TIMER("Timer",
            "Right-click to mark the base of the visual sand-timer column in the HUB. One per HUB.",
            false),
    PLAYER_SPAWN("Player Spawn",
            "Right-click to mark where a player spawns at game start. Place one per player (up to your team size) in the HUB; players are spread across them.",
            false);

    private final String displayName;
    private final String hint;
    /** True if this mode uses a two-click bound selection. */
    private final boolean isBoundMode;

    BuilderMode(String displayName, String hint, boolean isBoundMode) {
        this.displayName = displayName;
        this.hint = hint;
        this.isBoundMode = isBoundMode;
    }

    public String getDisplayName() { return displayName; }
    public String getHint() { return hint; }
    public boolean isBoundMode() { return isBoundMode; }
}

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
    COIN_SPAWN("Coin Spawn",
            "Right-click to mark a coin spawn. Use /sotmode COIN_SPAWN <value> to set value.",
            false),
    ITEM_SPAWN("Item Spawn",
            "Right-click to mark an item spawn position.",
            false),
    MOB_SPAWNER("Mob Spawner",
            "Right-click to mark a mob spawner position.",
            false),
    SAFE_EXIT("Safe Exit",
            "Right-click to mark where escaped players are teleported. Usually placed in the HUB segment.",
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

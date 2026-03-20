package com.clarkson.sot.commands;

/**
 * All available modes for the segment builder tool.
 * Switched via /sotmode <mode>.
 */
public enum BuilderMode {

    ENTRY_POINT(
        "Entry Point",
        "Right-click a wall face to place. Right-click existing marker to rotate."
    ),
    VAULT_DOOR(
        "Vault Door",
        "Right-click two opposite air corners to define the vault door opening."
    ),
    GATE(
        "Gate",
        "Right-click two opposite air corners to define the gate opening."
    ),
    LEVER(
        "Lever",
        "Right-click any block face to mark the gate lever location."
    ),
    SAND_SPAWN(
        "Sand Spawn",
        "Right-click any block face to mark a sand spawn location."
    ),
    SAND_SACRIFICE(
        "Sand Sacrifice",
        "Right-click any block face to mark a sand sacrifice altar."
    ),
    COIN_SPAWN(
        "Coin Spawn",
        "Right-click any block face to mark a coin spawn location."
    ),
    ITEM_SPAWN(
        "Item Spawn",
        "Right-click any block face to mark an item spawn location."
    ),
    MOB_SPAWNER(
        "Mob Spawner",
        "Right-click any block face to mark a mob spawner location."
    );

    private final String displayName;
    private final String hint;

    BuilderMode(String displayName, String hint) {
        this.displayName = displayName;
        this.hint = hint;
    }

    public String getDisplayName() { return displayName; }
    public String getHint() { return hint; }
}

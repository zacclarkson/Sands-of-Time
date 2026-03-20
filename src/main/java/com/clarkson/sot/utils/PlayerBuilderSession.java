package com.clarkson.sot.utils;

import com.clarkson.sot.commands.BuilderMode;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Tracks per-player state for the segment builder tool.
 */
public class PlayerBuilderSession {

    private BuilderMode mode = BuilderMode.ENTRY_POINT;

    // In-progress bound selection state (VAULT_DOOR / GATE modes)
    @Nullable private Location pendingCorner1;
    @Nullable private UUID pendingCornerEntityId;

    public BuilderMode getMode() { return mode; }

    /** Switches mode and clears any in-progress bound selection. */
    public void setMode(BuilderMode mode) {
        this.mode = mode;
        clearPendingBound();
    }

    public boolean hasPendingCorner() { return pendingCorner1 != null; }

    @Nullable public Location getPendingCorner1() { return pendingCorner1; }

    @Nullable public UUID getPendingCornerEntityId() { return pendingCornerEntityId; }

    public void setPendingCorner(Location corner, UUID entityId) {
        this.pendingCorner1 = corner;
        this.pendingCornerEntityId = entityId;
    }

    public void clearPendingBound() {
        this.pendingCorner1 = null;
        this.pendingCornerEntityId = null;
    }
}

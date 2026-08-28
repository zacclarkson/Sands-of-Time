package com.clarkson.sot.events;

import com.clarkson.sot.dungeon.VaultColor;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Stores per-player state for the segment builder tool.
 * One instance is held per online builder in BuilderSessionManager.
 */
public class PlayerBuilderSession {

    private BuilderMode mode = BuilderMode.ENTRY_POINT;

    // For modes that require a vault color (VAULT_DOOR, VAULT_MARKER, KEY_SPAWN).
    @Nullable private VaultColor vaultColor = null;

    // Base coin value used when placing COIN_SPAWN markers.
    private int coinValue = 10;

    // --- Two-click bound selection state (VAULT_DOOR / GATE) ---
    /** Absolute world location of the first corner click (air block in front of face). */
    @Nullable private Location pendingBoundCorner = null;
    /** UUID of the temporary "first corner" marker entity, so it can be removed on cancel/complete. */
    @Nullable private UUID pendingCornerEntityId = null;

    // --- Undo history ---
    /** Most-recent-first stack of placed marker group IDs, for /sotundo. */
    private final Deque<String> undoStack = new ArrayDeque<>();
    private static final int UNDO_CAP = 200;

    // --- Getters and setters ---

    public BuilderMode getMode() { return mode; }

    public void setMode(BuilderMode mode) { this.mode = mode; }

    @Nullable public VaultColor getVaultColor() { return vaultColor; }

    public void setVaultColor(@Nullable VaultColor vaultColor) { this.vaultColor = vaultColor; }

    public int getCoinValue() { return coinValue; }

    public void setCoinValue(int coinValue) { this.coinValue = Math.max(1, coinValue); }

    @Nullable public Location getPendingBoundCorner() { return pendingBoundCorner; }

    public void setPendingBoundCorner(@Nullable Location loc) { this.pendingBoundCorner = loc; }

    @Nullable public UUID getPendingCornerEntityId() { return pendingCornerEntityId; }

    public void setPendingCornerEntityId(@Nullable UUID id) { this.pendingCornerEntityId = id; }

    /** Clears pending bound selection state without changing mode. */
    public void clearPendingBound() {
        this.pendingBoundCorner = null;
        this.pendingCornerEntityId = null;
    }

    // --- Undo history ---

    /** Records a newly placed marker group so it can be reverted by /sotundo. */
    public void pushUndo(String groupId) {
        undoStack.push(groupId);
        while (undoStack.size() > UNDO_CAP) {
            undoStack.removeLast();
        }
    }

    /** Pops and returns the most recently placed group ID, or null if history is empty. */
    @Nullable
    public String popUndo() {
        return undoStack.poll();
    }

    /** Clears all undo history (e.g. after /sotclearmarkers). */
    public void clearUndo() {
        undoStack.clear();
    }

    public int undoSize() {
        return undoStack.size();
    }
}

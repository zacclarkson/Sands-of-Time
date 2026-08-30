package com.clarkson.sot.dungeon;

import com.clarkson.sot.entities.Area;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * One vault door wall in absolute world coordinates, with the vault colour that opens it.
 *
 * <p>A vault door has no keyhole and no key of its own: it drops when its team opens the matching
 * vault. Only one key of each colour exists per dungeon and {@code VaultManager} consumes it at the
 * vault marker, so a second keyhole here could never be opened.
 */
public final class VaultDoorPlacement {

    private final VaultColor color;
    private final Area bounds;
    private final String segmentName;

    public VaultDoorPlacement(@NotNull VaultColor color, @NotNull Area bounds, @NotNull String segmentName) {
        this.color = Objects.requireNonNull(color, "Vault colour cannot be null");
        this.bounds = Objects.requireNonNull(bounds, "Vault door bounds cannot be null");
        this.segmentName = Objects.requireNonNull(segmentName, "Segment name cannot be null");
    }

    @NotNull public VaultColor getColor() { return color; }
    @NotNull public Area getBounds() { return bounds; }
    @NotNull public String getSegmentName() { return segmentName; }

    @Override
    public String toString() {
        return "VaultDoorPlacement{segment=" + segmentName
                + ", color=" + color
                + ", min=" + bounds.getMinPoint().toVector() + '}';
    }
}

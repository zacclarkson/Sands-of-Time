package com.clarkson.sot.dungeon;

import com.clarkson.sot.entities.Area;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One segment's gates and what opens them -- a lever, one or more sand sacrifice chests, or both --
 * in absolute world coordinates.
 *
 * <p>The pairing is the whole point. A gate restricts access to an optional area <em>within its own
 * segment</em>, and a segment with gates must carry a lever or (outside the HUB) at least one
 * {@code SAND_SACRIFICE} chest ({@code SaveSegmentCommand} refuses to save otherwise), so pulling the
 * lever or paying the chest opens that segment's gates and no others. Flattening gates into a
 * dungeon-wide list -- the shape every other blueprint feature uses -- would lose exactly the
 * association that makes either mean anything.
 *
 * <p>A lever is free and one-way; a sacrifice chest costs {@link SacrificePlacement#cost()} sand,
 * paid one at a time. Both are registered against the same {@code Gate} objects by {@code DoorManager},
 * so whichever opens first wins and the other simply reports the gates already open.
 */
public final class GateGroup {

    /**
     * One gate sacrifice chest: the absolute cell the chest is written into, and its sand price.
     * Priced per marker by the builder ({@code /sotmode SAND_SACRIFICE <cost>}).
     */
    public record SacrificePlacement(@NotNull Location location, int cost) {
        public SacrificePlacement {
            Objects.requireNonNull(location, "Sacrifice location cannot be null");
            location = location.clone();
            if (cost < 1) throw new IllegalArgumentException("Sacrifice cost must be at least 1, got " + cost);
        }

        @Override
        public @NotNull Location location() { return location.clone(); }
    }

    @Nullable private final Location leverLocation;
    private final List<Area> gateBounds;
    private final List<SacrificePlacement> sacrificePlacements;
    private final String segmentName;

    /**
     * @param leverLocation       Absolute cell the LEVER marker occupied (a real lever block is written
     *                            here), or null when this segment opens by sacrifice only.
     * @param gateBounds          Absolute bounds of every gate this group opens.
     * @param sacrificePlacements The gate sacrifice chests that open these gates; may be empty when a
     *                            lever is present.
     * @param segmentName         Name of the segment template these came from, for logging.
     * @throws IllegalArgumentException when there is neither a lever nor a sacrifice chest -- a group
     *         nothing could ever open is exactly what {@code resolveGateGroups} leaves out instead.
     */
    public GateGroup(@Nullable Location leverLocation, @NotNull List<Area> gateBounds,
                     @NotNull List<SacrificePlacement> sacrificePlacements, @NotNull String segmentName) {
        Objects.requireNonNull(gateBounds, "Gate bounds cannot be null");
        Objects.requireNonNull(sacrificePlacements, "Sacrifice placements cannot be null");
        if (leverLocation == null && sacrificePlacements.isEmpty()) {
            throw new IllegalArgumentException("A gate group needs a lever or at least one sacrifice chest");
        }
        this.leverLocation = leverLocation != null ? leverLocation.clone() : null;
        this.gateBounds = Collections.unmodifiableList(new ArrayList<>(gateBounds));
        this.sacrificePlacements = Collections.unmodifiableList(new ArrayList<>(sacrificePlacements));
        this.segmentName = Objects.requireNonNull(segmentName, "Segment name cannot be null");
    }

    /** A lever-only group. */
    public GateGroup(@NotNull Location leverLocation, @NotNull List<Area> gateBounds, @NotNull String segmentName) {
        this(Objects.requireNonNull(leverLocation, "Lever location cannot be null"), gateBounds, List.of(), segmentName);
    }

    /** The lever cell, or null when these gates open by sacrifice only. */
    @Nullable public Location getLeverLocation() { return leverLocation != null ? leverLocation.clone() : null; }
    public boolean hasLever() { return leverLocation != null; }
    @NotNull public List<Area> getGateBounds() { return gateBounds; }
    @NotNull public List<SacrificePlacement> getSacrificePlacements() { return sacrificePlacements; }
    @NotNull public String getSegmentName() { return segmentName; }

    @Override
    public String toString() {
        return "GateGroup{segment=" + segmentName
                + ", lever=" + (leverLocation != null ? leverLocation.toVector() : "none")
                + ", sacrifices=" + sacrificePlacements.size()
                + ", gates=" + gateBounds.size() + '}';
    }
}

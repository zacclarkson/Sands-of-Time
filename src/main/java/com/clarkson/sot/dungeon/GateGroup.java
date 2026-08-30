package com.clarkson.sot.dungeon;

import com.clarkson.sot.entities.Area;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One segment's gates and the single lever that opens them, in absolute world coordinates.
 *
 * <p>The pairing is the whole point. A gate restricts access to an optional area <em>within its own
 * segment</em>, and a segment with gates must carry exactly one lever ({@code SaveSegmentCommand}
 * refuses to save otherwise), so pulling a lever opens that segment's gates and no others. Flattening
 * gates into a dungeon-wide list -- the shape every other blueprint feature uses -- would lose exactly
 * the association that makes a lever mean anything.
 */
public final class GateGroup {

    private final Location leverLocation;
    private final List<Area> gateBounds;
    private final String segmentName;

    /**
     * @param leverLocation Absolute cell the LEVER marker occupied; a real lever block is written here.
     * @param gateBounds    Absolute bounds of every gate this lever opens.
     * @param segmentName   Name of the segment template these came from, for logging.
     */
    public GateGroup(@NotNull Location leverLocation, @NotNull List<Area> gateBounds, @NotNull String segmentName) {
        this.leverLocation = Objects.requireNonNull(leverLocation, "Lever location cannot be null").clone();
        this.gateBounds = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(gateBounds, "Gate bounds cannot be null")));
        this.segmentName = Objects.requireNonNull(segmentName, "Segment name cannot be null");
    }

    @NotNull public Location getLeverLocation() { return leverLocation.clone(); }
    @NotNull public List<Area> getGateBounds() { return gateBounds; }
    @NotNull public String getSegmentName() { return segmentName; }

    @Override
    public String toString() {
        return "GateGroup{segment=" + segmentName
                + ", lever=" + leverLocation.toVector()
                + ", gates=" + gateBounds.size() + '}';
    }
}

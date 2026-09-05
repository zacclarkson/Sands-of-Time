package com.clarkson.sot.dungeon;

import com.clarkson.sot.entities.Gate;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One gate sacrifice chest in a live dungeon instance: the sand price the builder set on the marker,
 * how much of it has been paid so far, and the gates it opens.
 *
 * <p>This is the sand-for-money trade. A sacrifice point never hands out coins itself: it stands in
 * front of a gate and whatever the segment template put <em>behind</em> the gate is the reward, so
 * the decision is the same one a lever asks ("is what I can see through the bars worth it?") with a
 * price attached. Payment is one sand per right-click, so several teammates can chip in; the gates
 * open on the click that completes the total. Sand paid into a chest whose gates then open by lever
 * is spent, not refunded -- the same rule {@link DeathCage} applies to a revive that never completes.
 *
 * <p>Like a {@link DeathCage} this is rebuilt from template geometry for every instance
 * ({@code DungeonManager.resolveGateGroups} -> {@code DoorManager.initializeGatesForInstance}), so the
 * part-payment lives and dies with the dungeon and nothing has to reset it between rounds. Both
 * paths that open the gates -- the lever and the completing payment -- go through
 * {@code DoorManager}, which is what marks the point open.
 */
public final class GateSacrificePoint {

    private final Location location;
    private final int cost;
    private final List<Gate> gates;
    @Nullable private final Location leverLocation;
    private final String segmentName;

    private int sandDeposited;
    private boolean open;

    /**
     * @param location      Absolute cell the chest is written into.
     * @param cost          Sand price, at least 1.
     * @param gates         The gates a completed payment opens -- the <em>same</em> objects the
     *                      segment's lever opens, so whichever path opens first wins.
     * @param leverLocation The segment's lever cell, or null when the gates open by sacrifice only.
     * @param segmentName   Name of the segment template, for logging.
     */
    public GateSacrificePoint(@NotNull Location location, int cost, @NotNull List<Gate> gates,
                              @Nullable Location leverLocation, @NotNull String segmentName) {
        this.location = Objects.requireNonNull(location, "Sacrifice location cannot be null").clone();
        if (cost < 1) throw new IllegalArgumentException("Sacrifice cost must be at least 1, got " + cost);
        this.cost = cost;
        this.gates = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(gates, "Gates cannot be null")));
        this.leverLocation = leverLocation != null ? leverLocation.clone() : null;
        this.segmentName = Objects.requireNonNull(segmentName, "Segment name cannot be null");
    }

    @NotNull public Location getLocation() { return location.clone(); }
    public int getCost() { return cost; }
    public int getSandDeposited() { return sandDeposited; }
    /** Sand still owed before the gates open; 0 once paid in full (or opened another way). */
    public int getRemainingSand() { return open ? 0 : Math.max(0, cost - sandDeposited); }
    @NotNull public List<Gate> getGates() { return gates; }
    @Nullable public Location getLeverLocation() { return leverLocation != null ? leverLocation.clone() : null; }
    @NotNull public String getSegmentName() { return segmentName; }

    /** True once the gates this chest fronts have opened, by payment or by lever. */
    public boolean isOpen() { return open; }

    /** Records that the gates are open, whichever path opened them. Idempotent. */
    public void markOpened() { this.open = true; }

    /**
     * Records one sand paid toward the price.
     *
     * @return true when this deposit completes the total.
     */
    public boolean depositSand() {
        sandDeposited++;
        return getRemainingSand() <= 0;
    }

    /** True when {@code location} is this chest's block (exact block match and same world). */
    public boolean isAt(@NotNull Location other) {
        return other.getBlockX() == location.getBlockX()
                && other.getBlockY() == location.getBlockY()
                && other.getBlockZ() == location.getBlockZ()
                && Objects.equals(other.getWorld(), location.getWorld());
    }

    /** True when this chest opens the gates the lever at {@code lever} opens. */
    public boolean isOpenedByLeverAt(@NotNull Location lever) {
        return leverLocation != null
                && lever.getBlockX() == leverLocation.getBlockX()
                && lever.getBlockY() == leverLocation.getBlockY()
                && lever.getBlockZ() == leverLocation.getBlockZ()
                && Objects.equals(lever.getWorld(), leverLocation.getWorld());
    }

    @Override
    public String toString() {
        return "GateSacrificePoint{segment=" + segmentName + ", at=" + location.toVector()
                + ", paid=" + sandDeposited + "/" + cost + ", open=" + open + '}';
    }
}

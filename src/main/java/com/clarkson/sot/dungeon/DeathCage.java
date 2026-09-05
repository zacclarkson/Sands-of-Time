package com.clarkson.sot.dungeon;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a single death cage + sacrifice point pair within a dungeon instance.
 * Each player on a team is assigned to one cage. When that player dies,
 * they are teleported into the cage. Teammates free them by right-clicking the paired
 * sacrifice point (a CHEST) while carrying sand.
 *
 * <p>The cage also carries the state of the revive itself, because it is the one object that is
 * already 1:1 with a player for the round: how many times that player has died (which sets the
 * price of the next revive) and how much sand teammates have put toward it so far. Cages are
 * rebuilt for every dungeon instance, so both reset per round without anything having to clear them.
 *
 * <p>Reviving costs the player's death count in sand, capped at {@link #MAX_REVIVE_COST} — the
 * first death costs 1, the second 2, and so on. The cost is paid a sand at a time, so several
 * teammates can contribute to the same revive.
 *
 * Max 4 cages per team (teams are 1-4 players).
 */
public class DeathCage {

    /** Ceiling on the escalating revive price, in sand. */
    public static final int MAX_REVIVE_COST = 5;

    private final Location cageLocation;
    private final Location sacrificePointLocation;
    private UUID assignedPlayerUUID; // null if unassigned

    private int deathCount;    // deaths by the assigned player this round; sets the revive price
    private int sandDeposited; // sand paid toward the current revive

    public DeathCage(@NotNull Location cageLocation, @NotNull Location sacrificePointLocation) {
        this.cageLocation = Objects.requireNonNull(cageLocation, "Cage location cannot be null");
        this.sacrificePointLocation = Objects.requireNonNull(sacrificePointLocation, "Sacrifice point location cannot be null");
        this.assignedPlayerUUID = null;
    }

    @NotNull
    public Location getCageLocation() {
        return cageLocation.clone();
    }

    @NotNull
    public Location getSacrificePointLocation() {
        return sacrificePointLocation.clone();
    }

    @Nullable
    public UUID getAssignedPlayerUUID() {
        return assignedPlayerUUID;
    }

    public void assignPlayer(@NotNull UUID playerUUID) {
        this.assignedPlayerUUID = Objects.requireNonNull(playerUUID);
    }

    public void clearAssignment() {
        this.assignedPlayerUUID = null;
    }

    public boolean isAssigned() {
        return assignedPlayerUUID != null;
    }

    /**
     * Records that the assigned player has died, raising the price of their next revive and
     * discarding any part-payment left over from an earlier one.
     *
     * <p>Clearing the progress matters: a player who is revived at a cost of 2 and dies again must
     * not inherit the sand paid the first time.
     */
    public void recordDeath() {
        deathCount++;
        sandDeposited = 0;
    }

    /** Deaths recorded for the assigned player this round. */
    public int getDeathCount() {
        return deathCount;
    }

    /** Sand this revive costs in total: the death count, capped at {@link #MAX_REVIVE_COST}. */
    public int getRequiredSand() {
        return Math.min(MAX_REVIVE_COST, deathCount);
    }

    /** Sand already paid toward the current revive. */
    public int getSandDeposited() {
        return sandDeposited;
    }

    /** Sand still owed before the assigned player can be freed; never negative. */
    public int getRemainingSand() {
        return Math.max(0, getRequiredSand() - sandDeposited);
    }

    /**
     * Puts one sand toward the current revive.
     *
     * @return true if that sand completed the price, meaning the caged player should now be freed.
     */
    public boolean depositSand() {
        sandDeposited++;
        return getRemainingSand() <= 0;
    }

    /** Clears part-payment after a completed revive, so the next death starts from zero. */
    public void clearProgress() {
        sandDeposited = 0;
    }

    /**
     * Checks if the given location matches this cage's sacrifice point (block coordinates).
     */
    public boolean isSacrificePointAt(@NotNull Location location) {
        return sacrificePointLocation.getBlockX() == location.getBlockX()
            && sacrificePointLocation.getBlockY() == location.getBlockY()
            && sacrificePointLocation.getBlockZ() == location.getBlockZ()
            && Objects.equals(sacrificePointLocation.getWorld(), location.getWorld());
    }
}

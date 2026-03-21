package com.clarkson.sot.dungeon;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a single death cage + sacrifice point pair within a dungeon instance.
 * Each player on a team is assigned to one cage. When that player dies,
 * they are teleported into the cage. A teammate must interact with the
 * paired sacrifice point (LODESTONE) to revive them.
 *
 * Max 4 cages per team (teams are 1-4 players).
 */
public class DeathCage {

    private final Location cageLocation;
    private final Location sacrificePointLocation;
    private UUID assignedPlayerUUID; // null if unassigned

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
     * Checks if the given location matches this cage's sacrifice point (block coordinates).
     */
    public boolean isSacrificePointAt(@NotNull Location location) {
        return sacrificePointLocation.getBlockX() == location.getBlockX()
            && sacrificePointLocation.getBlockY() == location.getBlockY()
            && sacrificePointLocation.getBlockZ() == location.getBlockZ()
            && Objects.equals(sacrificePointLocation.getWorld(), location.getWorld());
    }
}

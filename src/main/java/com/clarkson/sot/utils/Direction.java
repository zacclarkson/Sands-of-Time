package com.clarkson.sot.utils;

import org.bukkit.util.Vector; // Import Bukkit's Vector class
import org.jetbrains.annotations.NotNull;

public enum Direction {
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    EAST(1, 0, 0),
    WEST(-1, 0, 0),
    UP(0, 1, 0),
    DOWN(0, -1, 0); // Added DOWN for completeness, adjust if not needed

    private final Vector blockVector;

    Direction(int modX, int modY, int modZ) {
        this.blockVector = new Vector(modX, modY, modZ);
    }

    /**
     * Gets the unit vector representing the direction (e.g., NORTH is <0, 0, -1>).
     * @return The corresponding BlockVector.
     */
    @NotNull
    public Vector getBlockVector() {
        // Return a clone to prevent modification of the original vector instance
        return blockVector.clone();
    }

    @NotNull
    public Direction getOpposite() {
        switch (this) {
            case NORTH: return SOUTH;
            case SOUTH: return NORTH;
            case EAST: return WEST;
            case WEST: return EAST;
            case UP: return DOWN;
            case DOWN: return UP;
            // The default case should ideally not be reached if all enum constants are handled
            default: throw new IllegalStateException("Unexpected direction value: " + this);
        }
    }

    @NotNull
    public org.bukkit.block.BlockFace toBlockFace() {
        switch (this) {
            case NORTH: return org.bukkit.block.BlockFace.NORTH;
            case SOUTH: return org.bukkit.block.BlockFace.SOUTH;
            case EAST:  return org.bukkit.block.BlockFace.EAST;
            case WEST:  return org.bukkit.block.BlockFace.WEST;
            case UP:    return org.bukkit.block.BlockFace.UP;
            case DOWN:  return org.bukkit.block.BlockFace.DOWN;
            default:    throw new IllegalStateException("Unexpected value: " + this);
        }
    }
}

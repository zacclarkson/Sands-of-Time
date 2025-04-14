package com.clarkson.sot.dungeon.segment; // Assuming this package

import org.bukkit.block.BlockFace; // Import BlockFace
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable; // Import Nullable

public enum Direction {
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    EAST(1, 0, 0),
    WEST(-1, 0, 0),
    UP(0, 1, 0),
    DOWN(0, -1, 0);

    private final Vector blockVector;

    Direction(int modX, int modY, int modZ) {
        this.blockVector = new Vector(modX, modY, modZ);
    }

    /**
     * Gets the unit vector representing the direction (e.g., NORTH is <0, 0, -1>).
     * @return A clone of the corresponding Vector.
     */
    @NotNull
    public Vector getBlockVector() {
        return blockVector.clone();
    }

    /**
     * Gets the opposite cardinal or vertical direction.
     * @return The opposite Direction.
     */
    @NotNull
    public Direction getOpposite() {
        switch (this) {
            case NORTH: return SOUTH;
            case SOUTH: return NORTH;
            case EAST: return WEST;
            case WEST: return EAST;
            case UP: return DOWN;
            case DOWN: return UP;
            default: throw new IllegalStateException("Unexpected direction value: " + this); // Should be unreachable
        }
    }

    /**
     * Converts this Direction enum to the corresponding Bukkit BlockFace.
     * @return The matching BlockFace.
     */
    @NotNull
    public BlockFace toBlockFace() {
        switch (this) {
            case NORTH: return BlockFace.NORTH;
            case SOUTH: return BlockFace.SOUTH;
            case EAST:  return BlockFace.EAST;
            case WEST:  return BlockFace.WEST;
            case UP:    return BlockFace.UP;
            case DOWN:  return BlockFace.DOWN;
            default:    throw new IllegalStateException("Unexpected value: " + this); // Should be unreachable
        }
    }

    // --- Static Helper Methods ---

    /**
     * Gets the cardinal Direction (NORTH, SOUTH, EAST, WEST) corresponding to a yaw value.
     * @param yaw The yaw angle (degrees).
     * @return The corresponding Direction (defaults to SOUTH).
     */
    @NotNull
    public static Direction fromYaw(float yaw) {
         yaw = (yaw % 360 + 360) % 360; // Normalize yaw 0-360
         if (yaw >= 315 || yaw < 45) return Direction.SOUTH; // 315-360 and 0-45
         if (yaw >= 45 && yaw < 135) return Direction.WEST;  // 45-135
         if (yaw >= 135 && yaw < 225) return Direction.NORTH; // 135-225
         if (yaw >= 225 && yaw < 315) return Direction.EAST;  // 225-315
         return Direction.SOUTH; // Fallback (shouldn't be needed)
     }

    /**
     * Gets the Direction enum corresponding to a Bukkit BlockFace.
     * Returns null for non-cardinal/non-axis faces (e.g., NORTH_EAST).
     * @param face The BlockFace.
     * @return The matching Direction, or null if no direct match.
     */
    @Nullable
    public static Direction fromBlockFace(@NotNull BlockFace face) {
          switch (face) {
              case NORTH: return Direction.NORTH;
              case SOUTH: return Direction.SOUTH;
              case EAST: return Direction.EAST;
              case WEST: return Direction.WEST;
              case UP: return Direction.UP;
              case DOWN: return Direction.DOWN;
              default: return null; // Return null for diagonal/self faces
          }
      }

}

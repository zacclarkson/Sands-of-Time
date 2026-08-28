package com.clarkson.sot.timer;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * Geometry for the lobby sand columns that visualise each team's timer.
 *
 * <p>Every team gets its own vertical column of sand. A column is described by two block
 * locations, matching what {@link VisualSandTimerDisplay} expects:
 * <ul>
 *   <li>the <em>bottom</em> location, the block directly <em>below</em> the lowest sand block;</li>
 *   <li>the <em>top</em> location, the highest sand block the column can hold.</li>
 * </ul>
 * The difference between their Y values is therefore the number of sand blocks in a full column:
 * {@link TeamTimer#DEFAULT_MAX_TIMER_SECONDS} divided by
 * {@link VisualSandTimerDisplay#SECONDS_PER_BLOCK} — 150 seconds at 10 seconds per block, so 15
 * blocks (see {@code GAME_RULES.md}, "Visual Sand Timer").
 *
 * <p>Columns are laid out in a row along +X starting at the lobby anchor, so the anchor is the
 * block directly below the first team's column and each subsequent team is
 * {@link #COLUMN_SPACING_BLOCKS} blocks further along X.
 */
public final class VisualTimerLayout {

    /** Number of sand blocks in a full column. */
    public static final int COLUMN_HEIGHT_BLOCKS =
            TeamTimer.DEFAULT_MAX_TIMER_SECONDS / VisualSandTimerDisplay.SECONDS_PER_BLOCK;

    /** Horizontal gap, in blocks, between the columns of adjacent teams. */
    public static final int COLUMN_SPACING_BLOCKS = 3;

    private VisualTimerLayout() {}

    /**
     * The block directly below the base of a team's sand column.
     *
     * @param anchor    The lobby anchor. Must have a loaded world.
     * @param teamIndex Zero-based index of the team within the current game.
     */
    public static Location bottomLocation(Location anchor, int teamIndex) {
        return columnLocation(anchor, teamIndex, 0);
    }

    /**
     * The highest sand block of a team's column, {@link #COLUMN_HEIGHT_BLOCKS} above the
     * matching {@link #bottomLocation}.
     *
     * @param anchor    The lobby anchor. Must have a loaded world.
     * @param teamIndex Zero-based index of the team within the current game.
     */
    public static Location topLocation(Location anchor, int teamIndex) {
        return columnLocation(anchor, teamIndex, COLUMN_HEIGHT_BLOCKS);
    }

    private static Location columnLocation(Location anchor, int teamIndex, int yOffset) {
        Objects.requireNonNull(anchor, "Anchor location cannot be null");
        World world = Objects.requireNonNull(anchor.getWorld(), "Anchor location must have a world");
        if (teamIndex < 0) {
            throw new IllegalArgumentException("Team index cannot be negative: " + teamIndex);
        }

        int x = anchor.getBlockX() + (teamIndex * COLUMN_SPACING_BLOCKS);
        int z = anchor.getBlockZ();
        int baseY = clampBaseY(world, anchor.getBlockY());
        return new Location(world, x, baseY + yOffset, z);
    }

    /**
     * Keeps the whole column inside the world's build limits: the base sits no lower than the
     * world floor, and the top sand block no higher than the last buildable layer.
     */
    private static int clampBaseY(World world, int desiredBaseY) {
        int lowestBase = world.getMinHeight();
        int highestBase = world.getMaxHeight() - 1 - COLUMN_HEIGHT_BLOCKS;
        if (highestBase < lowestBase) {
            // World is too short for a full column; the display will report the bad height itself.
            return lowestBase;
        }
        return Math.max(lowestBase, Math.min(highestBase, desiredBaseY));
    }
}

package com.clarkson.sot.timer;

/**
 * Geometry for the sand column that visualises a team's timer.
 *
 * <p>Every team gets its own vertical column of sand, standing in that team's dungeon hub on the
 * hub segment's {@code TIMER} marker. A column is described by two block locations, matching what
 * {@link VisualSandTimerDisplay} expects:
 * <ul>
 *   <li>the <em>bottom</em> location — the marker itself, the block directly <em>below</em> the
 *       lowest sand block;</li>
 *   <li>the <em>top</em> location, the highest sand block the column can hold,
 *       {@link #COLUMN_HEIGHT_BLOCKS} above the bottom.</li>
 * </ul>
 * The difference between their Y values is therefore the number of sand blocks in a full column:
 * {@link TeamTimer#DEFAULT_MAX_TIMER_SECONDS} divided by
 * {@link VisualSandTimerDisplay#SECONDS_PER_BLOCK} — 150 seconds at 10 seconds per block, so 15
 * blocks (see {@code GAME_RULES.md}, "Visual Sand Timer").
 */
public final class VisualTimerLayout {

    /** Number of sand blocks in a full column. */
    public static final int COLUMN_HEIGHT_BLOCKS =
            TeamTimer.DEFAULT_MAX_TIMER_SECONDS / VisualSandTimerDisplay.SECONDS_PER_BLOCK;

    private VisualTimerLayout() {}
}

package com.clarkson.sot.dungeon;

import com.clarkson.sot.dungeon.segment.Direction;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A single doorway in a generated dungeon layout, positioned relative to the blueprint origin.
 *
 * <p>The position is the bottom-centre cell of the opening -- the same cell a builder marks with
 * the ENTRY_POINT tool -- and the opening itself is 3 wide by 4 tall around it. Two connected
 * segments meet on a shared doorway plane, so both sides of a connection resolve to this one cell.
 *
 * <p>{@link DungeonGenerator} emits two lists of these: the connections it actually made (which
 * become rusty-key doors) and the entry points it never attached a neighbour to (which get sealed
 * as plain wall, so the dungeon has no holes opening onto nothing).
 */
public final class Doorway {

    private final Vector relativePosition;
    private final Direction direction;

    /**
     * @param relativePosition Bottom-centre cell of the opening, relative to the blueprint origin.
     * @param direction        The direction the opening faces, pointing out of the segment.
     */
    public Doorway(@NotNull Vector relativePosition, @NotNull Direction direction) {
        this.relativePosition = Objects.requireNonNull(relativePosition, "relativePosition").clone();
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    @NotNull public Vector getRelativePosition() { return relativePosition.clone(); }
    @NotNull public Direction getDirection() { return direction; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Doorway)) return false;
        Doorway that = (Doorway) o;
        return relativePosition.equals(that.relativePosition) && direction == that.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(relativePosition, direction);
    }

    @Override
    public String toString() {
        return "Doorway{pos=" + relativePosition + ", dir=" + direction + '}';
    }
}

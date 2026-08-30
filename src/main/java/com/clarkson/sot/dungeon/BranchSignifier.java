package com.clarkson.sot.dungeon;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * One coloured wall marking in a generated dungeon layout, positioned relative to the blueprint
 * origin. It tells a player standing at a junction which vault colour lies down the branch beside
 * it (GAME_RULES.md, "4 Vault Exits -- each marked with a coloured indicator on the wall").
 *
 * <p>The position is the cell a builder marked with the {@code BRANCH_SIGNIFIER} tool -- the air
 * cell in front of the wall face they clicked -- and {@link DungeonManager} writes a block of
 * {@link VaultColor#getConcreteMaterial()} there once the schematics are pasted.
 *
 * <p>The colour is <em>not</em> template data: a segment declares only where a marking goes, and
 * {@link DungeonGenerator#resolveBranchSignifiers} works out which vault that branch leads to for
 * the layout it just generated. A placeholder whose branch reaches no vault produces no
 * {@code BranchSignifier} at all, so the ~6 non-vault hub exits stay unmarked.
 */
public final class BranchSignifier {

    private final Vector relativePosition;
    private final VaultColor color;

    /**
     * @param relativePosition The cell the marking is written in, relative to the blueprint origin.
     * @param color            The vault colour that lies down this branch.
     */
    public BranchSignifier(@NotNull Vector relativePosition, @NotNull VaultColor color) {
        this.relativePosition = Objects.requireNonNull(relativePosition, "relativePosition").clone();
        this.color = Objects.requireNonNull(color, "color");
    }

    @NotNull public Vector getRelativePosition() { return relativePosition.clone(); }
    @NotNull public VaultColor getColor() { return color; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BranchSignifier)) return false;
        BranchSignifier that = (BranchSignifier) o;
        return relativePosition.equals(that.relativePosition) && color == that.color;
    }

    @Override
    public int hashCode() {
        return Objects.hash(relativePosition, color);
    }

    @Override
    public String toString() {
        return "BranchSignifier{pos=" + relativePosition + ", color=" + color + '}';
    }
}

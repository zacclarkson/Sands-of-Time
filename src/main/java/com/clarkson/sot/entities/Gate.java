package com.clarkson.sot.entities;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A gate: a wall inside a single segment, opened permanently by that segment's lever.
 *
 * <p>Gates take no key -- {@link #isCorrectKey} is always false -- and are built from iron bars so a
 * player can size up what is behind one before deciding whether to open it. That decision is the
 * mechanic ("there is a set of coins here but it's guarded by ravagers -- do you open the gate?"), and
 * an opaque wall would hide the very thing being weighed. They are one-way: {@link #close} refuses, so
 * a team cannot wall a ravager back in after seeing it.
 *
 * <p>The {@code lockLocation} is a cell of the gate itself, never the lever. {@link Door#buildClosed()}
 * stamps {@link #getLockMaterial()} at the lock location, so pointing a gate's lock at its lever would
 * bury the lever under iron bars the moment the gate was built.
 */
public class Gate extends Door {

    /**
     * @param bounds The blocks making up this gate. Its minimum corner doubles as the lock location,
     *               which keeps {@code buildClosed}'s keyhole stamp inside the gate.
     */
    public Gate(@NotNull Plugin plugin, @NotNull UUID teamId, @NotNull Area bounds) {
        super(plugin, teamId, bounds, minPointOf(bounds));
    }

    private static Location minPointOf(@NotNull Area bounds) {
        return bounds.getMinPoint().clone();
    }

    /** Gates are opened by a lever, never by a key. */
    @Override
    public boolean isCorrectKey(@Nullable ItemStack keyStack) {
        return false;
    }

    @Override
    @NotNull
    protected Material getClosedMaterial() {
        return Material.IRON_BARS;
    }

    /** One-way by design: a pulled lever opens its gates for the rest of the round. */
    @Override
    public boolean close(@Nullable Player player) {
        return false;
    }
}

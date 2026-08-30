package com.clarkson.sot.events;

import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The whitelist of blocks a player is allowed to break while a round is live.
 *
 * <p>Sands of Time is played inside a generated dungeon, so a player mining it is a player
 * dismantling the game: vault marker blocks can be tunnelled around to skip their key, death cages
 * can be mined out of instead of costing a teammate a sand sacrifice, and the hub's sand timer
 * column can be farmed for time. Everything the dungeon is built out of is therefore protected, and
 * only the blocks that are themselves a game mechanic may be broken:
 *
 * <ul>
 *   <li>{@link Material#SAND} — the timer currency. Breaking dungeon sand is the only way to add
 *       time to a team's clock ({@code SandManager.collectSandItem}, +10s each).</li>
 *   <li>{@link Material#SPAWNER} — broken to stop the hostile mobs it produces.</li>
 * </ul>
 *
 * <p><b>Money blocks are the extension point.</b> The planned "break a block for the coins inside"
 * feature is not implemented. When it lands, a money block that has its own material simply joins
 * {@link #BREAKABLE}. If instead it reuses a material the dungeon already places — likely, since
 * vault markers already reuse {@code GOLD_BLOCK} — then material alone cannot identify it and it
 * will need a per-team location registry of the kind {@code VaultManager} and {@code DoorManager}
 * keep; {@link BlockProtectionListener} carries a marked insertion point for that check.
 *
 * <p>This is game-design policy rather than a server-operator knob, so it lives here as a constant
 * rather than in {@code config.yml}.
 *
 * @see BlockProtectionListener
 */
public final class BreakableBlocks {

    /**
     * Materials a player may break during a live round. Unmodifiable.
     *
     * <p>Note that being on this list is necessary but not sufficient: the team's visual sand timer
     * column is made of {@code SAND} and is protected regardless, because the blocks belong to the
     * timer rather than to the dungeon's sand spawns.
     */
    public static final Set<Material> BREAKABLE =
            Collections.unmodifiableSet(EnumSet.of(Material.SAND, Material.SPAWNER));

    /**
     * Whether players may break this material while a round is on the clock.
     *
     * @param material the block's material, or null
     * @return true only for the whitelisted materials; false for null
     */
    public static boolean isBreakableDuringRound(@Nullable Material material) {
        return material != null && BREAKABLE.contains(material);
    }

    private BreakableBlocks() {}
}
